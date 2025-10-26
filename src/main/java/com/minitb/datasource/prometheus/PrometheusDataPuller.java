package com.minitb.datasource.prometheus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.transport.service.TransportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prometheus 数据拉取器
 * 
 * 职责：
 * 1. 定时从 Prometheus 拉取指标数据
 * 2. 根据标签映射将数据关联到具体设备
 * 3. 通过 TransportService.processTelemetry() 统一入口处理数据
 * 
 * 设计原理：
 * - Prometheus 是 Pull 模式，没有设备主动连接
 * - 通过 Device.prometheusLabel 字段建立标签映射
 * - 使用 Device.accessToken 作为内部标识符
 * - 复用 TransportService 的统一数据处理流程
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrometheusDataPuller {
    
    private final DeviceService deviceService;
    private final TransportService transportService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    
    /**
     * 定时拉取所有 Prometheus 设备的数据
     * 每 30 秒执行一次
     */
    @Scheduled(fixedRate = 30000, initialDelay = 5000)
    public void pullAllPrometheusDevices() {
        try {
            // 1. 获取所有 Prometheus 类型的设备
            List<Device> prometheusDevices = deviceService.findAll().stream()
                    .filter(this::isPrometheusDevice)
                    .collect(Collectors.toList());
            
            if (prometheusDevices.isEmpty()) {
                log.debug("没有 Prometheus 设备需要拉取数据");
                return;
            }
            
            log.info("📊 开始拉取 {} 个 Prometheus 设备的数据", prometheusDevices.size());
            
            // 2. 对每个设备拉取数据
            int successCount = 0;
            int failCount = 0;
            
            for (Device device : prometheusDevices) {
                try {
                    pullDeviceMetrics(device);
                    successCount++;
                } catch (Exception e) {
                    log.error("拉取设备 {} 的数据失败", device.getName(), e);
                    failCount++;
                }
            }
            
            log.info("✅ Prometheus 数据拉取完成: 成功 {}, 失败 {}", successCount, failCount);
            
        } catch (Exception e) {
            log.error("Prometheus 数据拉取整体失败", e);
        }
    }
    
    /**
     * 拉取单个设备的指标数据
     */
    private void pullDeviceMetrics(Device device) throws Exception {
        log.debug("拉取设备 {} 的 Prometheus 指标", device.getName());
        
        // 1. 获取设备的 DeviceProfile
        DeviceProfile profile = deviceService.findProfileById(device.getDeviceProfileId())
                .orElseThrow(() -> new IllegalStateException(
                    "设备 " + device.getName() + " 的 DeviceProfile 不存在"));
        
        // 2. 检查 Prometheus 配置
        if (profile.getPrometheusEndpoint() == null || profile.getPrometheusEndpoint().isEmpty()) {
            log.warn("设备 {} 的 DeviceProfile 未配置 Prometheus 端点", device.getName());
            return;
        }
        
        if (device.getPrometheusLabel() == null || device.getPrometheusLabel().isEmpty()) {
            log.warn("设备 {} 未配置 Prometheus 标签映射", device.getName());
            return;
        }
        
        // 3. 解析设备的 Prometheus 标签
        // prometheusLabel 格式: "instance=server-01:9100"
        String[] labelParts = device.getPrometheusLabel().split("=", 2);
        if (labelParts.length != 2) {
            log.error("设备 {} 的 prometheusLabel 格式错误: {}", 
                device.getName(), device.getPrometheusLabel());
            return;
        }
        
        String labelKey = labelParts[0].trim();    // "instance"
        String labelValue = labelParts[1].trim();  // "server-01:9100"
        
        // 4. 遍历所有遥测定义，拉取数据
        Map<String, Object> telemetryData = new HashMap<>();
        
        for (TelemetryDefinition telemetryDef : profile.getTelemetryDefinitions()) {
            if (!telemetryDef.isPrometheus()) {
                continue;
            }
            
            PrometheusConfig config = telemetryDef.getPrometheusConfig();
            String promQL = config.getPromQL();
            
            try {
                // 5. 查询 Prometheus
                List<PrometheusQueryResult> results = queryPrometheus(
                    profile.getPrometheusEndpoint(), 
                    promQL
                );
                
                // 6. 根据标签过滤出属于当前设备的数据
                Optional<PrometheusQueryResult> deviceData = results.stream()
                        .filter(result -> result.matchesLabel(labelKey, labelValue))
                        .findFirst();
                
                if (deviceData.isPresent()) {
                    telemetryData.put(telemetryDef.getKey(), deviceData.get().getValue());
                    log.debug("  ✓ {} = {}", telemetryDef.getKey(), deviceData.get().getValue());
                } else {
                    log.debug("  ✗ {} - 未找到匹配标签 {}={} 的数据", 
                        telemetryDef.getKey(), labelKey, labelValue);
                }
                
            } catch (Exception e) {
                log.error("查询 Prometheus 指标 {} 失败: {}", telemetryDef.getKey(), promQL, e);
            }
        }
        
        // 7. 如果有数据，通过 TransportService 统一处理
        if (!telemetryData.isEmpty()) {
            String telemetryJson = gson.toJson(telemetryData);
            
            log.debug("📤 设备 {} 拉取到 {} 个指标，调用 processTelemetry", 
                device.getName(), telemetryData.size());
            
            // ✅ 关键：使用 Device 的 AccessToken 调用 processTelemetry
            // 这样数据就能通过统一流程关联到设备
            transportService.processTelemetry(
                device.getAccessToken(),  // ← 通过 Token 关联设备！
                telemetryJson
            );
        } else {
            log.debug("设备 {} 没有拉取到任何数据", device.getName());
        }
    }
    
    /**
     * 查询 Prometheus
     * 
     * @param prometheusEndpoint Prometheus URL，例如 "http://localhost:9090"
     * @param promQL PromQL 查询表达式
     * @return 查询结果列表
     */
    private List<PrometheusQueryResult> queryPrometheus(String prometheusEndpoint, String promQL) 
            throws IOException, InterruptedException {
        
        // 构建 Prometheus API URL
        // API: GET /api/v1/query?query=<promQL>
        String url = prometheusEndpoint + "/api/v1/query?query=" + 
                     java.net.URLEncoder.encode(promQL, "UTF-8");
        
        log.debug("查询 Prometheus: {}", promQL);
        
        // 发送 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Prometheus 查询失败: HTTP " + response.statusCode());
        }
        
        // 解析响应
        return parsePrometheusResponse(response.body());
    }
    
    /**
     * 解析 Prometheus API 响应
     * 
     * 响应格式:
     * {
     *   "status": "success",
     *   "data": {
     *     "resultType": "vector",
     *     "result": [
     *       {
     *         "metric": {"instance": "server-01:9100", "job": "node"},
     *         "value": [timestamp, "45.2"]
     *       }
     *     ]
     *   }
     * }
     */
    private List<PrometheusQueryResult> parsePrometheusResponse(String responseBody) {
        List<PrometheusQueryResult> results = new ArrayList<>();
        
        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            // 检查状态
            String status = json.get("status").getAsString();
            if (!"success".equals(status)) {
                log.error("Prometheus 查询返回非成功状态: {}", status);
                return results;
            }
            
            // 解析结果
            JsonObject data = json.getAsJsonObject("data");
            JsonArray resultArray = data.getAsJsonArray("result");
            
            for (JsonElement element : resultArray) {
                JsonObject resultItem = element.getAsJsonObject();
                
                // 解析 metric 标签
                Map<String, String> metric = new HashMap<>();
                JsonObject metricObj = resultItem.getAsJsonObject("metric");
                for (String key : metricObj.keySet()) {
                    metric.put(key, metricObj.get(key).getAsString());
                }
                
                // 解析 value [timestamp, "value"]
                JsonArray valueArray = resultItem.getAsJsonArray("value");
                long timestamp = valueArray.get(0).getAsLong();
                double value = Double.parseDouble(valueArray.get(1).getAsString());
                
                results.add(PrometheusQueryResult.builder()
                        .metric(metric)
                        .timestamp(timestamp)
                        .value(value)
                        .build());
            }
            
            log.debug("解析 Prometheus 响应: {} 个时间序列", results.size());
            
        } catch (Exception e) {
            log.error("解析 Prometheus 响应失败", e);
        }
        
        return results;
    }
    
    /**
     * 判断设备是否是 Prometheus 类型
     */
    private boolean isPrometheusDevice(Device device) {
        if (device.getDeviceProfileId() == null) {
            return false;
        }
        
        Optional<DeviceProfile> profileOpt = deviceService.findProfileById(device.getDeviceProfileId());
        return profileOpt.isPresent() && 
               DeviceProfile.DataSourceType.PROMETHEUS == profileOpt.get().getDataSourceType();
    }
}
