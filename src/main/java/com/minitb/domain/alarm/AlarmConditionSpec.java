package com.minitb.domain.alarm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警条件规格
 * 
 * 用于 DURATION 和 REPEATING 类型的额外参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmConditionSpec {
    
    /**
     * 持续时间（秒）
     * 用于 DURATION 类型
     */
    private Integer durationSeconds;
    
    /**
     * 重复次数
     * 用于 REPEATING 类型
     */
    private Integer repeatingCount;
    
    /**
     * 创建持续时间条件规格
     */
    public static AlarmConditionSpec duration(int seconds) {
        return AlarmConditionSpec.builder()
            .durationSeconds(seconds)
            .build();
    }
    
    /**
     * 创建重复次数条件规格
     */
    public static AlarmConditionSpec repeating(int count) {
        return AlarmConditionSpec.builder()
            .repeatingCount(count)
            .build();
    }
}



