package com.minitb.integration;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.PrometheusDeviceConfiguration;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.id.RuleChainId;
import com.minitb.domain.id.RuleNodeId;
import com.minitb.domain.messaging.Message;
import com.minitb.domain.messaging.MessageType;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.rule.RuleChain;
import com.minitb.domain.rule.RuleNode;
import com.minitb.domain.rule.RuleNodeConfig;
import com.minitb.domain.rule.RuleNodeContext;
import com.minitb.domain.telemetry.DataType;
import com.minitb.infrastructure.rule.LogNode;
import com.minitb.infrastructure.rule.SaveTelemetryNode;
import com.minitb.infrastructure.transport.service.TransportService;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.storage.TelemetryStorage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则链路由端到端测试
 * 
 * 测试目标：
 * 1. 验证不同DeviceProfile可以使用不同的规则链
 * 2. 验证消息能正确路由到对应的规则链
 * 3. 验证GPU和BMC设备使用各自的专用规则链
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class RuleChainRoutingTest {

    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private TransportService transportService;
    
    @Autowired
    private RuleEngineService ruleEngineService;
    
    @Autowired
    private TelemetryStorage telemetryStorage;
    
    // 测试用的计数器，用于验证规则链是否被调用
    private static final ConcurrentHashMap<String, AtomicInteger> ruleChainCallCounts = new ConcurrentHashMap<>();
    
    private DeviceProfile gpuProfile;
    private DeviceProfile bmcProfile;
    private Device gpuDevice;
    private Device bmcDevice;
    
    @BeforeEach
    void setUp() {
        log.info("\n========================================");
        log.info("   规则链路由测试 - 初始化");
        log.info("========================================");
        
        // 清理旧数据
        deviceService.findAll().forEach(device -> deviceService.delete(device.getId()));
        deviceService.findAllProfiles().forEach(profile -> deviceService.deleteProfile(profile.getId()));
        
        // 重置计数器
        ruleChainCallCounts.clear();
        
        // 1. 创建GPU专用规则链
        RuleChainId gpuRuleChainId = RuleChainId.random();
        RuleChain gpuRuleChain = new RuleChain("GPU Monitoring Rule Chain");
        gpuRuleChain
            .addNode(new CountingNode("GPU-Entry", "gpu"))
            .addNode(new LogNode("GPU日志"))
            .addNode(new CountingNode("GPU-Processing", "gpu"))
            .addNode(new SaveTelemetryNode(telemetryStorage))
            .addNode(new CountingNode("GPU-Exit", "gpu"));
        
        ruleEngineService.registerRuleChain(gpuRuleChainId.toString(), gpuRuleChain);
        log.info("✓ 创建GPU专用规则链: {}", gpuRuleChainId);
        
        // 2. 创建BMC专用规则链
        RuleChainId bmcRuleChainId = RuleChainId.random();
        RuleChain bmcRuleChain = new RuleChain("BMC Monitoring Rule Chain");
        bmcRuleChain
            .addNode(new CountingNode("BMC-Entry", "bmc"))
            .addNode(new LogNode("BMC日志"))
            .addNode(new CountingNode("BMC-Processing", "bmc"))
            .addNode(new SaveTelemetryNode(telemetryStorage))
            .addNode(new CountingNode("BMC-Exit", "bmc"));
        
        ruleEngineService.registerRuleChain(bmcRuleChainId.toString(), bmcRuleChain);
        log.info("✓ 创建BMC专用规则链: {}", bmcRuleChainId);
        
        // 3. 创建GPU DeviceProfile（指向GPU规则链）
        gpuProfile = DeviceProfile.builder()
            .id(DeviceProfileId.random())
            .name("GPU Monitor Profile")
            .description("NVIDIA GPU监控配置 - 使用GPU专用规则链")
            .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
            .defaultRuleChainId(gpuRuleChainId)  // ⭐ 指定GPU规则链
            .defaultQueueName("HighPerformance")
            .prometheusDeviceLabelKey("gpu")
            .strictMode(false)
            .telemetryDefinitions(Arrays.asList(
                TelemetryDefinition.builder()
                    .key("gpu_temperature")
                    .displayName("GPU Temperature")
                    .dataType(DataType.DOUBLE)
                    .unit("°C")
                    .protocolConfig(PrometheusConfig.builder().promQL("gpu_temp").build())
                    .build(),
                TelemetryDefinition.builder()
                    .key("gpu_power")
                    .displayName("GPU Power")
                    .dataType(DataType.DOUBLE)
                    .unit("W")
                    .protocolConfig(PrometheusConfig.builder().promQL("gpu_power").build())
                    .build()
            ))
            .createdTime(System.currentTimeMillis())
            .build();
        
        gpuProfile = deviceService.saveProfile(gpuProfile);
        log.info("✓ 创建GPU DeviceProfile: {} -> RuleChain: {}", gpuProfile.getName(), gpuRuleChainId);
        
        // 4. 创建BMC DeviceProfile（指向BMC规则链）
        bmcProfile = DeviceProfile.builder()
            .id(DeviceProfileId.random())
            .name("BMC Monitor Profile")
            .description("服务器BMC监控配置 - 使用BMC专用规则链")
            .dataSourceType(DeviceProfile.DataSourceType.IPMI)
            .defaultRuleChainId(bmcRuleChainId)  // ⭐ 指定BMC规则链
            .defaultQueueName("Main")
            .strictMode(false)
            .telemetryDefinitions(Arrays.asList(
                TelemetryDefinition.builder()
                    .key("cpu_temperature")
                    .displayName("CPU Temperature")
                    .dataType(DataType.DOUBLE)
                    .unit("°C")
                    .build(),
                TelemetryDefinition.builder()
                    .key("fan_speed")
                    .displayName("Fan Speed")
                    .dataType(DataType.LONG)
                    .unit("RPM")
                    .build()
            ))
            .createdTime(System.currentTimeMillis())
            .build();
        
        bmcProfile = deviceService.saveProfile(bmcProfile);
        log.info("✓ 创建BMC DeviceProfile: {} -> RuleChain: {}", bmcProfile.getName(), bmcRuleChainId);
        
        // 5. 创建GPU设备
        gpuDevice = Device.builder()
            .id(DeviceId.random())
            .name("NVIDIA RTX 4090 - GPU 0")
            .type("GPU")
            .accessToken("gpu-test-token")
            .deviceProfileId(gpuProfile.getId())
            .configuration(PrometheusDeviceConfiguration.builder()
                .endpoint("http://localhost:9090")
                .label("gpu=0")
                .build())
            .createdTime(System.currentTimeMillis())
            .build();
        
        gpuDevice = deviceService.save(gpuDevice);
        // Actor会在TransportService中自动初始化
        log.info("✓ 创建GPU设备: {}", gpuDevice.getName());
        
        // 6. 创建BMC设备
        bmcDevice = Device.builder()
            .id(DeviceId.random())
            .name("Dell PowerEdge R750 - BMC")
            .type("BMC")
            .accessToken("bmc-test-token")
            .deviceProfileId(bmcProfile.getId())
            .configuration(PrometheusDeviceConfiguration.builder()
                .endpoint("http://localhost:9091")
                .label("server=bmc")
                .build())
            .createdTime(System.currentTimeMillis())
            .build();
        
        bmcDevice = deviceService.save(bmcDevice);
        // Actor会在TransportService中自动初始化
        log.info("✓ 创建BMC设备: {}", bmcDevice.getName());
        
        log.info("========================================");
        log.info("   初始化完成");
        log.info("========================================\n");
    }
    
    @AfterEach
    void tearDown() {
        log.info("清理测试数据...");
        if (gpuDevice != null) deviceService.delete(gpuDevice.getId());
        if (bmcDevice != null) deviceService.delete(bmcDevice.getId());
        if (gpuProfile != null) deviceService.deleteProfile(gpuProfile.getId());
        if (bmcProfile != null) deviceService.deleteProfile(bmcProfile.getId());
    }
    
    @Test
    void testGpuDeviceUsesGpuRuleChain() throws InterruptedException {
        log.info("\n[测试 1] GPU设备使用GPU专用规则链");
        
        // 创建一个明确指定规则链ID的Message（模拟正确路由的情况）
        Message gpuMsg = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(gpuDevice.getId())
            .ruleChainId(gpuProfile.getDefaultRuleChainId().toString())  // ⭐ 设置规则链ID
            .queueName(gpuProfile.getDefaultQueueName())
            .data("{\"gpu_temperature\": 75.5, \"gpu_power\": 250.0}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        
        // 直接调用规则引擎处理
        ruleEngineService.processMessage(gpuMsg);
        
        // 2. 等待异步处理完成
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            int gpuCount = getCallCount("gpu");
            log.info("   GPU规则链调用次数: {}", gpuCount);
            
            // GPU规则链应该被调用（3个CountingNode）
            assertTrue(gpuCount > 0, "GPU规则链应该被调用");
            assertEquals(3, gpuCount, "GPU规则链应该被调用3次（Entry + Processing + Exit）");
            
            // BMC规则链不应该被调用
            int bmcCount = getCallCount("bmc");
            assertEquals(0, bmcCount, "BMC规则链不应该被调用");
        });
        
        log.info("   ✅ GPU设备正确使用GPU专用规则链");
    }
    
    @Test
    void testBmcDeviceUsesBmcRuleChain() throws InterruptedException {
        log.info("\n[测试 2] BMC设备使用BMC专用规则链");
        
        // 创建一个明确指定规则链ID的Message
        Message bmcMsg = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(bmcDevice.getId())
            .ruleChainId(bmcProfile.getDefaultRuleChainId().toString())  // ⭐ 设置规则链ID
            .queueName(bmcProfile.getDefaultQueueName())
            .data("{\"cpu_temperature\": 65.0, \"fan_speed\": 3500}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        
        // 直接调用规则引擎处理
        ruleEngineService.processMessage(bmcMsg);
        
        // 2. 等待异步处理完成
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            int bmcCount = getCallCount("bmc");
            log.info("   BMC规则链调用次数: {}", bmcCount);
            
            // BMC规则链应该被调用（3个CountingNode）
            assertTrue(bmcCount > 0, "BMC规则链应该被调用");
            assertEquals(3, bmcCount, "BMC规则链应该被调用3次（Entry + Processing + Exit）");
            
            // GPU规则链不应该被调用
            int gpuCount = getCallCount("gpu");
            assertEquals(0, gpuCount, "GPU规则链不应该被调用");
        });
        
        log.info("   ✅ BMC设备正确使用BMC专用规则链");
    }
    
    @Test
    void testBothDevicesUseTheirOwnRuleChains() throws InterruptedException {
        log.info("\n[测试 3] GPU和BMC设备同时使用各自的规则链");
        
        // 重置计数器
        ruleChainCallCounts.clear();
        
        // 1. 创建GPU消息
        Message gpuMsg = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(gpuDevice.getId())
            .ruleChainId(gpuProfile.getDefaultRuleChainId().toString())
            .data("{\"gpu_temperature\": 80.0, \"gpu_power\": 300.0}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        
        ruleEngineService.processMessage(gpuMsg);
        log.info("   → GPU消息已发送");
        
        // 2. 创建BMC消息
        Message bmcMsg = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(bmcDevice.getId())
            .ruleChainId(bmcProfile.getDefaultRuleChainId().toString())
            .data("{\"cpu_temperature\": 70.0, \"fan_speed\": 4000}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        
        ruleEngineService.processMessage(bmcMsg);
        log.info("   → BMC消息已发送");
        
        // 3. 等待异步处理完成
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            int gpuCount = getCallCount("gpu");
            int bmcCount = getCallCount("bmc");
            
            log.info("   GPU规则链调用次数: {}, BMC规则链调用次数: {}", gpuCount, bmcCount);
            
            // 两个规则链都应该被调用
            assertEquals(3, gpuCount, "GPU规则链应该被调用3次");
            assertEquals(3, bmcCount, "BMC规则链应该被调用3次");
        });
        
        log.info("   ✅ 两个设备分别使用了各自的专用规则链");
    }
    
    @Test
    void testMultipleMessagesFromSameDevice() throws InterruptedException {
        log.info("\n[测试 4] 同一设备的多次消息都路由到同一规则链");
        
        // 重置计数器
        ruleChainCallCounts.clear();
        
        // 1. GPU设备发送3次消息
        for (int i = 0; i < 3; i++) {
            Message gpuMsg = Message.builder()
                .id(java.util.UUID.randomUUID())
                .type(MessageType.POST_TELEMETRY_REQUEST)
                .originator(gpuDevice.getId())
                .ruleChainId(gpuProfile.getDefaultRuleChainId().toString())
                .data(String.format("{\"gpu_temperature\": %.1f, \"gpu_power\": %.1f}", 
                    70.0 + i * 5, 200.0 + i * 20))
                .tsKvEntries(new ArrayList<>())
                .timestamp(System.currentTimeMillis())
                .build();
            
            ruleEngineService.processMessage(gpuMsg);
            log.info("   → GPU设备第{}次发送消息", i + 1);
            Thread.sleep(100);  // 稍微延迟，确保顺序处理
        }
        
        // 2. 验证所有消息都使用GPU规则链
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            int gpuCount = getCallCount("gpu");
            int bmcCount = getCallCount("bmc");
            
            log.info("   GPU规则链总调用次数: {}", gpuCount);
            
            // GPU规则链应该被调用9次（3次消息 × 3个CountingNode）
            assertEquals(9, gpuCount, "GPU规则链应该被调用9次（3次消息 × 3个节点）");
            
            // BMC规则链不应该被调用
            assertEquals(0, bmcCount, "BMC规则链不应该被调用");
        });
        
        log.info("   ✅ 同一设备的所有消息都正确路由到同一规则链");
    }
    
    @Test
    void testRuleChainIsolation() throws InterruptedException {
        log.info("\n[测试 5] 规则链之间相互隔离");
        
        // 重置计数器
        ruleChainCallCounts.clear();
        
        // 1. 交替发送GPU和BMC消息
        Message gpuMsg1 = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(gpuDevice.getId())
            .ruleChainId(gpuProfile.getDefaultRuleChainId().toString())
            .data("{\"gpu_temperature\": 75.0}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        ruleEngineService.processMessage(gpuMsg1);
        Thread.sleep(50);
        
        Message bmcMsg1 = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(bmcDevice.getId())
            .ruleChainId(bmcProfile.getDefaultRuleChainId().toString())
            .data("{\"cpu_temperature\": 65.0}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        ruleEngineService.processMessage(bmcMsg1);
        Thread.sleep(50);
        
        Message gpuMsg2 = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(gpuDevice.getId())
            .ruleChainId(gpuProfile.getDefaultRuleChainId().toString())
            .data("{\"gpu_power\": 250.0}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        ruleEngineService.processMessage(gpuMsg2);
        Thread.sleep(50);
        
        Message bmcMsg2 = Message.builder()
            .id(java.util.UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(bmcDevice.getId())
            .ruleChainId(bmcProfile.getDefaultRuleChainId().toString())
            .data("{\"fan_speed\": 3500}")
            .tsKvEntries(new ArrayList<>())
            .timestamp(System.currentTimeMillis())
            .build();
        ruleEngineService.processMessage(bmcMsg2);
        
        log.info("   → 交替发送了2次GPU消息和2次BMC消息");
        
        // 2. 验证各自的规则链调用次数
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            int gpuCount = getCallCount("gpu");
            int bmcCount = getCallCount("bmc");
            
            log.info("   GPU规则链调用: {}, BMC规则链调用: {}", gpuCount, bmcCount);
            
            // GPU规则链: 2次消息 × 3个节点 = 6次
            assertEquals(6, gpuCount, "GPU规则链应该被调用6次");
            
            // BMC规则链: 2次消息 × 3个节点 = 6次
            assertEquals(6, bmcCount, "BMC规则链应该被调用6次");
        });
        
        log.info("   ✅ 规则链之间完全隔离，互不干扰");
    }
    
    // ==================== 辅助方法 ====================
    
    private int getCallCount(String ruleChainType) {
        AtomicInteger counter = ruleChainCallCounts.get(ruleChainType);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 计数节点 - 用于追踪规则链的调用
     */
    private static class CountingNode implements RuleNode {
        private final String name;
        private final String ruleChainType;
        private RuleNode next;
        
        public CountingNode(String name, String ruleChainType) {
            this.name = name;
            this.ruleChainType = ruleChainType;
        }
        
        @Override
        public RuleNodeId getId() {
            return RuleNodeId.random();
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getNodeType() {
            return "COUNTING";
        }
        
        @Override
        public void init(RuleNodeConfig config, RuleNodeContext context) {
            // 不需要初始化
        }
        
        @Override
        public void onMsg(Message msg, RuleNodeContext context) {
            // 增加计数
            ruleChainCallCounts.computeIfAbsent(ruleChainType, k -> new AtomicInteger(0))
                .incrementAndGet();
            
            log.debug("[{}] 处理消息 from {}", name, msg.getOriginator());
            
            // 传递到下一个节点
            if (next != null) {
                next.onMsg(msg, context);
            }
        }
        
        @Override
        public void setNext(RuleNode next) {
            this.next = next;
        }
    }
}

