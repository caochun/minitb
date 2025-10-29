package com.minitb.domain.alarm;

/**
 * 告警严重程度
 * 
 * 参考 ThingsBoard 的设计，按严重程度从高到低排序
 */
public enum AlarmSeverity {
    /**
     * 致命 - 需要立即处理的严重问题
     */
    CRITICAL,
    
    /**
     * 重要 - 需要尽快处理的重要问题
     */
    MAJOR,
    
    /**
     * 次要 - 需要关注但不紧急的问题
     */
    MINOR,
    
    /**
     * 警告 - 潜在问题的警告
     */
    WARNING,
    
    /**
     * 不确定 - 无法确定严重程度
     */
    INDETERMINATE;
    
    /**
     * 获取严重程度的数值级别（用于比较）
     */
    public int getLevel() {
        return ordinal();
    }
    
    /**
     * 比较严重程度
     */
    public boolean isMoreSevereThan(AlarmSeverity other) {
        return this.getLevel() < other.getLevel();
    }
}
