package com.minitb.performance;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能指标收集器
 * 线程安全地收集和统计性能数据
 */
@Slf4j
@Data
public class PerformanceMetrics {
    
    // ==========================================
    // 基础统计
    // ==========================================
    
    /**
     * 测试开始时间
     */
    private long startTime;
    
    /**
     * 测试结束时间
     */
    private long endTime;
    
    /**
     * 发送的消息总数
     */
    private final AtomicLong totalSent = new AtomicLong(0);
    
    /**
     * 成功处理的消息数
     */
    private final AtomicLong totalProcessed = new AtomicLong(0);
    
    /**
     * 失败的消息数
     */
    private final AtomicLong totalFailed = new AtomicLong(0);
    
    // ==========================================
    // 延迟统计
    // ==========================================
    
    /**
     * 所有消息的延迟（纳秒）
     * 格式: deviceId -> List<延迟>
     */
    private final ConcurrentHashMap<String, List<Long>> deviceLatencies = new ConcurrentHashMap<>();
    
    /**
     * 全局延迟列表（用于计算百分位数）
     */
    private final List<Long> allLatencies = Collections.synchronizedList(new ArrayList<>());
    
    // ==========================================
    // Actor 系统统计
    // ==========================================
    
    /**
     * Actor 队列长度统计
     * 格式: actorId -> List<队列长度>
     */
    private final ConcurrentHashMap<String, List<Integer>> actorQueueSizes = new ConcurrentHashMap<>();
    
    /**
     * 最大队列长度
     */
    private final AtomicLong maxQueueSize = new AtomicLong(0);
    
    // ==========================================
    // 系统资源统计
    // ==========================================
    
    /**
     * 内存使用统计（MB）
     */
    private final List<Long> memoryUsage = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 最大内存使用
     */
    private final AtomicLong maxMemoryUsage = new AtomicLong(0);
    
    // ==========================================
    // 构造和初始化
    // ==========================================
    
    public PerformanceMetrics() {
        this.startTime = System.nanoTime();
    }
    
    /**
     * 开始测试
     */
    public void startTest() {
        this.startTime = System.nanoTime();
        log.info("性能测试开始: {}", startTime);
    }
    
    /**
     * 结束测试
     */
    public void endTest() {
        this.endTime = System.nanoTime();
        log.info("性能测试结束: {}, 总耗时: {}ms", endTime, getTotalDurationMs());
    }
    
    // ==========================================
    // 消息统计
    // ==========================================
    
    /**
     * 记录消息发送
     */
    public void recordMessageSent() {
        totalSent.incrementAndGet();
    }
    
    /**
     * 记录消息处理成功
     */
    public void recordMessageProcessed(String deviceId, long latencyNanos) {
        totalProcessed.incrementAndGet();
        
        // 记录设备延迟
        deviceLatencies.computeIfAbsent(deviceId, k -> Collections.synchronizedList(new ArrayList<>()))
                       .add(latencyNanos);
        
        // 记录全局延迟
        allLatencies.add(latencyNanos);
    }
    
    /**
     * 记录消息处理失败
     */
    public void recordMessageFailed() {
        totalFailed.incrementAndGet();
    }
    
    // ==========================================
    // Actor 队列统计
    // ==========================================
    
    /**
     * 记录 Actor 队列长度
     */
    public void recordActorQueueSize(String actorId, int queueSize) {
        actorQueueSizes.computeIfAbsent(actorId, k -> Collections.synchronizedList(new ArrayList<>()))
                       .add(queueSize);
        
        // 更新最大队列长度
        long currentMax = maxQueueSize.get();
        while (queueSize > currentMax && !maxQueueSize.compareAndSet(currentMax, queueSize)) {
            currentMax = maxQueueSize.get();
        }
    }
    
    // ==========================================
    // 内存统计
    // ==========================================
    
