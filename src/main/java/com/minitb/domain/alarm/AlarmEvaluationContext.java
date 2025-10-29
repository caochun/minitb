package com.minitb.domain.alarm;

import lombok.Data;

/**
 * 告警评估上下文
 * 
 * 用于维护 DURATION 和 REPEATING 类型条件的状态
 * 每个规则+设备组合维护一个上下文
 */
@Data
public class AlarmEvaluationContext {
    
    /**
     * 规则ID
     */
    private final String ruleId;
    
    /**
     * 首次匹配时间（用于 DURATION）
     */
    private Long firstMatchTs;
    
    /**
     * 连续匹配次数（用于 REPEATING）
     */
    private int matchCount;
    
    public AlarmEvaluationContext(String ruleId) {
        this.ruleId = ruleId;
        this.matchCount = 0;
    }
    
    /**
     * 增加匹配次数
     */
    public void incrementMatchCount() {
        this.matchCount++;
    }
    
    /**
     * 重置匹配次数
     */
    public void resetMatchCount() {
        this.matchCount = 0;
    }
    
    /**
     * 重置所有状态
     */
    public void reset() {
        this.firstMatchTs = null;
        this.matchCount = 0;
    }
}


