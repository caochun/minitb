package com.minitb.performance;

import lombok.Data;

/**
 * 性能测试配置类
 * 定义测试的各种参数和场景
 */
@Data
public class PerformanceTestConfig {
    
    // ==========================================
    // 基础配置
    // ==========================================
    
    /**
     * 测试名称
     */
    private String testName;
    
    /**
     * 是否使用 Actor 系统
     */
    private boolean useActorSystem = true;
    
    /**
     * Actor 线程池大小
     */
    private int actorThreadPoolSize = 5;
    
    // ==========================================
    // 设备配置
    // ==========================================
    
    /**
     * 设备数量
     */
    private int numDevices = 10;
    
    /**
     * 每设备发送的消息数量
     */
    private int msgsPerDevice = 1000;
    
    /**
     * 发送线程数（模拟并发）
     */
    private int senderThreads = 10;
    
    // ==========================================
    // 消息配置
    // ==========================================
    
    /**
     * 消息大小（字节）
     */
    private int messageSize = 100;
    
    /**
     * 消息发送间隔（毫秒，0表示最快速度）
     */
    private long sendIntervalMs = 0;
    
    /**
     * 是否包含多种数据类型
     */
    private boolean includeMultipleDataTypes = true;
    
    // ==========================================
    // 测试控制
    // ==========================================
    
    /**
     * 预热消息数量
     */
    private int warmupMessages = 1000;
    
    /**
     * 测试重复次数
     */
    private int repeatCount = 3;
    
    /**
     * 测试间隔（秒）
     */
    private int testIntervalSeconds = 10;
    
    /**
     * 是否启用详细日志
     */
    private boolean enableVerboseLogging = false;
    
    // ==========================================
    // 预设测试场景
    // ==========================================
    
    /**
     * 场景1: 单设备吞吐量测试
     */
    public static PerformanceTestConfig singleDeviceThroughput() {
        PerformanceTestConfig config = new PerformanceTestConfig();
        config.setTestName("单设备吞吐量测试");
        config.setNumDevices(1);
        config.setMsgsPerDevice(10000);
        config.setSenderThreads(1);
        config.setMessageSize(100);
        config.setSendIntervalMs(0); // 最快速度
        return config;
    }
    
    /**
     * 场景2: 多设备并发测试
     */
    public static PerformanceTestConfig multiDeviceConcurrency(int deviceCount) {
        PerformanceTestConfig config = new PerformanceTestConfig();
        config.setTestName("多设备并发测试-" + deviceCount + "设备");
        config.setNumDevices(deviceCount);
        config.setMsgsPerDevice(1000);
        config.setSenderThreads(Math.min(deviceCount, 20)); // 最多20个发送线程
        config.setMessageSize(100);
        config.setSendIntervalMs(0);
        return config;
    }
    
    /**
     * 场景3: Actor vs 同步对比测试
     */
    public static PerformanceTestConfig actorVsSyncComparison() {
        PerformanceTestConfig config = new PerformanceTestConfig();
        config.setTestName("Actor vs 同步对比测试");
        config.setNumDevices(50);
        config.setMsgsPerDevice(1000);
        config.setSenderThreads(20);
        config.setMessageSize(100);
        config.setSendIntervalMs(0);
        return config;
    }
    
    /**
     * 场景4: 消息峰值测试
     */
    public static PerformanceTestConfig messagePeakTest() {
        PerformanceTestConfig config = new PerformanceTestConfig();
        config.setTestName("消息峰值测试");
        config.setNumDevices(20);
        config.setMsgsPerDevice(5000); // 更多消息模拟峰值
        config.setSenderThreads(20);
        config.setMessageSize(200);
        config.setSendIntervalMs(0);
        return config;
    }
    
    /**
     * 场景5: 故障隔离测试
     */
    public static PerformanceTestConfig faultIsolationTest() {
        PerformanceTestConfig config = new PerformanceTestConfig();
        config.setTestName("故障隔离测试");
        config.setNumDevices(10);
        config.setMsgsPerDevice(1000);
        config.setSenderThreads(10);
        config.setMessageSize(100);
        config.setSendIntervalMs(0);
        return config;
    }
    
    /**
     * 场景6: 背压测试
     */
    public static PerformanceTestConfig backpressureTest() {
        PerformanceTestConfig config = new PerformanceTestConfig();
        config.setTestName("背压测试");
        config.setNumDevices(100);
        config.setMsgsPerDevice(10000); // 大量消息测试背压
        config.setSenderThreads(50);
        config.setMessageSize(100);
        config.setSendIntervalMs(0);
        return config;
    }
    
    // ==========================================
    // 工具方法
    // ==========================================
    
    /**
     * 计算总消息数
     */
    public int getTotalMessages() {
        return numDevices * msgsPerDevice;
    }
    
    /**
     * 计算预期测试时长（秒）
     */
    public long getExpectedDurationSeconds() {
        if (sendIntervalMs > 0) {
            return (msgsPerDevice * sendIntervalMs) / 1000;
        } else {
            // 最快速度，估算为 1 秒
            return 1;
        }
    }
    
    /**
     * 生成测试描述
     */
    public String getDescription() {
        return String.format(
            "测试: %s | 设备: %d | 消息/设备: %d | 总消息: %d | Actor: %s | 线程: %d",
            testName, numDevices, msgsPerDevice, getTotalMessages(), 
            useActorSystem ? "启用" : "禁用", senderThreads
        );
    }
}
