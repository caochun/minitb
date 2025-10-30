package com.minitb.infrastructure.web.dto.alarm;

import com.minitb.domain.alarm.Alarm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警通知 DTO
 * 
 * 用于 SSE 推送的轻量级告警数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmNotificationDto {
    
    /**
     * 告警ID
     */
    private String id;
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 告警类型
     */
    private String type;
    
    /**
     * 告警级别 (WARNING, MINOR, MAJOR, CRITICAL)
     */
    private String severity;
    
    /**
     * 告警状态 (ACTIVE_UNACK, ACTIVE_ACK, CLEARED_UNACK, CLEARED_ACK)
     */
    private String status;
    
    /**
     * 开始时间戳
     */
    private long startTs;
    
    /**
     * 通知类型 (created, updated, cleared, repeat)
     */
    private String action;
    
    /**
     * 通知次数（用于重复提醒）
     */
    private int notificationCount;
    
    /**
     * 从 Alarm 实体创建通知 DTO
     */
    public static AlarmNotificationDto fromAlarm(Alarm alarm, String action) {
        return AlarmNotificationDto.builder()
            .id(alarm.getId().toString())
            .deviceId(alarm.getOriginator().toString())
            .deviceName(alarm.getOriginatorName())
            .type(alarm.getType())
            .severity(alarm.getSeverity().name())
            .status(alarm.getStatus().name())
            .startTs(alarm.getStartTs())
            .action(action)
            .notificationCount(alarm.getNotificationCount())
            .build();
    }
}

