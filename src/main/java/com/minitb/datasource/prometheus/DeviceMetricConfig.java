package com.minitb.datasource.prometheus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备指标配置
 * 用于配置从Prometheus拉取哪个设备的哪些指标
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMetricConfig {
    /**
     * 设备ID（用于在Prometheus中标识设备）
     */
    private String deviceId;
    
    /**
     * 设备访问令牌（用于MiniTB中的设备认证）
     */
    private String accessToken;
    
    /**
     * 需要拉取的指标名称列表
     * 例如: ["temperature", "humidity", "pressure"]
     */
    private List<String> metrics;
    
    /**
     * 指标名到 PromQL 查询的映射
     * 例如: {"cpu_rate": "rate(process_cpu_seconds_total[1m])"}
     */
    private Map<String, String> promQLMap = new HashMap<>();
    
    public DeviceMetricConfig(String deviceId, String accessToken, List<String> metrics) {
        this.deviceId = deviceId;
        this.accessToken = accessToken;
        this.metrics = metrics;
        this.promQLMap = new HashMap<>();
    }
}



