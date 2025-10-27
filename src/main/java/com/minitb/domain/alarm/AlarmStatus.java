package com.minitb.domain.alarm;

/**
 * 告警状态
 * 
 * 由 cleared 和 acknowledged 两个标志位计算得出
 */
public enum AlarmStatus {
    
    /**
     * 活跃且未确认
     */
    ACTIVE_UNACK,
    
    /**
     * 活跃且已确认
     */
    ACTIVE_ACK,
    
    /**
     * 已清除但未确认
     */
    CLEARED_UNACK,
    
    /**
     * 已清除且已确认
     */
    CLEARED_ACK;
    
    /**
     * 根据 cleared 和 acknowledged 标志计算状态
     */
    public static AlarmStatus from(boolean cleared, boolean acknowledged) {
        if (cleared) {
            return acknowledged ? CLEARED_ACK : CLEARED_UNACK;
        } else {
            return acknowledged ? ACTIVE_ACK : ACTIVE_UNACK;
        }
    }
}


