package com.minitb.domain.alarm;

/**
 * 告警状态
 * 
 * 状态转换:
 * ACTIVE_UNACK (活动未确认)
 *   ├─→ ACTIVE_ACK (确认) → CLEARED_ACK (清除)
 *   └─→ CLEARED_UNACK (直接清除)
 */
public enum AlarmStatus {
    /**
     * 活动-未确认
     * 告警条件满足，尚未被用户确认
     */
    ACTIVE_UNACK,
    
    /**
     * 活动-已确认
     * 告警条件满足，已被用户确认
     */
    ACTIVE_ACK,
    
    /**
     * 已清除-未确认
     * 告警条件不再满足，但尚未被确认清除
     */
    CLEARED_UNACK,
    
    /**
     * 已清除-已确认
     * 告警条件不再满足，已被确认清除
     */
    CLEARED_ACK;
    
    /**
     * 是否为活动状态
     */
    public boolean isActive() {
        return this == ACTIVE_UNACK || this == ACTIVE_ACK;
    }
    
    /**
     * 是否已清除
     */
    public boolean isCleared() {
        return this == CLEARED_UNACK || this == CLEARED_ACK;
    }
    
    /**
     * 是否已确认
     */
    public boolean isAcknowledged() {
        return this == ACTIVE_ACK || this == CLEARED_ACK;
    }
    
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
