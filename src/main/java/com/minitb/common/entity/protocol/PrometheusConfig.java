package com.minitb.common.entity.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prometheus 协议配置
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusConfig implements ProtocolConfig {
    
    /**
     * PromQL 查询表达式
     * 支持复杂查询，不只是简单的指标名
     * 
     * 示例:
     * 1. 简单指标: "process_cpu_seconds_total"
     * 2. 速率计算: "rate(http_requests_total[5m])"
     * 3. 聚合查询: "sum(rate(cpu_usage[1m])) by (instance)"
     * 4. 带标签过滤: "temperature{instance=\"sensor-01\"}"
     */
    private String promQL;
    
    /**
     * 结果标签提取（可选）
     * 当 PromQL 返回多个时间序列时，用哪个标签来区分
     * 例如: "instance" 表示使用 instance 标签的值
     */
    private String resultLabel;
    
    /**
     * 值提取表达式（可选）
     * 对于复杂的返回结果，如何提取数值
     * 默认取第一个结果的值
     */
    private String valueExtractor;
    
    /**
     * 是否需要计算（例如从累计值转换为速率）
     */
    @Builder.Default
    private boolean needsRateCalculation = false;
    
    /**
     * 速率计算时间窗口（秒）
     */
    @Builder.Default
    private int rateWindow = 60;
    
    @Override
    public String getProtocolType() {
        return "PROMETHEUS";
    }
}

