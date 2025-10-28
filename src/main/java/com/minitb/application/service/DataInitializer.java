package com.minitb.application.service;

import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.PrometheusDeviceConfiguration;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 数据初始化器
 * 
 * 职责：
 * - 应用启动时初始化默认数据
 * - 创建测试设备和配置文件
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final DeviceService deviceService;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("开始初始化默认数据...");
        log.info("========================================");
        
        initDefaultDevices();
        
        log.info("========================================");
        log.info("默认数据初始化完成");
        log.info("========================================");
    }
    
    /**
     * 初始化默认设备
     */
    private void initDefaultDevices() {
        // ========== 1. 确保 GPU 监控 DeviceProfile 存在 ==========
        DeviceProfileId gpuProfileId = ensureGpuMonitorProfileExists();
        
        // ========== 2. 确保 GPU 0 Device 存在 ==========
        ensureGpuDeviceExists(gpuProfileId, 0, "gpu-0-token", "gpu=0");
        
        // ========== 3. 确保 GPU 1 Device 存在 ==========
        ensureGpuDeviceExists(gpuProfileId, 1, "gpu-1-token", "gpu=1");
    }
    
    /**
     * 确保 GPU 监控 DeviceProfile 存在
     * @return DeviceProfile ID
     */
    private DeviceProfileId ensureGpuMonitorProfileExists() {
        // 检查是否已存在
        Optional<DeviceProfile> existing = deviceService.findProfileByName("NVIDIA GPU Monitor (DCGM)");
        if (existing.isPresent()) {
            log.info("GPU 监控配置文件已存在，使用现有配置 (ID: {})", existing.get().getId());
            return existing.get().getId();
        }
        
        // 创建新的 DeviceProfile
        log.info("创建 GPU 监控配置文件...");
        DeviceProfile gpuProfile = DeviceProfile.builder()
                .name("NVIDIA GPU Monitor (DCGM)")
                .description("NVIDIA TITAN V GPU 监控配置 - 从 Prometheus 拉取 DCGM 指标")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                // 注意: prometheusEndpoint 已移到 Device.configuration 中
                .prometheusDeviceLabelKey("gpu")
                .strictMode(true)
                .telemetryDefinitions(createGpuTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();
        
        DeviceProfile saved = deviceService.saveProfile(gpuProfile);
        log.info("✓ DeviceProfile 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - 遥测指标数量: {}", saved.getTelemetryDefinitions().size());
        
        return saved.getId();
    }
    
    /**
     * 确保指定的 GPU Device 存在
     */
    private void ensureGpuDeviceExists(DeviceProfileId profileId, int gpuIndex, 
                                       String accessToken, String prometheusLabel) {
        // 检查是否已存在
        if (deviceService.existsByAccessToken(accessToken)) {
            log.info("GPU {} 设备已存在，跳过创建 (token: {})", gpuIndex, accessToken);
            return;
        }
        
        // 创建新的 Device
        Device gpu = Device.builder()
                .name("NVIDIA TITAN V - GPU " + gpuIndex)
                .type("NVIDIA_GPU")
                .deviceProfileId(profileId)
                .accessToken(accessToken)
                // 使用 PrometheusDeviceConfiguration
                .configuration(PrometheusDeviceConfiguration.builder()
                        .endpoint("http://192.168.30.134:9090")
                        .label(prometheusLabel)  // "gpu=0" 或 "gpu=1"
                        .build())
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device saved = deviceService.save(gpu);
        log.info("✓ Device 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - AccessToken: {}", saved.getAccessToken());
        log.info("  - Prometheus Label: {}", prometheusLabel);
    }
    
    /**
     * 创建 GPU 遥测指标定义
     */
    private List<TelemetryDefinition> createGpuTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        // 1. GPU 利用率
        defs.add(TelemetryDefinition.builder()
                .key("gpu_utilization")
                .displayName("GPU利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_GPU_UTIL")
                        .build())
                .build());
        
        // 2. 内存拷贝带宽利用率
        defs.add(TelemetryDefinition.builder()
                .key("memory_copy_utilization")
                .displayName("内存拷贝带宽利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEM_COPY_UTIL")
                        .build())
                .build());
        
        // 3. GPU 温度
        defs.add(TelemetryDefinition.builder()
                .key("gpu_temperature")
                .displayName("GPU温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_GPU_TEMP")
                        .build())
                .build());
        
        // 4. 显存温度
        defs.add(TelemetryDefinition.builder()
                .key("memory_temperature")
                .displayName("显存温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEMORY_TEMP")
                        .build())
                .build());
        
        // 5. 功耗
        defs.add(TelemetryDefinition.builder()
                .key("power_usage")
                .displayName("功耗")
                .dataType(DataType.DOUBLE)
                .unit("W")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_POWER_USAGE")
                        .build())
                .build());
        
        // 6. 已用显存
        defs.add(TelemetryDefinition.builder()
                .key("memory_used")
                .displayName("已用显存")
                .dataType(DataType.DOUBLE)
                .unit("MiB")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_FB_USED")
                        .build())
                .build());
        
        // 7. 空闲显存
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
}


