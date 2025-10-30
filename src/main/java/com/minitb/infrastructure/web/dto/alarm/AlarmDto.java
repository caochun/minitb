package com.minitb.infrastructure.web.dto.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.minitb.domain.alarm.Alarm;
import com.minitb.domain.alarm.AlarmSeverity;
import com.minitb.domain.alarm.AlarmStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmDto {
    
    private String id;
    private String deviceId;
    private String deviceName;
    private String type;
    private AlarmSeverity severity;
    private AlarmStatus status;
    private Long startTs;
    private Long endTs;
    private Long ackTs;
    private Long clearTs;
    private JsonNode details;
    private Long createdTime;
    private Long durationSeconds;
    
    /**
     * 从领域对象转换
     */
    public static AlarmDto fromDomain(Alarm alarm) {
        return AlarmDto.builder()
            .id(alarm.getId().toString())
            .deviceId(alarm.getOriginator().toString())
            .deviceName(alarm.getOriginatorName())
            .type(alarm.getType())
            .severity(alarm.getSeverity())
            .status(alarm.getStatus())
            .startTs(alarm.getStartTs())
            .endTs(alarm.getEndTs())
            .ackTs(alarm.getAckTs())
            .clearTs(alarm.getClearTs())
            .details(alarm.getDetails())
            .createdTime(alarm.getCreatedTime())
            .durationSeconds(alarm.getDurationSeconds())
            .build();
    }
}



