package com.minitb.performance;

import lombok.extern.slf4j.Slf4j;

/**
 * 性能测试主程序
 * 执行 MiniTB 性能压力测试
 */
@Slf4j
public class PerformanceTestMain {
    
    public static void main(String[] args) {
        log.info("========================================");
        log.info("    MiniTB 性能压力测试程序");
        log.info("========================================");
        
        try {
            // 检查参数
            String testType = args.length > 0 ? args[0] : "full";
            
            switch (testType.toLowerCase()) {
                case "full":
                    runFullTestSuite();
                    break;
                case "single":
                    runSingleDeviceTest();
                    break;
                case "multi":
                    runMultiDeviceTest();
                    break;
                case "comparison":
                    runComparisonTest();
                    break;
                case "peak":
                    runPeakTest();
                    break;
                case "fault":
                    runFaultTest();
                    break;
                case "backpressure":
                    runBackpressureTest();
                    break;
                default:
                    printUsage();
                    break;
            }
            
        } catch (Exception e) {
            log.error("性能测试执行失败", e);
            System.exit(1);
        }
        
        // 显式退出程序，确保所有线程都被终止
        log.info("========================================");
        log.info("    性能测试全部完成，程序退出");
        log.info("========================================");
        System.exit(0);
    }
    
    /**
     * 执行完整测试套件
     */
    private static void runFullTestSuite() throws Exception {
        log.info("执行完整性能测试套件...");
        PerformanceTestSuite suite = new PerformanceTestSuite();
        suite.runFullTestSuite();
    }
    
    /**
     * 执行单设备测试
     */
    private static void runSingleDeviceTest() throws Exception {
        log.info("执行单设备吞吐量测试...");
        
        // Actor 模式
        PerformanceTestConfig actorConfig = PerformanceTestConfig.singleDeviceThroughput();
        actorConfig.setUseActorSystem(true);
        
        PerformanceTestRunner actorRunner = new PerformanceTestRunner(actorConfig);
        try {
            actorRunner.initialize();
            PerformanceMetrics actorMetrics = actorRunner.runTest();
            log.info("Actor 模式结果: {}", actorMetrics.generateSummaryReport());
        } finally {
            actorRunner.cleanup();
        }
        
        // 同步模式
        PerformanceTestConfig syncConfig = PerformanceTestConfig.singleDeviceThroughput();
        syncConfig.setUseActorSystem(false);
        
        PerformanceTestRunner syncRunner = new PerformanceTestRunner(syncConfig);
        try {
            syncRunner.initialize();
            PerformanceMetrics syncMetrics = syncRunner.runTest();
            log.info("同步模式结果: {}", syncMetrics.generateSummaryReport());
        } finally {
            syncRunner.cleanup();
        }
        
        log.info("单设备测试完成");
    }
    
    /**
     * 执行多设备测试
     */
    private static void runMultiDeviceTest() throws Exception {
        log.info("执行多设备并发测试...");
        
        int[] deviceCounts = {10, 50, 100};
        
        for (int deviceCount : deviceCounts) {
            log.info("测试 {} 个设备并发", deviceCount);
            
            PerformanceTestConfig config = PerformanceTestConfig.multiDeviceConcurrency(deviceCount);
            config.setUseActorSystem(true);
            
            PerformanceTestRunner runner = new PerformanceTestRunner(config);
            try {
                runner.initialize();
                PerformanceMetrics metrics = runner.runTest();
                log.info("{} 设备结果: {}", deviceCount, metrics.generateSummaryReport());
            } finally {
                runner.cleanup();
            }
        }
    }
    
