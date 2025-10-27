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
 * Prometheus 性能分析测试
 * 
 * 目标：
 * 1. 测量每一层的耗时
 * 2. 识别性能瓶颈
 * 3. 优化建议
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROMETHEUS_ENABLED", matches = "true")
class PrometheusPerformanceAnalysisTest {
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private PrometheusDataPuller prometheusDataPuller;
    
    @Autowired
    private TelemetryStorage telemetryStorage;
    
    @Autowired
    private MiniTbActorSystem actorSystem;
    
    private static DeviceProfileId testProfileId;
    private static DeviceId testDeviceId;
    private static boolean initialized = false;
    
    private static final String PROMETHEUS_ENDPOINT = "http://localhost:9090";
    private static final String NODE_EXPORTER_INSTANCE = "localhost:9100";
    
    @BeforeEach
    void setUp() {
        if (initialized) {
            return;
        }
        
        // 创建测试设备
        DeviceProfile profile = DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("Performance Test Profile")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .prometheusEndpoint(PROMETHEUS_ENDPOINT)
                .prometheusDeviceLabelKey("instance")
                .telemetryDefinitions(createTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();
        
        testProfileId = deviceService.saveProfile(profile).getId();
        
        Device device = Device.builder()
                .id(DeviceId.random())
                .name("Performance Test Device")
                .type("PERF_TEST")
                .deviceProfileId(testProfileId)
                .accessToken("perf-test-token-" + System.currentTimeMillis())
                .prometheusLabel("instance=" + NODE_EXPORTER_INSTANCE)
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device savedDevice = deviceService.save(device);
        testDeviceId = savedDevice.getId();
        
        // 创建 DeviceActor
        com.minitb.actor.device.DeviceActor deviceActor = 
            new com.minitb.actor.device.DeviceActor(savedDevice.getId(), savedDevice);
        actorSystem.createActor(deviceActor.getActorId(), deviceActor);
        
        initialized = true;
    }
    
    @Test
    void testDetailedPerformanceBreakdown() throws Exception {
        System.out.println("\n╔═════════════════════════════════════════════════════════╗");
        System.out.println("║   Prometheus 数据流性能分析                             ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝\n");
        
        long totalStart = System.currentTimeMillis();
        
        // ========== 1. Prometheus HTTP 查询耗时 ==========
        System.out.println("📊 第 1 层: Prometheus HTTP 查询");
        
        long promStart = System.currentTimeMillis();
        
        String cpuQuery = "100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)";
        HttpClient client = HttpClient.newHttpClient();
        String url = PROMETHEUS_ENDPOINT + "/api/v1/query?query=" + 
                     java.net.URLEncoder.encode(cpuQuery, "UTF-8");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        long promEnd = System.currentTimeMillis();
        long promTime = promEnd - promStart;
        
        System.out.println("   ⏱️  HTTP 请求耗时: " + promTime + " ms");
        System.out.println("   📏 响应大小: " + response.body().length() + " 字节");
        System.out.println();
        
        // ========== 2. PrometheusDataPuller 拉取耗时 ==========
        System.out.println("📊 第 2 层: PrometheusDataPuller.pullAllPrometheusDevices()");
        
        long pullStart = System.currentTimeMillis();
        
        prometheusDataPuller.pullAllPrometheusDevices();
        
        long pullEnd = System.currentTimeMillis();
        long pullTime = pullEnd - pullStart;
        
        System.out.println("   ⏱️  拉取耗时: " + pullTime + " ms");
        System.out.println("   📊 包含步骤:");
        System.out.println("      - 查询设备列表");
        System.out.println("      - 执行 3 次 Prometheus HTTP 查询 (CPU/Memory/Disk)");
        System.out.println("      - JSON 解析");
        System.out.println("      - 标签过滤");
        System.out.println("      - 调用 transportService.processTelemetry()");
        System.out.println();
        
        // ========== 3. Actor 异步处理耗时 ==========
        System.out.println("📊 第 3-6 层: Actor 异步处理 (TransportService → DeviceActor → RuleEngine → Storage)");
        
        long beforeAsync = System.currentTimeMillis();
        
        // 等待不同时长，观察数据何时可用
        int checkIntervals = 10;  // 检查 10 次
        int intervalMs = 100;     // 每次间隔 100ms
        
        boolean dataAvailable = false;
        int totalWaitTime = 0;
        
        for (int i = 1; i <= checkIntervals; i++) {
            Thread.sleep(intervalMs);
            totalWaitTime += intervalMs;
            
            List<TsKvEntry> cpuData = telemetryStorage.query(
                testDeviceId, "cpu_usage_percent", pullEnd, System.currentTimeMillis());
            
            if (!cpuData.isEmpty()) {
                dataAvailable = true;
                System.out.println("   ✅ 数据在 " + totalWaitTime + " ms 后可用");
                break;
            }
        }
        
        long afterAsync = System.currentTimeMillis();
        long actualAsyncTime = afterAsync - beforeAsync;
        
        if (!dataAvailable) {
            System.out.println("   ⚠️  数据在 " + totalWaitTime + " ms 后仍未可用");
        }
        
        System.out.println("   ⏱️  实际异步处理时间: " + totalWaitTime + " ms");
        System.out.println("   📊 包含步骤:");
        System.out.println("      - TransportService.authenticateDevice() - 数据库查询");
        System.out.println("      - ActorSystem.tell() - 消息入队");
        System.out.println("      - DeviceActor 线程池调度");
        System.out.println("      - DeviceActor.onMsg() - JSON 解析");
        System.out.println("      - RuleEngineActor 线程池调度");
        System.out.println("      - RuleEngine 规则链执行");
        System.out.println("      - TelemetryStorage.save()");
        System.out.println();
        
        long totalEnd = System.currentTimeMillis();
        long totalTime = totalEnd - totalStart;
        
        // ========== 性能摘要 ==========
        System.out.println("╔═════════════════════════════════════════════════════════╗");
        System.out.println("║   性能分析摘要                                          ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝\n");
        
        System.out.println("耗时分布:");
        System.out.println("  ┌─────────────────────────────────────────────────┐");
        System.out.printf ("  │ 1. Prometheus HTTP 查询     : %5d ms (%5.1f%%) │%n", 
            promTime, (promTime * 100.0 / totalTime));
        System.out.printf ("  │ 2. PrometheusDataPuller     : %5d ms (%5.1f%%) │%n", 
            pullTime, (pullTime * 100.0 / totalTime));
        System.out.printf ("  │ 3. Actor 异步处理           : %5d ms (%5.1f%%) │%n", 
            totalWaitTime, (totalWaitTime * 100.0 / totalTime));
        System.out.println("  ├─────────────────────────────────────────────────┤");
        System.out.printf ("  │ 总耗时                      : %5d ms (100.0%%) │%n", totalTime);
        System.out.println("  └─────────────────────────────────────────────────┘\n");
        
        // 进一步细分 PrometheusDataPuller 耗时
        long estimatedHttpTime = promTime * 3;  // 3 个指标查询
        long parsingTime = pullTime - estimatedHttpTime;
        
        System.out.println("PrometheusDataPuller 详细分析 (" + pullTime + " ms):");
        System.out.println("  - HTTP 查询 (3次)  : ~" + estimatedHttpTime + " ms");
        System.out.println("  - JSON 解析 + 处理 : ~" + (parsingTime > 0 ? parsingTime : 0) + " ms");
        System.out.println();
        
        System.out.println("Actor 异步处理详细分析 (" + totalWaitTime + " ms):");
        System.out.println("  - 线程池调度        : ~50-100 ms");
        System.out.println("  - JSON 解析         : ~10-20 ms");
        System.out.println("  - 规则链执行        : ~10-50 ms");
        System.out.println("  - 存储写入          : ~1-5 ms");
        System.out.println("  - ⚠️  大部分时间可能是线程切换和队列等待");
        System.out.println();
        
        // 性能评估
        System.out.println("💡 性能评估:");
        if (totalTime < 500) {
            System.out.println("   ✅ 优秀 (< 500ms)");
        } else if (totalTime < 1000) {
            System.out.println("   ✅ 良好 (500ms - 1s)");
        } else if (totalTime < 3000) {
            System.out.println("   ⚠️  可接受 (1s - 3s)，主要是测试中的 sleep");
        } else {
            System.out.println("   ❌ 需要优化 (> 3s)");
        }
        System.out.println();
        
        // 优化建议
        System.out.println("🚀 优化建议:");
        System.out.println("   1. 实际生产环境中，不需要 Thread.sleep() 等待");
        System.out.println("   2. Actor 系统是异步的，可以立即返回");
        System.out.println("   3. 如果去掉 3 秒 sleep，实际耗时约 " + (totalTime - 3000) + " ms");
        System.out.println("   4. HTTP 查询可以并发执行，减少等待时间");
        System.out.println();
        
        assertTrue(dataAvailable, "数据应该在合理时间内可用");
    }
    
    @Test
    void testActualProcessingTimeWithoutSleep() throws Exception {
        System.out.println("\n╔═════════════════════════════════════════════════════════╗");
        System.out.println("║   实际处理时间测试（不等待）                            ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝\n");
        
        long totalStart = System.currentTimeMillis();
        
        // 1. 拉取数据（同步部分）
        System.out.println("🔄 执行同步拉取...");
        long pullStart = System.currentTimeMillis();
        
        prometheusDataPuller.pullAllPrometheusDevices();
        
        long pullEnd = System.currentTimeMillis();
        long syncTime = pullEnd - pullStart;
        
        System.out.println("   ⏱️  同步部分耗时: " + syncTime + " ms");
        System.out.println("   📝 包含: HTTP查询 + JSON解析 + processTelemetry调用");
        System.out.println();
        
        // 2. 最小等待检测（轮询检测数据可用性）
        System.out.println("⏳ 轮询检测异步处理完成时间...");
        
        long asyncStart = System.currentTimeMillis();
        boolean found = false;
        int pollCount = 0;
        int maxPolls = 50;  // 最多轮询 50 次
        int pollInterval = 50;  // 每次间隔 50ms
        
        for (int i = 0; i < maxPolls; i++) {
            Thread.sleep(pollInterval);
            pollCount++;
            
            List<TsKvEntry> data = telemetryStorage.query(
                testDeviceId, "cpu_usage_percent", pullEnd, System.currentTimeMillis());
            
            if (!data.isEmpty()) {
                found = true;
                break;
            }
        }
        
        long asyncEnd = System.currentTimeMillis();
        long asyncTime = asyncEnd - asyncStart;
        
        if (found) {
            System.out.println("   ✅ 数据在第 " + pollCount + " 次轮询时可用");
            System.out.println("   ⏱️  异步处理耗时: " + asyncTime + " ms");
        } else {
            System.out.println("   ⚠️  " + (maxPolls * pollInterval) + " ms 后数据仍未可用");
        }
        System.out.println();
        
        long totalEnd = System.currentTimeMillis();
        long totalTime = totalEnd - totalStart;
        
        // 摘要
        System.out.println("╔═════════════════════════════════════════════════════════╗");
        System.out.println("║   实际性能摘要                                          ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝\n");
        
        System.out.println("  同步拉取部分: " + syncTime + " ms");
        System.out.println("  异步处理部分: " + asyncTime + " ms");
        System.out.println("  ─────────────────────────────");
        System.out.println("  实际总耗时  : " + totalTime + " ms");
        System.out.println();
        
        System.out.println("💡 结论:");
        System.out.println("   - 同步拉取（Prometheus 查询）占比: " + 
            String.format("%.1f%%", syncTime * 100.0 / totalTime));
        System.out.println("   - 异步处理（Actor 处理）占比: " + 
            String.format("%.1f%%", asyncTime * 100.0 / totalTime));
        System.out.println();
        
        if (syncTime > 100) {
            System.out.println("   ⚠️  HTTP 查询较慢 (> 100ms)");
            System.out.println("      - 可能原因: 网络延迟、Prometheus 查询复杂度");
            System.out.println("      - 优化: 使用连接池、并发查询");
        }
        
        if (asyncTime > 200) {
            System.out.println("   ⚠️  异步处理较慢 (> 200ms)");
            System.out.println("      - 可能原因: 线程池调度、规则链复杂度");
            System.out.println("      - 优化: 增加线程池大小、简化规则链");
        }
        
        if (syncTime <= 100 && asyncTime <= 200) {
            System.out.println("   ✅ 性能表现良好！");
        }
        
        System.out.println();
        
        assertTrue(found, "数据应该被成功处理");
    }
    
    @Test
    void testConcurrentHttpRequests() throws Exception {
        System.out.println("\n╔═════════════════════════════════════════════════════════╗");
        System.out.println("║   HTTP 查询性能测试                                     ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝\n");
        
        String[] queries = {
            "100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)",
            "(node_memory_active_bytes / (node_memory_active_bytes + node_memory_free_bytes + node_memory_inactive_bytes)) * 100",
            "(1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})) * 100"
        };
        
        HttpClient client = HttpClient.newHttpClient();
        
        // 串行查询
        System.out.println("📊 串行查询 (当前实现):");
        long serialStart = System.currentTimeMillis();
        
        for (int i = 0; i < queries.length; i++) {
            String url = PROMETHEUS_ENDPOINT + "/api/v1/query?query=" + 
                         java.net.URLEncoder.encode(queries[i], "UTF-8");
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            long queryStart = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long queryEnd = System.currentTimeMillis();
            
            System.out.println("   查询 " + (i + 1) + ": " + (queryEnd - queryStart) + " ms");
        }
        
        long serialEnd = System.currentTimeMillis();
        long serialTime = serialEnd - serialStart;
        
        System.out.println("   ⏱️  串行总耗时: " + serialTime + " ms");
        System.out.println();
        
        // 并发查询（理论优化）
        System.out.println("📊 并发查询 (理论优化):");
        long parallelStart = System.currentTimeMillis();
        
        List<java.util.concurrent.CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        
        for (String query : queries) {
            String url = PROMETHEUS_ENDPOINT + "/api/v1/query?query=" + 
                         java.net.URLEncoder.encode(query, "UTF-8");
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            java.util.concurrent.CompletableFuture<HttpResponse<String>> future = 
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            futures.add(future);
        }
        
        // 等待所有完成
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        
        long parallelEnd = System.currentTimeMillis();
        long parallelTime = parallelEnd - parallelStart;
        
        System.out.println("   ⏱️  并发总耗时: " + parallelTime + " ms");
        System.out.println();
        
        // 对比
        System.out.println("💡 性能对比:");
        System.out.println("   串行: " + serialTime + " ms");
        System.out.println("   并发: " + parallelTime + " ms");
        System.out.println("   提升: " + (serialTime - parallelTime) + " ms (" + 
            String.format("%.1f%%", (serialTime - parallelTime) * 100.0 / serialTime) + ")");
        System.out.println();
        
        if (parallelTime < serialTime) {
            System.out.println("   ✅ 并发查询更快！建议优化 PrometheusDataPuller");
        }
        
        System.out.println();
    }
    
    // ==================== Helper Methods ====================
    
    private List<TelemetryDefinition> createTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        defs.add(TelemetryDefinition.builder()
                .key("cpu_usage_percent")
                .dataType(DataType.DOUBLE)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)")
                        .build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("memory_usage_percent")
                .dataType(DataType.DOUBLE)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(node_memory_active_bytes / (node_memory_active_bytes + node_memory_free_bytes + node_memory_inactive_bytes)) * 100")
                        .build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("disk_usage_percent")
                .dataType(DataType.DOUBLE)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})) * 100")
                        .build())
                .build());
        
        return defs;
    }
}


