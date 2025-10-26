package com.minitb.integration;

import com.minitb.application.service.DeviceService;
import com.minitb.datasource.prometheus.PrometheusDataPuller;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import com.minitb.storage.TelemetryStorage;
import com.minitb.domain.telemetry.TsKvEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrometheusDataPuller 真实集成测试
 * 
 * 测试场景：
 * 1. 本机作为监控设备
 * 2. 从本地 Prometheus (http://localhost:9090) 拉取数据
 * 3. 验证 CPU、内存、磁盘三个指标
 * 
 * 前置条件：
 * - 本地运行 Prometheus (端口 9090)
 * - 本地运行 Node Exporter (端口 9100)
 * - 设置环境变量 PROMETHEUS_ENABLED=true 启用此测试
 * 
 * 启动 Prometheus (Docker):
 *   docker run -d -p 9090:9090 prom/prometheus
 * 
 * 启动 Node Exporter (Docker):
 *   docker run -d -p 9100:9100 prom/node-exporter
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROMETHEUS_ENABLED", matches = "true")
class PrometheusDataPullerIntegrationTest {
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private PrometheusDataPuller prometheusDataPuller;
    
    @Autowired
    private TelemetryStorage telemetryStorage;
    
    @Autowired
    private com.minitb.transport.service.TransportService transportService;
    
    @Autowired
    private com.minitb.actor.MiniTbActorSystem actorSystem;
    
    private static DeviceProfileId localhostProfileId;  // ← static，所有测试共享
    private static DeviceId localhostDeviceId;
    private static boolean initialized = false;
    
    private static final String PROMETHEUS_ENDPOINT = "http://localhost:9090";
    private static final String NODE_EXPORTER_INSTANCE = "localhost:9100";
    
    @BeforeEach
    void setUp() throws Exception {
        // 只初始化一次
        if (initialized) {
            return;
        }
        // 验证 Prometheus 是否可访问
        if (!isPrometheusAvailable()) {
            fail("❌ Prometheus 服务不可用，请确保 http://localhost:9090 可访问");
        }
        
        // 验证 Node Exporter 是否可访问
        if (!isNodeExporterAvailable()) {
            fail("❌ Node Exporter 不可用，请确保 http://localhost:9100/metrics 可访问");
        }
        
        // 创建本机监控的 DeviceProfile
        DeviceProfile localhostProfile = createLocalhostMonitorProfile();
        DeviceProfile savedProfile = deviceService.saveProfile(localhostProfile);
        localhostProfileId = savedProfile.getId();
        
        // 创建本机设备
        Device localhostDevice = Device.builder()
                .id(DeviceId.random())
                .name("本机服务器")
                .type("SERVER_LOCALHOST")
                .deviceProfileId(localhostProfileId)
                .accessToken("localhost-monitor-token")
                .prometheusLabel("instance=" + NODE_EXPORTER_INSTANCE)
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device savedDevice = deviceService.save(localhostDevice);
        localhostDeviceId = savedDevice.getId();
        
        // 手动为测试设备创建 DeviceActor（测试环境需要）
        // 因为 TransportService.setActorSystem() 只在应用启动时调用一次
        com.minitb.actor.device.DeviceActor deviceActor = 
            new com.minitb.actor.device.DeviceActor(savedDevice.getId(), savedDevice);
        actorSystem.createActor(deviceActor.getActorId(), deviceActor);
        System.out.println("✅ 为测试设备创建 DeviceActor: " + deviceActor.getActorId());
        
        initialized = true;  // ← 标记已初始化
        
        System.out.println("\n========================================");
        System.out.println("✅ 测试环境初始化完成");
        System.out.println("========================================");
        System.out.println("📊 Prometheus: " + PROMETHEUS_ENDPOINT);
        System.out.println("📡 Node Exporter: " + NODE_EXPORTER_INSTANCE);
        System.out.println("🖥️  设备: " + savedDevice.getName());
        System.out.println("🏷️  标签映射: " + savedDevice.getPrometheusLabel());
        System.out.println("========================================\n");
    }
    
    @Test
    void testPullLocalhostMetrics() throws Exception {
        System.out.println("\n🔄 开始拉取本机 Prometheus 数据...\n");
        
        // When - 手动触发数据拉取
        prometheusDataPuller.pullAllPrometheusDevices();
        
        // 等待数据处理（Actor 异步处理）
        Thread.sleep(2000);
        
        // Then - 验证数据已保存到存储
        System.out.println("📊 验证拉取的数据:\n");
        
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - 5 * 60 * 1000;
        
        // 验证 CPU 使用率
        List<TsKvEntry> cpuData = telemetryStorage.query(localhostDeviceId, "cpu_usage_percent", fiveMinutesAgo, now);
        assertNotNull(cpuData, "应该有 CPU 数据");
        assertFalse(cpuData.isEmpty(), "CPU 数据不应为空");
        
        TsKvEntry cpuEntry = cpuData.get(cpuData.size() - 1);  // 获取最新数据
        System.out.println("  ✓ CPU 使用率: " + cpuEntry.getValue() + "%");
        assertTrue(cpuEntry.getDoubleValue().isPresent(), "CPU 应该是 DOUBLE 类型");
        double cpuValue = cpuEntry.getDoubleValue().get();
        assertTrue(cpuValue >= 0 && cpuValue <= 100, "CPU 使用率应该在 0-100 之间，实际: " + cpuValue);
        
        // 验证内存使用率
        List<TsKvEntry> memoryData = telemetryStorage.query(localhostDeviceId, "memory_usage_percent", fiveMinutesAgo, now);
        assertNotNull(memoryData, "应该有内存数据");
        assertFalse(memoryData.isEmpty(), "内存数据不应为空");
        
        TsKvEntry memoryEntry = memoryData.get(memoryData.size() - 1);
        System.out.println("  ✓ 内存使用率: " + memoryEntry.getValue() + "%");
        assertTrue(memoryEntry.getDoubleValue().isPresent(), "内存应该是 DOUBLE 类型");
        double memoryValue = memoryEntry.getDoubleValue().get();
        assertTrue(memoryValue >= 0 && memoryValue <= 100, "内存使用率应该在 0-100 之间，实际: " + memoryValue);
        
        // 验证磁盘使用率
        List<TsKvEntry> diskData = telemetryStorage.query(localhostDeviceId, "disk_usage_percent", fiveMinutesAgo, now);
        assertNotNull(diskData, "应该有磁盘数据");
        assertFalse(diskData.isEmpty(), "磁盘数据不应为空");
        
        TsKvEntry diskEntry = diskData.get(diskData.size() - 1);
        System.out.println("  ✓ 磁盘使用率: " + diskEntry.getValue() + "%");
        assertTrue(diskEntry.getDoubleValue().isPresent(), "磁盘应该是 DOUBLE 类型");
        double diskValue = diskEntry.getDoubleValue().get();
        assertTrue(diskValue >= 0 && diskValue <= 100, "磁盘使用率应该在 0-100 之间，实际: " + diskValue);
        
        System.out.println("\n✅ 所有指标拉取成功！\n");
    }
    
    @Test
    void testPrometheusQueryResult() throws Exception {
        System.out.println("\n🔍 测试单个 Prometheus 查询...\n");
        
        // Given - 获取本机设备的 Profile
        DeviceProfile profile = deviceService.findProfileById(localhostProfileId)
                .orElseThrow();
        
        // When - 测试 CPU 查询
        TelemetryDefinition cpuDef = profile.getTelemetryDefinitions().stream()
                .filter(def -> "cpu_usage_percent".equals(def.getKey()))
                .findFirst()
                .orElseThrow();
        
        PrometheusConfig cpuConfig = cpuDef.getPrometheusConfig();
        String promQL = cpuConfig.getPromQL();
        
        System.out.println("📝 PromQL: " + promQL);
        
        // 直接查询 Prometheus（测试查询逻辑）
        String url = PROMETHEUS_ENDPOINT + "/api/v1/query?query=" + 
                     java.net.URLEncoder.encode(promQL, "UTF-8");
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Then - 验证查询成功
        assertEquals(200, response.statusCode(), "Prometheus 查询应该返回 200");
        
        String responseBody = response.body();
        System.out.println("\n📊 Prometheus 响应 (前 500 字符):");
        System.out.println(responseBody.substring(0, Math.min(500, responseBody.length())));
        System.out.println("...\n");
        
        assertTrue(responseBody.contains("\"status\":\"success\""), "查询应该成功");
        assertTrue(responseBody.contains("\"result\""), "应该有结果");
        assertTrue(responseBody.contains(NODE_EXPORTER_INSTANCE) || 
                   responseBody.contains("localhost"), 
                   "结果应该包含 localhost 实例");
    }
    
    @Test
    void testMultiplePullCycles() throws Exception {
        System.out.println("\n🔄 测试多次拉取周期...\n");
        
        long startTime = System.currentTimeMillis();
        
        // When - 执行 3 次拉取
        for (int i = 1; i <= 3; i++) {
            System.out.println("第 " + i + " 次拉取...");
            prometheusDataPuller.pullAllPrometheusDevices();
            Thread.sleep(1000);  // 等待处理
        }
        
        long endTime = System.currentTimeMillis();
        
        // Then - 验证数据被更新
        List<TsKvEntry> cpuData = telemetryStorage.query(localhostDeviceId, "cpu_usage_percent", startTime, endTime);
        assertFalse(cpuData.isEmpty(), "应该有最新的 CPU 数据");
        
        System.out.println("✅ 共拉取 " + cpuData.size() + " 次数据");
        System.out.println("✅ 最新 CPU 使用率: " + cpuData.get(cpuData.size() - 1).getValue() + "%");
        System.out.println();
    }
    
    @Test
    void testLabelMappingCorrectness() throws Exception {
        System.out.println("\n🏷️  测试标签映射正确性...\n");
        
        // Given - 查询设备信息
        Device device = deviceService.findById(localhostDeviceId).orElseThrow();
        DeviceProfile profile = deviceService.findProfileById(localhostProfileId).orElseThrow();
        
        // Then - 验证配置
        System.out.println("设备配置:");
        System.out.println("  - prometheusLabel: " + device.getPrometheusLabel());
        System.out.println("  - accessToken: " + device.getAccessToken());
        System.out.println("\nProfile 配置:");
        System.out.println("  - prometheusEndpoint: " + profile.getPrometheusEndpoint());
        System.out.println("  - prometheusDeviceLabelKey: " + profile.getPrometheusDeviceLabelKey());
        
        // 验证配置正确
        assertEquals("instance=" + NODE_EXPORTER_INSTANCE, device.getPrometheusLabel());
        assertEquals(PROMETHEUS_ENDPOINT, profile.getPrometheusEndpoint());
        assertEquals("instance", profile.getPrometheusDeviceLabelKey());
        
        // 解析标签
        String[] labelParts = device.getPrometheusLabel().split("=", 2);
        assertEquals(2, labelParts.length, "标签格式应该是 key=value");
        assertEquals("instance", labelParts[0], "标签键应该匹配 Profile 配置");
        assertEquals(NODE_EXPORTER_INSTANCE, labelParts[1], "标签值应该匹配 Node Exporter 实例");
        
        System.out.println("\n✅ 标签映射配置正确！\n");
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * 创建本机监控的 DeviceProfile
     */
    private DeviceProfile createLocalhostMonitorProfile() {
        List<TelemetryDefinition> telemetryDefs = new ArrayList<>();
        
        // CPU 使用率 (排除 idle 模式的使用率)
        telemetryDefs.add(TelemetryDefinition.builder()
                .key("cpu_usage_percent")
                .displayName("CPU使用率")
                .dataType(DataType.DOUBLE)
                .description("本机 CPU 使用率百分比")
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)")
                        .needsRateCalculation(false)
                        .build())
                .build());
        
        // 内存使用率 (macOS 兼容版本)
        // macOS 没有 MemAvailable_bytes，使用 active / (active + free + inactive)
        telemetryDefs.add(TelemetryDefinition.builder()
                .key("memory_usage_percent")
                .displayName("内存使用率")
                .dataType(DataType.DOUBLE)
                .description("本机内存使用率百分比")
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(node_memory_active_bytes / (node_memory_active_bytes + node_memory_free_bytes + node_memory_inactive_bytes)) * 100")
                        .needsRateCalculation(false)
                        .build())
                .build());
        
        // 磁盘使用率 (根分区)
        telemetryDefs.add(TelemetryDefinition.builder()
                .key("disk_usage_percent")
                .displayName("磁盘使用率")
                .dataType(DataType.DOUBLE)
                .description("本机根分区磁盘使用率百分比")
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})) * 100")
                        .needsRateCalculation(false)
                        .build())
                .build());
        
        return DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("Localhost Monitor Profile")
                .description("本机系统监控配置")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .prometheusEndpoint(PROMETHEUS_ENDPOINT)
                .prometheusDeviceLabelKey("instance")
                .strictMode(true)
                .telemetryDefinitions(telemetryDefs)
                .createdTime(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 检查 Prometheus 是否可访问
     */
    private boolean isPrometheusAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROMETHEUS_ENDPOINT + "/api/v1/status/config"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean available = response.statusCode() == 200;
            
            if (available) {
                System.out.println("✅ Prometheus 服务可用: " + PROMETHEUS_ENDPOINT);
            } else {
                System.out.println("❌ Prometheus 返回状态码: " + response.statusCode());
            }
            
            return available;
            
        } catch (Exception e) {
            System.out.println("❌ Prometheus 连接失败: " + e.getMessage());
            System.out.println("\n💡 启动方法:");
            System.out.println("   docker run -d -p 9090:9090 prom/prometheus");
            return false;
        }
    }
    
    /**
     * 检查 Node Exporter 是否可访问
     */
    private boolean isNodeExporterAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + NODE_EXPORTER_INSTANCE + "/metrics"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean available = response.statusCode() == 200;
            
            if (available) {
                System.out.println("✅ Node Exporter 可用: " + NODE_EXPORTER_INSTANCE);
            } else {
                System.out.println("❌ Node Exporter 返回状态码: " + response.statusCode());
            }
            
            return available;
            
        } catch (Exception e) {
            System.out.println("❌ Node Exporter 连接失败: " + e.getMessage());
            System.out.println("\n💡 启动方法:");
            System.out.println("   docker run -d -p 9100:9100 prom/node-exporter");
            return false;
        }
    }
}