    /**
     * 执行对比测试
     */
    private static void runComparisonTest() throws Exception {
        log.info("执行 Actor vs 同步对比测试...");
        
        PerformanceTestConfig baseConfig = PerformanceTestConfig.actorVsSyncComparison();
        
        // Actor 模式
        PerformanceTestConfig actorConfig = new PerformanceTestConfig();
        copyConfig(baseConfig, actorConfig);
        actorConfig.setUseActorSystem(true);
        actorConfig.setTestName("Actor vs 同步对比-Actor模式");
        
        PerformanceTestRunner actorRunner = new PerformanceTestRunner(actorConfig);
        try {
            actorRunner.initialize();
            PerformanceMetrics actorMetrics = actorRunner.runTest();
            log.info("Actor 模式结果: {}", actorMetrics.generateSummaryReport());
        } finally {
            actorRunner.cleanup();
        }
        
        // 同步模式
        PerformanceTestConfig syncConfig = new PerformanceTestConfig();
        copyConfig(baseConfig, syncConfig);
        syncConfig.setUseActorSystem(false);
        syncConfig.setTestName("Actor vs 同步对比-同步模式");
        
        PerformanceTestRunner syncRunner = new PerformanceTestRunner(syncConfig);
        try {
            syncRunner.initialize();
            PerformanceMetrics syncMetrics = syncRunner.runTest();
            log.info("同步模式结果: {}", syncMetrics.generateSummaryReport());
        } finally {
            syncRunner.cleanup();
        }
    }
    
    /**
     * 执行峰值测试
     */
    private static void runPeakTest() throws Exception {
        log.info("执行消息峰值测试...");
        
        PerformanceTestConfig config = PerformanceTestConfig.messagePeakTest();
        config.setUseActorSystem(true);
        
        PerformanceTestRunner runner = new PerformanceTestRunner(config);
        try {
            runner.initialize();
            PerformanceMetrics metrics = runner.runTest();
            log.info("峰值测试结果: {}", metrics.generateSummaryReport());
        } finally {
            runner.cleanup();
        }
    }
    
    /**
     * 执行故障隔离测试
     */
    private static void runFaultTest() throws Exception {
        log.info("执行故障隔离测试...");
        
        PerformanceTestConfig config = PerformanceTestConfig.faultIsolationTest();
        config.setUseActorSystem(true);
        
        PerformanceTestRunner runner = new PerformanceTestRunner(config);
        try {
            runner.initialize();
            PerformanceMetrics metrics = runner.runTest();
            log.info("故障隔离测试结果: {}", metrics.generateSummaryReport());
        } finally {
            runner.cleanup();
        }
    }
    
    /**
     * 执行背压测试
     */
    private static void runBackpressureTest() throws Exception {
        log.info("执行背压测试...");
        
        PerformanceTestConfig config = PerformanceTestConfig.backpressureTest();
        config.setUseActorSystem(true);
        
        PerformanceTestRunner runner = new PerformanceTestRunner(config);
        try {
            runner.initialize();
            PerformanceMetrics metrics = runner.runTest();
            log.info("背压测试结果: {}", metrics.generateSummaryReport());
        } finally {
            runner.cleanup();
        }
    }
    
    /**
     * 复制配置
     */
    private static void copyConfig(PerformanceTestConfig source, PerformanceTestConfig target) {
        target.setNumDevices(source.getNumDevices());
        target.setMsgsPerDevice(source.getMsgsPerDevice());
        target.setSenderThreads(source.getSenderThreads());
        target.setMessageSize(source.getMessageSize());
        target.setSendIntervalMs(source.getSendIntervalMs());
        target.setIncludeMultipleDataTypes(source.isIncludeMultipleDataTypes());
        target.setWarmupMessages(source.getWarmupMessages());
        target.setRepeatCount(source.getRepeatCount());
        target.setTestIntervalSeconds(source.getTestIntervalSeconds());
        target.setEnableVerboseLogging(source.isEnableVerboseLogging());
    }
    
    /**
     * 打印使用说明
     */
    private static void printUsage() {
        log.info("========================================");
        log.info("           使用说明");
        log.info("========================================");
        log.info("用法: java PerformanceTestMain [测试类型]");
        log.info("");
        log.info("测试类型:");
        log.info("  full        - 执行完整测试套件（默认）");
        log.info("  single      - 单设备吞吐量测试");
        log.info("  multi       - 多设备并发测试");
        log.info("  comparison  - Actor vs 同步对比测试");
        log.info("  peak        - 消息峰值测试");
        log.info("  fault       - 故障隔离测试");
        log.info("  backpressure - 背压测试");
        log.info("");
        log.info("示例:");
        log.info("  java PerformanceTestMain full");
        log.info("  java PerformanceTestMain single");
        log.info("  java PerformanceTestMain comparison");
        log.info("========================================");
    }
}
