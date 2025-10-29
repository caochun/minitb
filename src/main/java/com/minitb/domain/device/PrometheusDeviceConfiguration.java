package com.minitb.domain.device;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prometheus 设备配置
 * 
 * 存储连接到具体 Prometheus 数据源所需的信息：
 * - endpoint: Prometheus 服务器地址（每个设备可能连接不同的 Prometheus）
 * - label: 标签过滤器，用于从 Prometheus 查询结果中识别此设备的数据
 * 
 * 示例：
 * <pre>
 * PrometheusDeviceConfiguration config = PrometheusDeviceConfiguration.builder()
 *     .endpoint("http://192.168.30.134:9090")
 *     .label("gpu=0")  // 从结果中过滤 gpu="0" 的数据
 *     .build();
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PrometheusDeviceConfiguration implements DeviceConfiguration {
    
    /**
     * Prometheus 服务器地址
     * 例如: "http://192.168.30.134:9090"
     */
    private String endpoint;
    
    /**
     * 标签过滤器（格式: key=value）
     * 用于从 Prometheus 查询结果中识别此设备的数据
     * 
     * 例如:
     * - "gpu=0" : 匹配 gpu="0" 的指标
     * - "instance=192.168.1.100:9100" : 匹配特定 instance
     * - "job=node-exporter,instance=server-1" : 多个标签
     */
    private String label;
    
    @Override
    @JsonIgnore  // 不序列化此方法，避免与 @JsonTypeInfo 的 type 字段冲突
    public String getConfigurationType() {
        return "PROMETHEUS";
    }
}

