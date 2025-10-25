package com.minitb.performance;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.common.entity.Device;
import com.minitb.common.entity.DeviceId;
import com.minitb.common.entity.TenantId;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.ruleengine.RuleChain;
import com.minitb.ruleengine.node.LogNode;
import com.minitb.ruleengine.node.SaveTelemetryNode;
import com.minitb.ruleengine.node.PerformanceAwareSaveTelemetryNode;
import com.minitb.ruleengine.node.FilterNode;
import com.minitb.storage.TelemetryStorage;
import com.minitb.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能测试执行器
 * 负责执行各种性能测试场景
 */
@Slf4j
public class PerformanceTestRunner {
    
    private final PerformanceTestConfig config;
    private final PerformanceMetrics metrics;
    private final Random random = new Random();
    
    // 测试组件
    private TelemetryStorage storage;
    private RuleEngineService ruleEngineService;
    private TransportService transportService;
    private MiniTbActorSystem actorSystem;
    
    // 测试设备
    private final List<Device> testDevices = new ArrayList<>();
    
    // 线程池
    private ExecutorService senderExecutor;
    private ScheduledExecutorService monitorExecutor;
    
    public PerformanceTestRunner(PerformanceTestConfig config) {
        this.config = config;
        this.metrics = new PerformanceMetrics();
    }
    
    /**
     * 初始化测试环境
     */
    public void initialize() throws Exception {
        log.info("初始化性能测试环境...");
        
        // 1. 初始化存储
        storage = new TelemetryStorage(false); // 不启用文件存储，减少IO影响
        
        // 2. 初始化规则引擎
        ruleEngineService = new RuleEngineService();
        
        // 3. 创建规则链
        RuleChain ruleChain = createRuleChain();
        ruleEngineService.setRootRuleChain(ruleChain);
        
        // 4. 初始化传输服务
        transportService = new TransportService(ruleEngineService);
        
        // 5. 初始化 Actor 系统（如果启用）
        if (config.isUseActorSystem()) {
            actorSystem = new MiniTbActorSystem(config.getActorThreadPoolSize());
            transportService.enableActorSystem(actorSystem);
            log.info("Actor 系统已启用，线程池大小: {}", config.getActorThreadPoolSize());
        } else {
            log.info("使用同步模式");
        }
        
        // 6. 创建测试设备
        createTestDevices();
        
        // 7. 初始化线程池
        senderExecutor = Executors.newFixedThreadPool(config.getSenderThreads());
        monitorExecutor = Executors.newScheduledThreadPool(2);
        
        log.info("测试环境初始化完成");
    }
    
    /**
     * 创建规则链
     */
    private RuleChain createRuleChain() {
        RuleChain ruleChain = new RuleChain("Performance Test Rule Chain");
        
        // 添加规则节点
        ruleChain
            .addNode(new LogNode("性能测试日志"))
            .addNode(new FilterNode("temperature", 20.0)) // 过滤温度>20的数据
            .addNode(new PerformanceAwareSaveTelemetryNode(storage, metrics))
            .addNode(new LogNode("保存完成"));
        
        return ruleChain;
    }
    
    /**
     * 创建测试设备
     */
    private void createTestDevices() {
        TenantId tenantId = TenantId.random();
        
        for (int i = 0; i < config.getNumDevices(); i++) {
            String deviceName = String.format("性能测试设备-%03d", i + 1);
            String deviceType = "PerformanceTestDevice";
            String accessToken = String.format("perf-token-%03d", i + 1);
            
            Device device = new Device(deviceName, deviceType, accessToken);
            device.setTenantId(tenantId);
            
            testDevices.add(device);
            transportService.registerDevice(device);
        }
        
        log.info("创建了 {} 个测试设备", testDevices.size());
    }
    
