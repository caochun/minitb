package com.minitb.domain.alarm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 告警规则
 * 
 * 定义在 DeviceProfile 中，一个设备配置可以有多个告警规则
 * 每个规则定义：
 * 1. 告警类型（name）
 * 2. 不同严重程度的创建条件
 * 3. 清除条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {
    
    /**
     * 规则唯一标识
     */
    private String id;
    
    /**
     * 告警类型名称
     * 例如: "High Temperature", "High Power Usage", "Connection Lost"
     */
    private String alarmType;
    
    /**
     * 创建告警的条件（按严重程度）
     * 
     * 按严重程度从高到低评估，匹配到第一个满足的条件就停止
     * 例如:
     * - CRITICAL: temperature > 85
     * - MAJOR: temperature > 80
     * - WARNING: temperature > 75
     * 
     * 当 temperature = 86 时，匹配 CRITICAL，不会继续评估 MAJOR 和 WARNING
     */
    @Builder.Default
    private Map<AlarmSeverity, AlarmCondition> createConditions = new TreeMap<>(
        Comparator.comparingInt(AlarmSeverity::getLevel)
    );
    
    /**
     * 清除告警的条件
     * 
     * 当清除条件满足时，活动的告警会被标记为已清除
     * 例如: temperature < 75
     */
    private AlarmCondition clearCondition;
    
    /**
     * 添加创建条件
     */
    public void addCreateCondition(AlarmSeverity severity, AlarmCondition condition) {
        if (this.createConditions == null) {
            this.createConditions = new TreeMap<>(
                Comparator.comparingInt(AlarmSeverity::getLevel)
            );
        }
        this.createConditions.put(severity, condition);
    }
    
    /**
     * 获取按严重程度排序的创建条件（从高到低）
     */
    public List<Map.Entry<AlarmSeverity, AlarmCondition>> getSortedCreateConditions() {
        return new ArrayList<>(createConditions.entrySet());
    }
    
    
    @Override
    public String toString() {
        return String.format("AlarmRule[id=%s, type=%s, conditions=%d]", 
            id, alarmType, createConditions.size());
    }
}

