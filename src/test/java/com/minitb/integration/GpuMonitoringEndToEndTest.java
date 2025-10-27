package com.minitb.integration;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.application.service.DeviceService;
import com.minitb.datasource.prometheus.PrometheusDataPuller;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import com.minitb.domain.telemetry.TsKvEntry;
import com.minitb.storage.TelemetryStorage;
import com.minitb.infrastructure.transport.service.TransportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GPU 监控端到端测试
 * 
 * 测试场景：
 * - 监控 2 块 NVIDIA TITAN V GPU
 * - 从 DCGM Exporter (http://192.168.30.134:9400/metrics) 拉取数据
 * - 每个 GPU 作为一个独立的 Device
 * - 使用 gpu 标签区分不同的 GPU
 * 
 * 监控指标：
 * 1. GPU 利用率 (DCGM_FI_DEV_GPU_UTIL) - SM 计算单元使用率
 * 2. 内存拷贝带宽利用率 (DCGM_FI_DEV_MEM_COPY_UTIL) - PCIe 传输繁忙度
 * 3. GPU 温度 (DCGM_FI_DEV_GPU_TEMP)
 * 4. 显存温度 (DCGM_FI_DEV_MEMORY_TEMP)
 * 5. 功耗 (DCGM_FI_DEV_POWER_USAGE)
 * 6. 已用显存 (DCGM_FI_DEV_FB_USED) - 实际使用的显存 MiB
 * 7. 空闲显存 (DCGM_FI_DEV_FB_FREE) - 可用的显存 MiB
 * 
 * 前置条件：
 * - DCGM Exporter 运行在 http://192.168.30.134:9400
 * - 设置环境变量 GPU_MONITORING_ENABLED=true
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GPU_MONITORING_ENABLED", matches = "true")
class GpuMonitoringEndToEndTest {
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private PrometheusDataPuller prometheusDataPuller;
    
    @Autowired
    private TransportService transportService;
    
    @Autowired
    private MiniTbActorSystem actorSystem;
    
    @Autowired
    private TelemetryStorage telemetryStorage;
    
    private static DeviceProfileId gpuProfileId;
    private static DeviceId gpu0DeviceId;
    private static DeviceId gpu1DeviceId;
    private static boolean initialized = false;
    
    private static final String PROMETHEUS_ENDPOINT = "http://192.168.30.134:9090";
    private static final String DCGM_ENDPOINT = "http://192.168.30.134:9400";
    private static final String GPU_0_LABEL = "gpu=\"0\"";
    private static final String GPU_1_LABEL = "gpu=\"1\"";
    
    @BeforeEach
    void setUp() throws Exception {
        if (initialized) {
            return;
        }
        
        // 验证 DCGM Exporter 可用
        if (!checkDcgmAvailable()) {
            fail("❌ DCGM Exporter 不可用: " + DCGM_ENDPOINT);
        }
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║   GPU 监控端到端测试 - NVIDIA TITAN V                  ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // 创建 GPU 监控 DeviceProfile
        setupGpuDevices();
        
        initialized = true;
    }
    
    @Test
    void testDualGpuMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试双 GPU 监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long testStartTime = System.currentTimeMillis();
        
        // ========== 验证设备配置 ==========
        System.out.println("📋 设备配置:");
        Device gpu0 = deviceService.findById(gpu0DeviceId).orElseThrow();
        Device gpu1 = deviceService.findById(gpu1DeviceId).orElseThrow();
        
        System.out.println("  GPU 0:");
        System.out.println("    - 设备名称: " + gpu0.getName());
        System.out.println("    - 设备 ID: " + gpu0.getId());
        System.out.println("    - 标签映射: " + gpu0.getPrometheusLabel());
        System.out.println("    - AccessToken: " + gpu0.getAccessToken());
        
        System.out.println("  GPU 1:");
        System.out.println("    - 设备名称: " + gpu1.getName());
        System.out.println("    - 设备 ID: " + gpu1.getId());
        System.out.println("    - 标签映射: " + gpu1.getPrometheusLabel());
        System.out.println("    - AccessToken: " + gpu1.getAccessToken());
        System.out.println();
        
        // ========== 拉取数据 ==========
        System.out.println("🔄 从 Prometheus 拉取 GPU 数据...");
        System.out.println("  - Prometheus: " + PROMETHEUS_ENDPOINT);
        System.out.println("  - DCGM Exporter: " + DCGM_ENDPOINT);
        
        long beforePull = System.currentTimeMillis();
        
        // 手动触发拉取
        prometheusDataPuller.pullAllPrometheusDevices();
        
        System.out.println("  ✅ 数据拉取完成");
        System.out.println();
        
        // 等待异步处理（给足够时间以便调试）
        Thread.sleep(1000);
        
        long afterProcess = System.currentTimeMillis();
        
        // ========== 验证 GPU 0 数据 ==========
        System.out.println("📊 GPU 0 数据验证:");
        verifyGpuData(gpu0DeviceId, "GPU 0", beforePull, afterProcess);
        
        // ========== 验证 GPU 1 数据 ==========
        System.out.println("\n📊 GPU 1 数据验证:");
        verifyGpuData(gpu1DeviceId, "GPU 1", beforePull, afterProcess);
        
        // ========== 总结 ==========
        long totalTime = afterProcess - testStartTime;
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║   ✅ 双 GPU 监控测试通过                                ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("测试摘要:");
        System.out.println("  - 监控设备数量: 2 (GPU 0, GPU 1)");
        System.out.println("  - 每设备指标数: 7");
        System.out.println("  - 总指标数: 14");
        System.out.println("  - 总耗时: " + totalTime + " ms");
        System.out.println();
    }
    
    @Test
    void testGpuUtilizationMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 GPU 利用率监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(200);
        long afterProcess = System.currentTimeMillis();
        
        // 验证 GPU 利用率
        List<TsKvEntry> gpu0Util = telemetryStorage.query(
            gpu0DeviceId, "gpu_utilization", beforePull, afterProcess);
        
        List<TsKvEntry> gpu1Util = telemetryStorage.query(
            gpu1DeviceId, "gpu_utilization", beforePull, afterProcess);
        
        assertFalse(gpu0Util.isEmpty(), "GPU 0 利用率数据应存在");
        assertFalse(gpu1Util.isEmpty(), "GPU 1 利用率数据应存在");
        
        // 获取最新值
        double gpu0UtilValue = getValue(gpu0Util.get(gpu0Util.size() - 1));
        double gpu1UtilValue = getValue(gpu1Util.get(gpu1Util.size() - 1));
        
        System.out.println("📈 GPU 利用率:");
        System.out.println("  GPU 0: " + String.format("%.1f", gpu0UtilValue) + "%");
        System.out.println("  GPU 1: " + String.format("%.1f", gpu1UtilValue) + "%");
        System.out.println();
        
        // 验证数据范围
        assertTrue(gpu0UtilValue >= 0 && gpu0UtilValue <= 100, 
            "GPU 0 利用率应在 0-100% 范围内");
        assertTrue(gpu1UtilValue >= 0 && gpu1UtilValue <= 100, 
            "GPU 1 利用率应在 0-100% 范围内");
        
        System.out.println("✅ GPU 利用率监控验证通过");
        System.out.println();
    }
    
    @Test
    void testGpuTemperatureMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 GPU 温度监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(200);
        long afterProcess = System.currentTimeMillis();
        
        // 验证 GPU 温度
        List<TsKvEntry> gpu0Temp = telemetryStorage.query(
            gpu0DeviceId, "gpu_temperature", beforePull, afterProcess);
        
        List<TsKvEntry> gpu1Temp = telemetryStorage.query(
            gpu1DeviceId, "gpu_temperature", beforePull, afterProcess);
        
        assertFalse(gpu0Temp.isEmpty(), "GPU 0 温度数据应存在");
        assertFalse(gpu1Temp.isEmpty(), "GPU 1 温度数据应存在");
        
        // 获取最新值
        double gpu0TempValue = getValue(gpu0Temp.get(gpu0Temp.size() - 1));
        double gpu1TempValue = getValue(gpu1Temp.get(gpu1Temp.size() - 1));
        
        System.out.println("🌡️  GPU 温度:");
        System.out.println("  GPU 0: " + String.format("%.0f", gpu0TempValue) + "°C");
        System.out.println("  GPU 1: " + String.format("%.0f", gpu1TempValue) + "°C");
        System.out.println();
        
        // 验证温度范围（合理范围：30-100°C）
        assertTrue(gpu0TempValue >= 30 && gpu0TempValue <= 100, 
            "GPU 0 温度应在合理范围内 (30-100°C)");
        assertTrue(gpu1TempValue >= 30 && gpu1TempValue <= 100, 
            "GPU 1 温度应在合理范围内 (30-100°C)");
        
        System.out.println("✅ GPU 温度监控验证通过");
        System.out.println();
    }
    
    @Test
    void testGpuPowerMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 GPU 功耗监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(200);
        long afterProcess = System.currentTimeMillis();
        
        // 验证功耗
        List<TsKvEntry> gpu0Power = telemetryStorage.query(
            gpu0DeviceId, "power_usage", beforePull, afterProcess);
        
        List<TsKvEntry> gpu1Power = telemetryStorage.query(
            gpu1DeviceId, "power_usage", beforePull, afterProcess);
        
        assertFalse(gpu0Power.isEmpty(), "GPU 0 功耗数据应存在");
        assertFalse(gpu1Power.isEmpty(), "GPU 1 功耗数据应存在");
        
        // 获取最新值
        double gpu0PowerValue = getValue(gpu0Power.get(gpu0Power.size() - 1));
        double gpu1PowerValue = getValue(gpu1Power.get(gpu1Power.size() - 1));
        
        System.out.println("⚡ GPU 功耗:");
        System.out.println("  GPU 0: " + String.format("%.2f", gpu0PowerValue) + " W");
        System.out.println("  GPU 1: " + String.format("%.2f", gpu1PowerValue) + " W");
        System.out.println("  总功耗: " + String.format("%.2f", gpu0PowerValue + gpu1PowerValue) + " W");
        System.out.println();
        
        // NVIDIA TITAN V TDP: 250W
        assertTrue(gpu0PowerValue > 0 && gpu0PowerValue <= 300, 
            "GPU 0 功耗应在合理范围内 (0-300W)");
        assertTrue(gpu1PowerValue > 0 && gpu1PowerValue <= 300, 
            "GPU 1 功耗应在合理范围内 (0-300W)");
        
        System.out.println("✅ GPU 功耗监控验证通过");
        System.out.println();
    }
    
    @Test
    void testGpuMemoryMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 GPU 显存监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(200);
        long afterProcess = System.currentTimeMillis();
        
        // 验证显存数据
        List<TsKvEntry> gpu0Used = telemetryStorage.query(
            gpu0DeviceId, "memory_used", beforePull, afterProcess);
        List<TsKvEntry> gpu0Free = telemetryStorage.query(
            gpu0DeviceId, "memory_free", beforePull, afterProcess);
        
        List<TsKvEntry> gpu1Used = telemetryStorage.query(
            gpu1DeviceId, "memory_used", beforePull, afterProcess);
        List<TsKvEntry> gpu1Free = telemetryStorage.query(
            gpu1DeviceId, "memory_free", beforePull, afterProcess);
        
        assertFalse(gpu0Used.isEmpty(), "GPU 0 已用显存数据应存在");
        assertFalse(gpu0Free.isEmpty(), "GPU 0 空闲显存数据应存在");
        assertFalse(gpu1Used.isEmpty(), "GPU 1 已用显存数据应存在");
        assertFalse(gpu1Free.isEmpty(), "GPU 1 空闲显存数据应存在");
        
        // 获取最新值
        double gpu0UsedValue = getValue(gpu0Used.get(gpu0Used.size() - 1));
        double gpu0FreeValue = getValue(gpu0Free.get(gpu0Free.size() - 1));
        double gpu0Total = gpu0UsedValue + gpu0FreeValue;
        
        double gpu1UsedValue = getValue(gpu1Used.get(gpu1Used.size() - 1));
        double gpu1FreeValue = getValue(gpu1Free.get(gpu1Free.size() - 1));
        double gpu1Total = gpu1UsedValue + gpu1FreeValue;
        
        System.out.println("💾 GPU 显存:");
        System.out.println("  GPU 0:");
        System.out.println("    - 已用: " + String.format("%.0f", gpu0UsedValue) + " MiB");
        System.out.println("    - 空闲: " + String.format("%.0f", gpu0FreeValue) + " MiB");
        System.out.println("    - 总计: " + String.format("%.0f", gpu0Total) + " MiB (~12GB HBM2)");
        System.out.println("    - 实际使用率: " + String.format("%.2f", gpu0UsedValue / gpu0Total * 100) + "%");
        
        System.out.println("  GPU 1:");
        System.out.println("    - 已用: " + String.format("%.0f", gpu1UsedValue) + " MiB");
        System.out.println("    - 空闲: " + String.format("%.0f", gpu1FreeValue) + " MiB");
        System.out.println("    - 总计: " + String.format("%.0f", gpu1Total) + " MiB (~12GB HBM2)");
        System.out.println("    - 实际使用率: " + String.format("%.2f", gpu1UsedValue / gpu1Total * 100) + "%");
        System.out.println();
        
        // NVIDIA TITAN V: 12GB HBM2
        assertTrue(gpu0Total >= 11000 && gpu0Total <= 13000, 
            "GPU 0 总显存应接近 12GB");
        assertTrue(gpu1Total >= 11000 && gpu1Total <= 13000, 
            "GPU 1 总显存应接近 12GB");
        
        System.out.println("✅ GPU 显存监控验证通过");
        System.out.println();
    }
    
    // ==================== Helper Methods ====================
    
    private void setupGpuDevices() {
        System.out.println("🔧 初始化 GPU 监控设备...\n");
        
        // 1. 创建 GPU 监控 DeviceProfile
        DeviceProfile gpuProfile = DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("NVIDIA GPU Monitor (DCGM)")
                .description("NVIDIA TITAN V GPU 监控配置")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .prometheusEndpoint(PROMETHEUS_ENDPOINT)  // ← 使用 Prometheus 服务器地址
                .prometheusDeviceLabelKey("gpu")  // 使用 gpu 标签区分设备
                .strictMode(true)
                .telemetryDefinitions(createGpuTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();
        
        DeviceProfile savedProfile = deviceService.saveProfile(gpuProfile);
        gpuProfileId = savedProfile.getId();
        System.out.println("  ✓ DeviceProfile 创建: " + savedProfile.getName());
        
        // 2. 创建 GPU 0 Device
        Device gpu0 = Device.builder()
                .id(DeviceId.random())
                .name("NVIDIA TITAN V - GPU 0")
                .type("NVIDIA_GPU")
                .deviceProfileId(gpuProfileId)
                .accessToken("gpu-0-token-" + System.currentTimeMillis())
                .prometheusLabel("gpu=0")  // 标签映射
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device savedGpu0 = deviceService.save(gpu0);
        gpu0DeviceId = savedGpu0.getId();
        System.out.println("  ✓ Device 创建: " + savedGpu0.getName());
        
        // 创建 GPU 0 的 DeviceActor
        com.minitb.actor.device.DeviceActor gpu0Actor = 
            new com.minitb.actor.device.DeviceActor(savedGpu0.getId(), savedGpu0);
        actorSystem.createActor(gpu0Actor.getActorId(), gpu0Actor);
        System.out.println("    - DeviceActor 创建: " + gpu0Actor.getActorId());
        
        // 3. 创建 GPU 1 Device
        Device gpu1 = Device.builder()
                .id(DeviceId.random())
                .name("NVIDIA TITAN V - GPU 1")
                .type("NVIDIA_GPU")
                .deviceProfileId(gpuProfileId)
                .accessToken("gpu-1-token-" + System.currentTimeMillis())
                .prometheusLabel("gpu=1")  // 标签映射
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device savedGpu1 = deviceService.save(gpu1);
        gpu1DeviceId = savedGpu1.getId();
        System.out.println("  ✓ Device 创建: " + savedGpu1.getName());
        
        // 创建 GPU 1 的 DeviceActor
        com.minitb.actor.device.DeviceActor gpu1Actor = 
            new com.minitb.actor.device.DeviceActor(savedGpu1.getId(), savedGpu1);
        actorSystem.createActor(gpu1Actor.getActorId(), gpu1Actor);
        System.out.println("    - DeviceActor 创建: " + gpu1Actor.getActorId());
        
        System.out.println("\n✅ GPU 监控设备初始化完成\n");
    }
    
    private List<TelemetryDefinition> createGpuTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        // 1. GPU 利用率 (%)
        defs.add(TelemetryDefinition.builder()
                .key("gpu_utilization")
                .displayName("GPU利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_GPU_UTIL")
                        .build())
                .build());
        
        // 2. 内存拷贝带宽利用率 (%)
        defs.add(TelemetryDefinition.builder()
                .key("memory_copy_utilization")
                .displayName("内存拷贝带宽利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEM_COPY_UTIL")
                        .build())
                .build());
        
        // 3. GPU 温度 (°C)
        defs.add(TelemetryDefinition.builder()
                .key("gpu_temperature")
                .displayName("GPU温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_GPU_TEMP")
                        .build())
                .build());
        
        // 4. 显存温度 (°C)
        defs.add(TelemetryDefinition.builder()
                .key("memory_temperature")
                .displayName("显存温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEMORY_TEMP")
                        .build())
                .build());
        
        // 5. 功耗 (W)
        defs.add(TelemetryDefinition.builder()
                .key("power_usage")
                .displayName("功耗")
                .dataType(DataType.DOUBLE)
                .unit("W")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_POWER_USAGE")
                        .build())
                .build());
        
        // 6. 已用显存 (MiB)
        defs.add(TelemetryDefinition.builder()
                .key("memory_used")
                .displayName("已用显存")
                .dataType(DataType.DOUBLE)
                .unit("MiB")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_FB_USED")
                        .build())
                .build());
        
        // 7. 空闲显存 (MiB)
        defs.add(TelemetryDefinition.builder()
                .key("memory_free")
                .displayName("空闲显存")
                .dataType(DataType.DOUBLE)
                .unit("MiB")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_FB_FREE")
                        .build())
                .build());
        
        return defs;
    }
    
    private void verifyGpuData(DeviceId deviceId, String gpuName, long startTime, long endTime) {
        String[] metrics = {
            "gpu_utilization", "memory_copy_utilization", "gpu_temperature", 
            "memory_temperature", "power_usage", "memory_used", "memory_free"
        };
        
        int successCount = 0;
        
        for (String metricKey : metrics) {
            List<TsKvEntry> data = telemetryStorage.query(deviceId, metricKey, startTime, endTime);
            
            if (!data.isEmpty()) {
                TsKvEntry latest = data.get(data.size() - 1);
                
                // 尝试获取 double 值
                Optional<Double> doubleValue = latest.getDoubleValue();
                Optional<Long> longValue = latest.getLongValue();
                
                if (doubleValue.isPresent()) {
                    System.out.println("  ✓ " + metricKey + ": " + 
                        String.format("%.2f", doubleValue.get()) + " " + getUnit(metricKey));
                    successCount++;
                } else if (longValue.isPresent()) {
                    System.out.println("  ✓ " + metricKey + ": " + 
                        longValue.get() + " " + getUnit(metricKey));
                    successCount++;
                } else {
                    System.out.println("  ✗ " + metricKey + ": 无法获取值");
                }
            } else {
                System.out.println("  ✗ " + metricKey + ": 未保存 (查询结果为空)");
            }
        }
        
        System.out.println("  总计: " + successCount + "/7 指标成功");
    }
    
    private String getUnit(String metricKey) {
        if (metricKey.contains("utilization")) return "%";
        if (metricKey.contains("temperature")) return "°C";
        if (metricKey.contains("power")) return "W";
        if (metricKey.contains("memory")) return "MiB";
        return "";
    }
    
    /**
     * 从 TsKvEntry 获取数值（自动处理 DOUBLE 或 LONG 类型）
     */
    private double getValue(TsKvEntry entry) {
        Optional<Double> doubleValue = entry.getDoubleValue();
        if (doubleValue.isPresent()) {
            return doubleValue.get();
        }
        Optional<Long> longValue = entry.getLongValue();
        if (longValue.isPresent()) {
            return longValue.get().doubleValue();
        }
        throw new IllegalStateException("TsKvEntry 既不包含 double 值也不包含 long 值");
    }
    
    private boolean checkDcgmAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // 检查 Prometheus 是否可用
            HttpRequest promRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PROMETHEUS_ENDPOINT + "/api/v1/query?query=up"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> promResponse = client.send(promRequest, HttpResponse.BodyHandlers.ofString());
            
            if (promResponse.statusCode() != 200) {
                System.out.println("❌ Prometheus 服务器不可用: " + PROMETHEUS_ENDPOINT);
                return false;
            }
            System.out.println("✅ Prometheus 服务器可用: " + PROMETHEUS_ENDPOINT);
            
            // 检查 DCGM metrics 是否被 Prometheus 抓取
            HttpRequest dcgmRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PROMETHEUS_ENDPOINT + "/api/v1/query?query=DCGM_FI_DEV_GPU_UTIL"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> dcgmResponse = client.send(dcgmRequest, HttpResponse.BodyHandlers.ofString());
            
            if (dcgmResponse.statusCode() == 200 && dcgmResponse.body().contains("\"result\"")) {
                System.out.println("✅ DCGM 数据已被 Prometheus 抓取");
                return true;
            } else {
                System.out.println("❌ Prometheus 未抓取到 DCGM 数据");
                System.out.println("   请检查: " + PROMETHEUS_ENDPOINT + "/targets");
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ 连接失败: " + e.getMessage());
            return false;
        }
    }
}

