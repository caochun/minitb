package com.minitb.domain.alarm;

/**
 * 告警条件类型
 */
public enum AlarmConditionType {
    /**
     * 简单条件 - 立即评估
     * 满足条件时立即触发告警
     */
    SIMPLE,
    
    /**
     * 持续条件 - 持续时间评估
     * 条件需要持续满足指定时间（秒）才触发告警
     * 用于避免短暂的数据抖动造成的误告警
     */
    DURATION,
    
    /**
     * 重复条件 - 重复次数评估
     * 条件需要连续满足指定次数才触发告警
     * 用于检测间歇性问题
     */
    REPEATING
}


