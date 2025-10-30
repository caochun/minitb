package com.minitb.domain.alarm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 告警条件
 * 
 * 定义触发或清除告警的条件
 * 多个过滤器之间是 AND 关系（所有条件必须同时满足）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmCondition {
    
    /**
     * 条件类型
     */
    @Builder.Default
    private AlarmConditionType type = AlarmConditionType.SIMPLE;
    
    /**
     * 条件过滤器列表
     * 所有过滤器必须同时满足（AND 关系）
     */
    @Builder.Default
    private List<AlarmConditionFilter> filters = new ArrayList<>();
    
    /**
     * 条件规格（用于 DURATION 和 REPEATING 类型）
     */
    private AlarmConditionSpec spec;
    
    /**
     * 添加过滤器
     */
    public void addFilter(AlarmConditionFilter filter) {
        if (this.filters == null) {
            this.filters = new ArrayList<>();
        }
        this.filters.add(filter);
    }
    
    /**
     * 创建简单条件
     */
    public static AlarmCondition simple(AlarmConditionFilter... filters) {
        return AlarmCondition.builder()
            .type(AlarmConditionType.SIMPLE)
            .filters(Arrays.asList(filters))
            .build();
    }
    
    /**
     * 创建持续条件
     */
    public static AlarmCondition duration(int seconds, AlarmConditionFilter... filters) {
        return AlarmCondition.builder()
            .type(AlarmConditionType.DURATION)
            .spec(AlarmConditionSpec.duration(seconds))
            .filters(Arrays.asList(filters))
            .build();
    }
    
    /**
     * 创建重复条件
     */
    public static AlarmCondition repeating(int count, AlarmConditionFilter... filters) {
        return AlarmCondition.builder()
            .type(AlarmConditionType.REPEATING)
            .spec(AlarmConditionSpec.repeating(count))
            .filters(Arrays.asList(filters))
            .build();
    }
    
    @Override
    public String toString() {
        return String.format("AlarmCondition[type=%s, filters=%d]", type, filters.size());
    }
}



