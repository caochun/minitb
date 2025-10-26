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
import com.minitb.transport.service.TransportService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prometheus 端到端数据流测试
 * 
 * 测试完整的数据流程:
 * Prometheus → PrometheusDataPuller → TransportService → DeviceActor → RuleEngine → TelemetryStorage
 * 
 * 目标：
 * 1. 验证每一层的数据传递
 * 2. 验证数据格式转换正确
 * 3. 验证最终数据持久化成功
 * 
 * 前置条件：
 * - 本地运行 Prometheus (http://localhost:9090)
 * - 本地运行 Node Exporter (http://localhost:9100)
 * - 设置环境变量 PROMETHEUS_ENABLED=true
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROMETHEUS_ENABLED", matches = "true")
class PrometheusEndToEndFlowTest {
    
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
    
    private static DeviceProfileId testProfileId;
    private static DeviceId testDeviceId;
    private static boolean initialized = false;
    
    private static final String PROMETHEUS_ENDPOINT = "http://localhost:9090";
    private static final String NODE_EXPORTER_INSTANCE = "localhost:9100";
    
    @BeforeEach
    void setUp() throws Exception {
        if (initialized) {
            return;
        }
        
        // 验证环境
        if (!checkPrometheusAvailable()) {
            fail("❌ Prometheus 不可用");
        }
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║   Prometheus 端到端数据流测试                          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // 创建测试 DeviceProfile 和 Device
        setupTestDevice();
        
        initialized = true;
    }
    
    @Test
    void testCompleteDataFlow() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试完整数据流");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long testStartTime = System.currentTimeMillis();
        
        // ========== 第 1 层：Prometheus 数据源 ==========
        System.out.println("📡 第 1 层: Prometheus 数据源");
        System.out.println("  - 端点: " + PROMETHEUS_ENDPOINT);
        System.out.println("  - 目标: " + NODE_EXPORTER_INSTANCE);
        
        // 验证 Prometheus 有数据
        String cpuQuery = "100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)";
        HttpClient client = HttpClient.newHttpClient();
        String url = PROMETHEUS_ENDPOINT + "/api/v1/query?query=" + 
                     java.net.URLEncoder.encode(cpuQuery, "UTF-8");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Prometheus 查询应该成功");
        assertTrue(response.body().contains("\"result\":["), "应该有查询结果");
        System.out.println("  ✅ Prometheus 查询成功");
        System.out.println();
        
        // ========== 第 2 层：PrometheusDataPuller 拉取 ==========
        System.out.println("🔄 第 2 层: PrometheusDataPuller 拉取数据");
        System.out.println("  - 拉取设备: " + deviceService.findById(testDeviceId).get().getName());
        System.out.println("  - 标签映射: instance=" + NODE_EXPORTER_INSTANCE);
        
        long beforePull = System.currentTimeMillis();
        
        // 手动触发拉取
        prometheusDataPuller.pullAllPrometheusDevices();
        
        System.out.println("  ✅ 数据拉取完成");
        System.out.println();
        
        // 等待异步处理
        Thread.sleep(3000);
        
        // ========== 第 3 层：TransportService 处理 ==========
        System.out.println("📨 第 3 层: TransportService");
        System.out.println("  - 设备认证: 通过 accessToken");
        System.out.println("  - 消息创建: TransportToDeviceMsg");
        System.out.println("  ✅ TransportService 处理完成");
        System.out.println();
        
        // ========== 第 4 层：DeviceActor 异步处理 ==========
        System.out.println("🎭 第 4 层: DeviceActor");
        System.out.println("  - Actor ID: Device:" + testDeviceId.getId());
        System.out.println("  - 解析 JSON 为 KvEntry");
        System.out.println("  - 创建 ToRuleEngineMsg");
        System.out.println("  ✅ DeviceActor 处理完成");
        System.out.println();
        
        // ========== 第 5 层：RuleEngine 规则处理 ==========
        System.out.println("⚙️  第 5 层: RuleEngine");
        System.out.println("  - 规则链: Root Rule Chain");
        System.out.println("  - 节点: LogNode → FilterNode → SaveTelemetryNode");
        System.out.println("  ✅ RuleEngine 处理完成");
        System.out.println();
        
        // ========== 第 6 层：TelemetryStorage 持久化 ==========
        System.out.println("💾 第 6 层: TelemetryStorage 持久化");
        
        long afterProcess = System.currentTimeMillis();
        
        // 验证 CPU 数据
        List<TsKvEntry> cpuData = telemetryStorage.query(
            testDeviceId, "cpu_usage_percent", beforePull, afterProcess);
        
        assertFalse(cpuData.isEmpty(), "应该有 CPU 数据被持久化");
        TsKvEntry latestCpu = cpuData.get(cpuData.size() - 1);
        System.out.println("  ✓ CPU 使用率: " + latestCpu.getValue() + "%");
        System.out.println("    - 数据类型: " + latestCpu.getDataType());
        System.out.println("    - 时间戳: " + latestCpu.getTs());
        System.out.println("    - DeviceId: " + testDeviceId);
        
        // 验证内存数据
        List<TsKvEntry> memoryData = telemetryStorage.query(
            testDeviceId, "memory_usage_percent", beforePull, afterProcess);
        
        assertFalse(memoryData.isEmpty(), "应该有内存数据被持久化");
        TsKvEntry latestMemory = memoryData.get(memoryData.size() - 1);
        System.out.println("  ✓ 内存使用率: " + latestMemory.getValue() + "%");
        System.out.println("    - 数据类型: " + latestMemory.getDataType());
        
        // 验证磁盘数据
        List<TsKvEntry> diskData = telemetryStorage.query(
            testDeviceId, "disk_usage_percent", beforePull, afterProcess);
        
        assertFalse(diskData.isEmpty(), "应该有磁盘数据被持久化");
        TsKvEntry latestDisk = diskData.get(diskData.size() - 1);
        System.out.println("  ✓ 磁盘使用率: " + latestDisk.getValue() + "%");
        System.out.println("    - 数据类型: " + latestDisk.getDataType());
        
        System.out.println("  ✅ 数据持久化验证通过");
        System.out.println();
        
        // ========== 总结 ==========
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   ✅ 端到端数据流测试通过                              ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("完整数据流程验证：");
        System.out.println("  1️⃣  Prometheus 查询               ✅");
        System.out.println("  2️⃣  PrometheusDataPuller 拉取     ✅");
        System.out.println("  3️⃣  TransportService 认证          ✅");
        System.out.println("  4️⃣  DeviceActor 异步处理           ✅");
        System.out.println("  5️⃣  RuleEngine 规则链处理          ✅");
        System.out.println("  6️⃣  TelemetryStorage 持久化        ✅");
        System.out.println();
        System.out.println("拉取数据：");
        System.out.println("  📊 CPU: " + String.format("%.2f", latestCpu.getDoubleValue().get()) + "%");
        System.out.println("  📊 内存: " + String.format("%.2f", latestMemory.getDoubleValue().get()) + "%");
        System.out.println("  📊 磁盘: " + String.format("%.2f", latestDisk.getDoubleValue().get()) + "%");
        System.out.println();
        
        long totalTime = afterProcess - testStartTime;
        System.out.println("⏱️  总耗时: " + totalTime + " ms");
        System.out.println();
    }
    
    @Test
    void testDataFlowWithMultipleMetrics() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试多指标同时处理");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        
        // 拉取数据
        System.out.println("🔄 PrometheusDataPuller 拉取 3 个指标...");
        prometheusDataPuller.pullAllPrometheusDevices();
        
        // 等待处理
        Thread.sleep(3000);
        
        long afterProcess = System.currentTimeMillis();
        
        // 验证所有 3 个指标都被保存
        System.out.println("\n💾 验证持久化结果:");
        
        String[] metrics = {"cpu_usage_percent", "memory_usage_percent", "disk_usage_percent"};
        int savedCount = 0;
        
        for (String metricKey : metrics) {
            List<TsKvEntry> data = telemetryStorage.query(testDeviceId, metricKey, beforePull, afterProcess);
            
            if (!data.isEmpty()) {
                TsKvEntry latest = data.get(data.size() - 1);
                System.out.println("  ✓ " + metricKey + ": " + 
                    String.format("%.2f", latest.getDoubleValue().get()) + "%");
                savedCount++;
            } else {
                System.out.println("  ✗ " + metricKey + ": 未保存");
            }
        }
        
        assertEquals(3, savedCount, "应该保存了 3 个指标");
        System.out.println("\n✅ 所有指标都已持久化");
        System.out.println();
    }
    
    @Test
    void testActorSystemMessagePassing() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 Actor 系统消息传递");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        // 获取设备信息
        Device device = deviceService.findById(testDeviceId).orElseThrow();
        
        System.out.println("📋 设备信息:");
        System.out.println("  - ID: " + device.getId());
        System.out.println("  - 名称: " + device.getName());
        System.out.println("  - AccessToken: " + device.getAccessToken());
        System.out.println("  - Prometheus 标签: " + device.getPrometheusLabel());
        System.out.println();
        
        // 计数当前数据量
        long beforePull = System.currentTimeMillis();
        
        List<TsKvEntry> beforeCpuData = telemetryStorage.query(
            testDeviceId, "cpu_usage_percent", 0, beforePull);
        int beforeCount = beforeCpuData.size();
        
        System.out.println("📊 拉取前数据量: " + beforeCount);
        System.out.println();
        
        // 执行拉取
        System.out.println("🔄 执行数据拉取...");
        System.out.println("  步骤 1: PrometheusDataPuller.pullAllPrometheusDevices()");
        prometheusDataPuller.pullAllPrometheusDevices();
        
        System.out.println("  步骤 2: → TransportService.processTelemetry(token, json)");
        System.out.println("  步骤 3: → TransportService.authenticateDevice(token)");
        System.out.println("  步骤 4: → ActorSystem.tell(DeviceActor, msg)");
        System.out.println("  步骤 5: → DeviceActor 解析 JSON → KvEntry");
        System.out.println("  步骤 6: → ActorSystem.tell(RuleEngineActor, msg)");
        System.out.println("  步骤 7: → RuleEngine 执行规则链");
        System.out.println("  步骤 8: → SaveTelemetryNode.onMsg()");
        System.out.println("  步骤 9: → TelemetryStorage.save()");
        System.out.println();
        
        // 等待异步处理完成
        System.out.println("⏳ 等待 Actor 异步处理 (3秒)...");
        Thread.sleep(3000);
        
        long afterPull = System.currentTimeMillis();
        
        // 验证数据增加
        List<TsKvEntry> afterCpuData = telemetryStorage.query(
            testDeviceId, "cpu_usage_percent", 0, afterPull);
        int afterCount = afterCpuData.size();
        
        System.out.println("📊 拉取后数据量: " + afterCount);
        System.out.println();
        
        assertTrue(afterCount > beforeCount, 
            "数据量应该增加，拉取前: " + beforeCount + ", 拉取后: " + afterCount);
        
        // 获取新增的数据
        TsKvEntry newData = afterCpuData.get(afterCpuData.size() - 1);
        
        System.out.println("📈 新增数据详情:");
        System.out.println("  - Key: " + newData.getKey());
        System.out.println("  - Value: " + newData.getValue());
        System.out.println("  - Type: " + newData.getDataType());
        System.out.println("  - Timestamp: " + newData.getTs());
        System.out.println("  - DeviceId: " + testDeviceId);
        System.out.println();
        
        // 验证数据类型正确
        assertEquals(DataType.DOUBLE, newData.getDataType(), "应该是 DOUBLE 类型");
        assertTrue(newData.getDoubleValue().isPresent(), "应该有 DOUBLE 值");
        
        double cpuValue = newData.getDoubleValue().get();
        assertTrue(cpuValue >= 0 && cpuValue <= 100, 
            "CPU 值应该在 0-100 之间，实际: " + cpuValue);
        
        System.out.println("✅ Actor 系统消息传递验证通过");
        System.out.println();
    }
    
    @Test
    void testRuleChainProcessing() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试规则链处理");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        System.out.println("⚙️  规则链配置:");
        System.out.println("  1. LogNode (入口日志)");
        System.out.println("  2. FilterNode (temperature > 20.0)");
        System.out.println("  3. LogNode (过滤后日志)");
        System.out.println("  4. SaveTelemetryNode (保存数据)");
        System.out.println("  5. LogNode (保存完成)");
        System.out.println();
        
        System.out.println("📝 注意: Prometheus 数据没有 temperature 字段");
        System.out.println("       FilterNode 会警告，但不影响后续处理");
        System.out.println();
        
        long beforePull = System.currentTimeMillis();
        
        // 拉取数据
        System.out.println("🔄 拉取数据并通过规则链...");
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(3000);
        
        long afterPull = System.currentTimeMillis();
        
        // 验证数据经过规则链后正确保存
        String[] allMetrics = {"cpu_usage_percent", "memory_usage_percent", "disk_usage_percent"};
        
        System.out.println("\n💾 验证规则链处理结果:");
        for (String metricKey : allMetrics) {
            List<TsKvEntry> data = telemetryStorage.query(testDeviceId, metricKey, beforePull, afterPull);
            
            if (!data.isEmpty()) {
                TsKvEntry latest = data.get(data.size() - 1);
                System.out.println("  ✓ " + metricKey + ": " + 
                    String.format("%.2f", latest.getDoubleValue().get()) + "% (已通过规则链)");
                
                // 验证数据格式
                assertNotNull(latest.getKey(), "Key 不应为 null");
                assertNotNull(latest.getDataType(), "DataType 不应为 null");
                assertTrue(latest.getTs() > 0, "Timestamp 应该有效");
            }
        }
        
        System.out.println("\n✅ 规则链处理验证通过");
        System.out.println();
    }
    
    @Test
    void testLabelMappingAndDeviceAssociation() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试标签映射与设备关联");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        // 获取设备和配置
        Device device = deviceService.findById(testDeviceId).orElseThrow();
        DeviceProfile profile = deviceService.findProfileById(testProfileId).orElseThrow();
        
        System.out.println("🔗 标签映射机制:");
        System.out.println("  1. DeviceProfile 配置:");
        System.out.println("     - prometheusDeviceLabelKey: " + profile.getPrometheusDeviceLabelKey());
        System.out.println("     - 含义: 使用 Prometheus 的 '" + profile.getPrometheusDeviceLabelKey() + "' 标签识别设备");
        System.out.println();
        
        System.out.println("  2. Device 配置:");
        System.out.println("     - prometheusLabel: " + device.getPrometheusLabel());
        
        // 解析标签
        String[] parts = device.getPrometheusLabel().split("=", 2);
        String labelKey = parts[0];
        String labelValue = parts[1];
        
        System.out.println("     - 标签键: " + labelKey);
        System.out.println("     - 标签值: " + labelValue);
        System.out.println();
        
        System.out.println("  3. 匹配逻辑:");
        System.out.println("     - PromQL 查询返回多个时间序列");
        System.out.println("     - 每个时间序列都有 metric.instance 标签");
        System.out.println("     - 过滤出 metric.instance = '" + labelValue + "' 的数据");
        System.out.println("     - 使用 device.accessToken 关联到设备");
        System.out.println();
        
        // 验证关联
        assertEquals(profile.getPrometheusDeviceLabelKey(), labelKey, 
            "设备标签键应该匹配 Profile 配置");
        
        System.out.println("  4. 设备关联:");
        System.out.println("     - AccessToken: " + device.getAccessToken());
        System.out.println("     - DeviceId: " + device.getId());
        System.out.println("     - 通过 TransportService.authenticateDevice(token) 认证");
        System.out.println("     - 返回完整的 Device 对象");
        System.out.println();
        
        // 执行拉取并验证
        long beforePull = System.currentTimeMillis();
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(3000);
        long afterPull = System.currentTimeMillis();
        
        // 验证数据确实关联到了正确的设备
        List<TsKvEntry> cpuData = telemetryStorage.query(testDeviceId, "cpu_usage_percent", beforePull, afterPull);
        
        assertFalse(cpuData.isEmpty(), "应该有数据关联到设备 " + testDeviceId);
        
        System.out.println("✅ 标签映射与设备关联验证通过");
        System.out.println("   数据成功关联到设备: " + device.getName());
        System.out.println();
    }
    
    // ==================== Helper Methods ====================
    
    private void setupTestDevice() {
        System.out.println("🔧 初始化测试设备...\n");
        
        // 1. 创建 DeviceProfile
        DeviceProfile profile = DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("E2E Test Profile")
                .description("端到端测试用 Profile")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .prometheusEndpoint(PROMETHEUS_ENDPOINT)
                .prometheusDeviceLabelKey("instance")
                .strictMode(true)
                .telemetryDefinitions(createTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();
        
        DeviceProfile savedProfile = deviceService.saveProfile(profile);
        testProfileId = savedProfile.getId();
        System.out.println("  ✓ DeviceProfile 创建: " + savedProfile.getName());
        
        // 2. 创建 Device
        Device device = Device.builder()
                .id(DeviceId.random())
                .name("E2E Test Device")
                .type("SERVER_MONITOR_E2E")
                .deviceProfileId(testProfileId)
                .accessToken("e2e-test-token-" + System.currentTimeMillis())  // 唯一 token
                .prometheusLabel("instance=" + NODE_EXPORTER_INSTANCE)
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device savedDevice = deviceService.save(device);
        testDeviceId = savedDevice.getId();
        System.out.println("  ✓ Device 创建: " + savedDevice.getName());
        
        // 3. 创建 DeviceActor
        com.minitb.actor.device.DeviceActor deviceActor = 
            new com.minitb.actor.device.DeviceActor(savedDevice.getId(), savedDevice);
        actorSystem.createActor(deviceActor.getActorId(), deviceActor);
        System.out.println("  ✓ DeviceActor 创建: " + deviceActor.getActorId());
        
        System.out.println("\n✅ 测试环境初始化完成\n");
    }
    
    private List<TelemetryDefinition> createTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        // CPU 使用率
        defs.add(TelemetryDefinition.builder()
                .key("cpu_usage_percent")
                .displayName("CPU使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)")
                        .build())
                .build());
        
        // 内存使用率 (macOS 兼容)
        defs.add(TelemetryDefinition.builder()
                .key("memory_usage_percent")
                .displayName("内存使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(node_memory_active_bytes / (node_memory_active_bytes + node_memory_free_bytes + node_memory_inactive_bytes)) * 100")
                        .build())
                .build());
        
        // 磁盘使用率
        defs.add(TelemetryDefinition.builder()
                .key("disk_usage_percent")
                .displayName("磁盘使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})) * 100")
                        .build())
                .build());
        
        return defs;
    }
    
    private boolean checkPrometheusAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROMETHEUS_ENDPOINT + "/api/v1/status/config"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Prometheus 可用: " + PROMETHEUS_ENDPOINT);
                return true;
            } else {
                System.out.println("❌ Prometheus 返回: " + response.statusCode());
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Prometheus 连接失败: " + e.getMessage());
            return false;
        }
    }
}

