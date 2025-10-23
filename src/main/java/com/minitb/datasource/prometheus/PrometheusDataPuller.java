package com.minitb.datasource.prometheus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minitb.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Prometheus数据拉取器
 * 职责：
 * 1. 定时从Prometheus查询设备数据
 * 2. 将Prometheus格式转为JSON
 * 3. 模拟MQTT消息，注入到TransportService
 */
@Slf4j
public class PrometheusDataPuller {
    
    private final String prometheusUrl;
    private final TransportService transportService;
    private final ScheduledExecutorService scheduler;
    private final Map<String, DeviceMetricConfig> deviceConfigs;
    private final HttpClient httpClient;
    
    public PrometheusDataPuller(String prometheusUrl, 
                                TransportService transportService) {
        this.prometheusUrl = prometheusUrl;
        this.transportService = transportService;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.deviceConfigs = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        log.info("Prometheus数据拉取器初始化完成，目标: {}", prometheusUrl);
    }
    
    /**
     * 注册需要拉取的设备配置
     * @param deviceId 设备ID
     * @param accessToken 设备token（用于认证）
     * @param metrics 需要拉取的指标列表，如["temperature", "humidity"]
     */
    public void registerDevice(String deviceId, String accessToken, 
                               List<String> metrics) {
        DeviceMetricConfig config = new DeviceMetricConfig(
            deviceId, accessToken, metrics
        );
        deviceConfigs.put(deviceId, config);
        log.info("注册Prometheus数据源设备: deviceId={}, token={}, 指标={}", 
                 deviceId, accessToken, metrics);
    }
    
    /**
     * 启动定时拉取任务
     * @param intervalSeconds 拉取间隔（秒）
     */
    public void start(int intervalSeconds) {
        log.info("启动Prometheus数据拉取任务，间隔: {}秒", intervalSeconds);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                pullAndInject();
            } catch (Exception e) {
                log.error("拉取Prometheus数据失败", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 核心方法：拉取数据并注入到MiniTB数据流
     */
    private void pullAndInject() {
        log.debug("开始拉取Prometheus数据，设备数量: {}", deviceConfigs.size());
        
        for (DeviceMetricConfig config : deviceConfigs.values()) {
            try {
                // 1. 查询Prometheus获取最新数据
                Map<String, Double> latestData = queryPrometheus(config);
                
                if (latestData.isEmpty()) {
                    log.warn("设备 {} 无最新数据", config.getDeviceId());
                    continue;
                }
                
                // 2. 转换为JSON格式
                String telemetryJson = convertToJson(latestData);
                
                log.info("从Prometheus拉取到设备数据: deviceId={}, data={}", 
                         config.getDeviceId(), telemetryJson);
                
                // 3. 注入到TransportService（模拟MQTT上报）
                transportService.processTelemetry(
                    config.getAccessToken(), 
                    telemetryJson
                );
                
            } catch (Exception e) {
                log.error("处理设备 {} 数据失败", config.getDeviceId(), e);
            }
        }
    }
    
    /**
     * 查询Prometheus获取设备最新指标
     */
    private Map<String, Double> queryPrometheus(DeviceMetricConfig config) 
            throws Exception {
        Map<String, Double> result = new HashMap<>();
        
        for (String metricName : config.getMetrics()) {
            // 构造PromQL查询（查询最新值）
            // 例如: device_temperature{device_id="xxx"}
            String promQL = String.format(
                "%s{device_id=\"%s\"}", 
                metricName, 
                config.getDeviceId()
            );
            
            // 调用Prometheus HTTP API
            String url = String.format(
                "%s/api/v1/query?query=%s",
                prometheusUrl,
                URLEncoder.encode(promQL, StandardCharsets.UTF_8)
            );
            
            log.debug("查询Prometheus: {}", url);
            
            String response = httpGet(url);
            
            // 解析响应获取数值
            Double value = parsePrometheusResponse(response, metricName);
            if (value != null) {
                result.put(metricName, value);
            } else {
                log.debug("指标 {} 无数据", metricName);
            }
        }
        
        return result;
    }
    
    /**
     * HTTP GET请求
     */
    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP请求失败: " + response.statusCode());
        }
        
        return response.body();
    }
    
    /**
     * 解析Prometheus响应
     * 响应格式:
     * {
     *   "status": "success",
     *   "data": {
     *     "resultType": "vector",
     *     "result": [{
     *       "metric": {"device_id": "xxx", "__name__": "temperature"},
     *       "value": [timestamp, "25.0"]
     *     }]
     *   }
     * }
     */
    private Double parsePrometheusResponse(String jsonResponse, String metricName) {
        try {
            JsonObject json = JsonParser.parseString(jsonResponse)
                .getAsJsonObject();
            
            // 检查状态
            String status = json.get("status").getAsString();
            if (!"success".equals(status)) {
                log.warn("Prometheus查询失败: status={}", status);
                return null;
            }
            
            // 获取结果数组
            JsonArray results = json.getAsJsonObject("data")
                .getAsJsonArray("result");
            
            if (results.size() > 0) {
                // 获取第一个结果的值
                JsonArray value = results.get(0).getAsJsonObject()
                    .getAsJsonArray("value");
                // value[0]是时间戳, value[1]是数值字符串
                String valueStr = value.get(1).getAsString();
                return Double.parseDouble(valueStr);
            } else {
                log.debug("指标 {} 查询结果为空", metricName);
            }
        } catch (Exception e) {
            log.error("解析Prometheus响应失败: metricName={}, response={}", 
                     metricName, jsonResponse, e);
        }
        return null;
    }
    
    /**
     * 转换为ThingsBoard JSON格式
     */
    private String convertToJson(Map<String, Double> data) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue());
        }
        return json.toString();
    }
    
    /**
     * 关闭拉取器
     */
    public void shutdown() {
        log.info("关闭Prometheus数据拉取器");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
    
    /**
     * 获取已注册设备数量
     */
    public int getRegisteredDeviceCount() {
        return deviceConfigs.size();
    }
}