    /**
     * 记录当前内存使用
     */
    public void recordMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024; // MB
        memoryUsage.add(usedMemory);
        
        // 更新最大内存使用
        long currentMax = maxMemoryUsage.get();
        while (usedMemory > currentMax && !maxMemoryUsage.compareAndSet(currentMax, usedMemory)) {
            currentMax = maxMemoryUsage.get();
        }
    }
    
    // ==========================================
    // 计算统计指标
    // ==========================================
    
    /**
     * 获取总测试时长（毫秒）
     */
    public long getTotalDurationMs() {
        if (endTime == 0) {
            return (System.nanoTime() - startTime) / 1_000_000;
        }
        return (endTime - startTime) / 1_000_000;
    }
    
    /**
     * 获取总测试时长（秒）
     */
    public double getTotalDurationSeconds() {
        return getTotalDurationMs() / 1000.0;
    }
    
    /**
     * 计算吞吐量（消息/秒）
     */
    public double getThroughput() {
        double durationSeconds = getTotalDurationSeconds();
        if (durationSeconds <= 0) return 0;
        return totalProcessed.get() / durationSeconds;
    }
    
    /**
     * 计算成功率
     */
    public double getSuccessRate() {
        long total = totalSent.get();
        if (total <= 0) return 0;
        return (double) totalProcessed.get() / total * 100;
    }
    
    /**
     * 计算平均延迟（毫秒）
     * 注意：这是单个消息的端到端延迟，包含排队时间
     */
    public double getAverageLatencyMs() {
        if (allLatencies.isEmpty()) return 0;
        
        long totalLatency = allLatencies.stream().mapToLong(Long::longValue).sum();
        return (totalLatency / 1_000_000.0) / allLatencies.size();
    }
    
    /**
     * 计算批量平均完成时间（毫秒）
     * 这是更公平的指标：总测试时间 / 处理的消息数
     * 
     * 公式：批量平均完成时间 = 1000 / 吞吐量
     * 
     * 这个指标考虑了：
     * - 所有排队时间（Netty 线程池 + Actor 邮箱）
     * - 系统的整体处理能力
     * - 批量负载下的真实表现
     * 
     * 与单消息延迟的区别：
     * - 单消息延迟：测量单个消息从发送到完成的时间（Actor模式包含邮箱排队）
     * - 批量完成时间：测量系统处理一批消息的平均效率（更公平）
     */
    public double getBatchAverageCompletionTimeMs() {
        long processed = totalProcessed.get();
        if (processed <= 0) return 0;
        
        long durationMs = getTotalDurationMs();
        return (double) durationMs / processed;
    }
    
    /**
     * 计算延迟百分位数
     */
    public double getLatencyPercentile(double percentile) {
        if (allLatencies.isEmpty()) return 0;
        
        List<Long> sortedLatencies = new ArrayList<>(allLatencies);
        Collections.sort(sortedLatencies);
        
        int index = (int) Math.ceil(percentile * sortedLatencies.size() / 100.0) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        
        return sortedLatencies.get(index) / 1_000_000.0; // 转换为毫秒
    }
    
    /**
     * 获取 P50 延迟
     */
    public double getP50LatencyMs() {
        return getLatencyPercentile(50);
    }
    
    /**
     * 获取 P95 延迟
     */
    public double getP95LatencyMs() {
        return getLatencyPercentile(95);
    }
    
    /**
     * 获取 P99 延迟
     */
    public double getP99LatencyMs() {
        return getLatencyPercentile(99);
    }
    
    /**
     * 获取最大延迟
     */
    public double getMaxLatencyMs() {
        if (allLatencies.isEmpty()) return 0;
        return Collections.max(allLatencies) / 1_000_000.0;
    }
    
    /**
     * 获取平均内存使用（MB）
     */
    public double getAverageMemoryUsage() {
        if (memoryUsage.isEmpty()) return 0;
        return memoryUsage.stream().mapToLong(Long::longValue).average().orElse(0);
    }
    
    /**
     * 获取最大内存使用（MB）
     */
    public long getMaxMemoryUsage() {
        return maxMemoryUsage.get();
    }
    
    /**
     * 获取平均队列长度
     */
    public double getAverageQueueSize() {
        if (actorQueueSizes.isEmpty()) return 0;
        
        double totalSize = 0;
        int totalSamples = 0;
        
        for (List<Integer> sizes : actorQueueSizes.values()) {
            totalSize += sizes.stream().mapToInt(Integer::intValue).sum();
            totalSamples += sizes.size();
        }
        
        return totalSamples > 0 ? totalSize / totalSamples : 0;
    }
    
    /**
     * 获取最大队列长度
     */
    public long getMaxQueueSize() {
        return maxQueueSize.get();
    }
    
    // ==========================================
    // 报告生成
    // ==========================================
    
    /**
     * 生成详细报告
     */
    public String generateDetailedReport() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("============================================\n");
        sb.append("           性能测试详细报告\n");
        sb.append("============================================\n");
        sb.append(String.format("测试时长: %.2f 秒\n", getTotalDurationSeconds()));
        sb.append(String.format("发送消息: %d 条\n", totalSent.get()));
        sb.append(String.format("处理消息: %d 条\n", totalProcessed.get()));
        sb.append(String.format("失败消息: %d 条\n", totalFailed.get()));
        sb.append(String.format("成功率: %.2f%%\n", getSuccessRate()));
        sb.append("\n");
        
        sb.append("============================================\n");
        sb.append("           吞吐量统计\n");
        sb.append("============================================\n");
        sb.append(String.format("吞吐量: %.2f msg/s\n", getThroughput()));
        sb.append("\n");
        
        sb.append("============================================\n");
        sb.append("           延迟统计\n");
        sb.append("============================================\n");
        sb.append(String.format("平均延迟: %.2f ms\n", getAverageLatencyMs()));
        sb.append(String.format("P50 延迟: %.2f ms\n", getP50LatencyMs()));
        sb.append(String.format("P95 延迟: %.2f ms\n", getP95LatencyMs()));
        sb.append(String.format("P99 延迟: %.2f ms\n", getP99LatencyMs()));
        sb.append(String.format("最大延迟: %.2f ms\n", getMaxLatencyMs()));
        sb.append("\n");
        
        sb.append("============================================\n");
        sb.append("           系统资源统计\n");
        sb.append("============================================\n");
        sb.append(String.format("平均内存: %.2f MB\n", getAverageMemoryUsage()));
        sb.append(String.format("最大内存: %d MB\n", getMaxMemoryUsage()));
        sb.append(String.format("平均队列长度: %.2f\n", getAverageQueueSize()));
        sb.append(String.format("最大队列长度: %d\n", getMaxQueueSize()));
        sb.append("\n");
        
        return sb.toString();
    }
    
    /**
     * 生成简化报告（包含公平的批量完成时间指标）
     */
    public String generateSummaryReport() {
        return String.format(
            "吞吐量: %.2f msg/s | 单消息延迟: %.2f ms | 批量完成时间: %.4f ms ⭐ | P95: %.2f ms | 成功率: %.2f%% | 内存: %d MB",
            getThroughput(), getAverageLatencyMs(), getBatchAverageCompletionTimeMs(),
            getP95LatencyMs(), getSuccessRate(), getMaxMemoryUsage()
        );
    }
    
    /**
     * 重置所有统计数据
     */
    public void reset() {
        startTime = System.nanoTime();
        endTime = 0;
        totalSent.set(0);
        totalProcessed.set(0);
        totalFailed.set(0);
        deviceLatencies.clear();
        allLatencies.clear();
        actorQueueSizes.clear();
        memoryUsage.clear();
        maxQueueSize.set(0);
        maxMemoryUsage.set(0);
    }
}
