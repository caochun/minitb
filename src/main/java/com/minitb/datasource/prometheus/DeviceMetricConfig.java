package com.minitb.datasource.prometheus;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 设备指标配置
 * 用于配置从Prometheus拉取哪个设备的哪些指标
 */
@Data
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
}



