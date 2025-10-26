package com.minitb.datasource.prometheus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Prometheus 查询结果
 * 
 * 对应 Prometheus API 返回的单个时间序列数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusQueryResult {
    
    /**
     * 指标标签
     * 例如: {"instance": "server-01:9100", "job": "node", "__name__": "node_cpu_seconds_total"}
     */
    private Map<String, String> metric;
    
    /**
     * 时间戳（Unix 秒）
     */
    private long timestamp;
    
    /**
     * 数值
     */
    private double value;
    
    /**
     * 根据标签键获取标签值
     */
    public String getLabel(String labelKey) {
        return metric != null ? metric.get(labelKey) : null;
    }
    
    /**
     * 检查是否包含指定的标签键值对
     */
    public boolean matchesLabel(String labelKey, String labelValue) {
        String actualValue = getLabel(labelKey);
        return actualValue != null && actualValue.equals(labelValue);
    }
}

