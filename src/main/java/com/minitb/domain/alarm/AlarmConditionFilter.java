package com.minitb.domain.alarm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警条件过滤器
 * 
 * 用于定义单个条件判断：key operator value
 * 例如: "gpu_temperature" > 85.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmConditionFilter {
    
    /**
     * 遥测数据的键名
     * 例如: "gpu_temperature", "power_usage", "memory_used"
     */
    private String key;
    
    /**
     * 比较操作符
     */
    private FilterOperator operator;
    
    /**
     * 比较值
     * 支持数值、字符串、布尔值
     */
    private Object value;
    
    /**
     * 创建大于条件
     */
    public static AlarmConditionFilter greaterThan(String key, double value) {
        return AlarmConditionFilter.builder()
            .key(key)
            .operator(FilterOperator.GREATER_THAN)
            .value(value)
            .build();
    }
    
    /**
     * 创建小于条件
     */
    public static AlarmConditionFilter lessThan(String key, double value) {
        return AlarmConditionFilter.builder()
            .key(key)
            .operator(FilterOperator.LESS_THAN)
            .value(value)
            .build();
    }
    
    /**
     * 创建等于条件
     */
    public static AlarmConditionFilter equal(String key, Object value) {
        return AlarmConditionFilter.builder()
            .key(key)
            .operator(FilterOperator.EQUAL)
            .value(value)
            .build();
    }
    
    @Override
    public String toString() {
        return String.format("%s %s %s", key, operator, value);
    }
}


