package com.minitb.domain.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.minitb.domain.telemetry.DataType;
import com.minitb.domain.protocol.HttpConfig;
import com.minitb.domain.protocol.MqttConfig;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.protocol.ProtocolConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 遥测数据定义
 * 定义单个遥测数据项的配置
 * 
 * 设计模式：组合模式 + 接口多态
 * - 通用字段：适用于所有协议
 * - protocolConfig：协议特定配置（使用接口实现多态）
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryDefinition {
    
    /**
     * 遥测键名
     * 例如: "temperature", "cpu_usage", "http_requests"
     */
    private String key;
    
    /**
     * 显示名称（可选）
     * 例如: "温度", "CPU使用率", "HTTP请求数"
     */
    private String displayName;
    
    /**
     * 数据类型（可选，用于验证）
     */
    private DataType dataType;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 单位（可选）
     * 例如: "°C", "%", "requests/s"
     */
    private String unit;
    
    /**
     * 协议特定配置（使用接口多态）
     * 可以是：
     * - PrometheusConfig: Prometheus 拉取配置
     * - MqttConfig: MQTT 推送配置
     * - HttpConfig: HTTP 推送配置
     * - 或 null: 使用默认配置
     */
    private ProtocolConfig protocolConfig;
    
    // ==================== 便捷方法 ====================
    
    /**
     * 判断是否是 Prometheus 协议
     */
    public boolean isPrometheus() {
        return protocolConfig instanceof PrometheusConfig;
    }
    
    /**
     * 获取 Prometheus 配置（类型安全）
     */
    public PrometheusConfig getPrometheusConfig() {
        return isPrometheus() ? (PrometheusConfig) protocolConfig : null;
    }
    
    /**
     * 判断是否是 MQTT 协议
     */
    public boolean isMqtt() {
        return protocolConfig instanceof MqttConfig;
    }
    
    /**
     * 获取 MQTT 配置（类型安全）
     */
    public MqttConfig getMqttConfig() {
        return isMqtt() ? (MqttConfig) protocolConfig : null;
    }
    
    /**
     * 判断是否是 HTTP 协议
     */
    public boolean isHttp() {
        return protocolConfig instanceof HttpConfig;
    }
    
    /**
     * 获取 HTTP 配置（类型安全）
     */
    public HttpConfig getHttpConfig() {
        return isHttp() ? (HttpConfig) protocolConfig : null;
    }
    
    /**
     * 获取协议类型名称
     */
    public String getProtocolType() {
        return protocolConfig != null ? protocolConfig.getProtocolType() : "DEFAULT";
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 创建简单的遥测定义（无协议特定配置）
     */
    public static TelemetryDefinition simple(String key, DataType dataType) {
        return TelemetryDefinition.builder()
                .key(key)
                .dataType(dataType)
                .build();
    }
    
    /**
     * 创建 MQTT 遥测定义
     */
    public static TelemetryDefinition mqtt(String key, DataType dataType) {
        return TelemetryDefinition.builder()
                .key(key)
                .dataType(dataType)
                .protocolConfig(MqttConfig.builder().build())
                .build();
    }
    
    /**
     * 创建简单的 Prometheus 遥测定义
     */
    public static TelemetryDefinition prometheus(String key, String promQL) {
        return TelemetryDefinition.builder()
                .key(key)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL(promQL)
                        .build())
                .build();
    }
    
    /**
     * 创建带速率计算的 Prometheus 遥测定义
     */
    public static TelemetryDefinition prometheusRate(String key, String metricName, int windowSeconds) {
        String promQL = String.format("rate(%s[%ds])", metricName, windowSeconds);
        return TelemetryDefinition.builder()
                .key(key)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL(promQL)
                        .needsRateCalculation(true)
                        .rateWindow(windowSeconds)
                        .build())
                .build();
    }
}
