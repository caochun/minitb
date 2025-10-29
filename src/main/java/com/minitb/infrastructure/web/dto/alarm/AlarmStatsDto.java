package com.minitb.infrastructure.web.dto.alarm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警统计 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmStatsDto {
    
    /**
     * 总告警数
     */
    private long total;
    
    /**
     * 活动告警数
     */
    private long active;
    
    /**
     * 未确认告警数
     */
    private long unacknowledged;
    
    /**
     * 已清除告警数
     */
    private long cleared;
    
    /**
     * CRITICAL 级别告警数
     */
    private long critical;
    
    /**
     * MAJOR 级别告警数
     */
    private long major;
    
    /**
     * MINOR 级别告警数
     */
    private long minor;
    
    /**
     * WARNING 级别告警数
     */
    private long warning;
}