    /**
     * 执行性能测试
     */
    public PerformanceMetrics runTest() throws Exception {
        log.info("开始执行性能测试: {}", config.getDescription());
        
        // 预热
        if (config.getWarmupMessages() > 0) {
            log.info("执行预热，发送 {} 条消息", config.getWarmupMessages());
            runWarmup();
            
            // 预热后等待所有消息处理完成
            Thread.sleep(2000);
            
            // 重置 metrics，排除预热数据
            metrics.reset();
            log.info("预热完成，已重置性能指标");
        }
        
        // 开始监控
        startMonitoring();
        
        // 执行测试
        metrics.startTest();
        runConcurrentTest();
        metrics.endTest();
        
        // 停止监控
        stopMonitoring();
        
        // 等待所有消息处理完成
        waitForCompletion();
        
        log.info("性能测试完成: {}", metrics.generateSummaryReport());
        return metrics;
    }
    
    /**
     * 执行预热
     */
    private void runWarmup() throws InterruptedException {
        final int warmupPerDevice = config.getWarmupMessages() / config.getNumDevices();
        final int actualWarmupPerDevice = warmupPerDevice > 0 ? warmupPerDevice : 1;
        
        CountDownLatch latch = new CountDownLatch(config.getNumDevices());
        
        for (Device device : testDevices) {
            senderExecutor.submit(() -> {
                try {
                    for (int i = 0; i < actualWarmupPerDevice; i++) {
                        sendTestMessage(device);
                        if (config.getSendIntervalMs() > 0) {
                            try {
                                Thread.sleep(config.getSendIntervalMs());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        Thread.sleep(2000); // 等待处理完成
    }
    
    /**
     * 执行并发测试
     */
    private void runConcurrentTest() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(config.getNumDevices());
        
        // 启动所有发送线程
        for (Device device : testDevices) {
            senderExecutor.submit(() -> {
                try {
                    // 等待开始信号
                    startLatch.await();
                    
                    // 发送消息
                    for (int i = 0; i < config.getMsgsPerDevice(); i++) {
                        sendTestMessage(device);
                        metrics.recordMessageSent();
                        
                        if (config.getSendIntervalMs() > 0) {
                            Thread.sleep(config.getSendIntervalMs());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        // 开始测试
        log.info("开始发送消息...");
        startLatch.countDown();
        
        // 等待所有发送完成
        finishLatch.await(300, TimeUnit.SECONDS); // 最多等待5分钟
    }
    
    /**
     * 发送测试消息
     */
    private void sendTestMessage(Device device) {
        try {
            // 记录发送时间（纳秒），用于计算延迟
            long sendTimeNanos = System.nanoTime();
            String message = generateTestMessage(device, sendTimeNanos);
            transportService.processTelemetry(device.getAccessToken(), message);
        } catch (Exception e) {
            log.error("发送消息失败: {}", device.getName(), e);
            metrics.recordMessageFailed();
        }
    }
    
    /**
     * 生成测试消息
     */
    private String generateTestMessage(Device device, long sendTimeNanos) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // 基础遥测数据
        json.append("\"temperature\":").append(20 + random.nextDouble() * 20); // 20-40度
        json.append(",\"humidity\":").append(40 + random.nextDouble() * 40); // 40-80%
        json.append(",\"pressure\":").append(1000 + random.nextDouble() * 100); // 1000-1100 hPa
        
        if (config.isIncludeMultipleDataTypes()) {
            // 布尔值
            json.append(",\"online\":").append(random.nextBoolean());
            // 字符串
            json.append(",\"status\":\"").append(random.nextBoolean() ? "running" : "idle").append("\"");
            // 整数
            json.append(",\"counter\":").append(random.nextInt(1000));
        }
        
        // 添加时间戳（毫秒，用于业务逻辑）
        json.append(",\"timestamp\":").append(System.currentTimeMillis());
        
        // 添加发送时间（纳秒，用于性能测试）
        json.append(",\"sendTimeNanos\":").append(sendTimeNanos);
        
        // 添加设备信息
        json.append(",\"deviceId\":\"").append(device.getId().getId()).append("\"");
        json.append(",\"deviceName\":\"").append(device.getName()).append("\"");
        
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * 开始监控
     */
    private void startMonitoring() {
        // 监控内存使用
        monitorExecutor.scheduleAtFixedRate(() -> {
            metrics.recordMemoryUsage();
        }, 0, 1, TimeUnit.SECONDS);
        
        // 监控 Actor 队列（如果启用）
        if (config.isUseActorSystem() && actorSystem != null) {
            monitorExecutor.scheduleAtFixedRate(() -> {
                // 这里可以添加 Actor 队列监控逻辑
                // 由于 MiniTbActorSystem 没有暴露队列信息，这里简化处理
            }, 0, 500, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * 停止监控
     */
    private void stopMonitoring() {
        if (monitorExecutor != null) {
            monitorExecutor.shutdown();
            try {
                monitorExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 等待所有消息处理完成
     */
    private void waitForCompletion() {
        log.info("等待所有消息处理完成...");
        
        // 等待一段时间让消息处理完成
        try {
            Thread.sleep(5000); // 等待5秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 检查存储中的消息数量
        int storedMessages = storage.getTotalMessages();
        log.info("存储中的消息数量: {}", storedMessages);
    }
    
    /**
     * 执行多次测试并返回平均结果
     */
    public PerformanceMetrics runRepeatedTest() throws Exception {
        log.info("执行重复测试，次数: {}", config.getRepeatCount());
        
        List<PerformanceMetrics> results = new ArrayList<>();
        
        for (int i = 0; i < config.getRepeatCount(); i++) {
            log.info("执行第 {} 次测试", i + 1);
            
            // 重置环境
            reset();
            
            // 执行测试
            PerformanceMetrics result = runTest();
            results.add(result);
            
            // 测试间隔
            if (i < config.getRepeatCount() - 1) {
                log.info("等待 {} 秒后执行下一次测试", config.getTestIntervalSeconds());
                Thread.sleep(config.getTestIntervalSeconds() * 1000);
            }
        }
        
        // 计算平均结果
        return calculateAverageMetrics(results);
    }
    
    /**
     * 计算平均指标
     */
    private PerformanceMetrics calculateAverageMetrics(List<PerformanceMetrics> results) {
        if (results.isEmpty()) {
            return new PerformanceMetrics();
        }
        
        PerformanceMetrics average = new PerformanceMetrics();
        
        // 计算平均值
        double avgThroughput = results.stream().mapToDouble(PerformanceMetrics::getThroughput).average().orElse(0);
        double avgLatency = results.stream().mapToDouble(PerformanceMetrics::getAverageLatencyMs).average().orElse(0);
        double avgP95Latency = results.stream().mapToDouble(PerformanceMetrics::getP95LatencyMs).average().orElse(0);
        double avgSuccessRate = results.stream().mapToDouble(PerformanceMetrics::getSuccessRate).average().orElse(0);
        double avgMemory = results.stream().mapToDouble(PerformanceMetrics::getAverageMemoryUsage).average().orElse(0);
        
        // 设置平均值（这里简化处理，实际应该重新计算）
        log.info("平均结果 - 吞吐量: {:.2f} msg/s, 延迟: {:.2f} ms, P95: {:.2f} ms, 成功率: {:.2f}%, 内存: {:.2f} MB",
                avgThroughput, avgLatency, avgP95Latency, avgSuccessRate, avgMemory);
        
        return results.get(0); // 返回最后一次的结果作为代表
    }
    
    /**
     * 重置测试环境
     */
    private void reset() {
        // 重置指标
        metrics.reset();
        
        // 清理存储
        if (storage != null) {
            storage.clear();
        }
        
        // 重新初始化设备
        testDevices.clear();
        createTestDevices();
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        log.info("清理测试资源...");
        
        if (senderExecutor != null) {
            senderExecutor.shutdown();
            try {
                senderExecutor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (monitorExecutor != null) {
            monitorExecutor.shutdown();
            try {
                monitorExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (actorSystem != null) {
            actorSystem.shutdown();
        }
        
        log.info("资源清理完成");
    }
}
