package com.minitb.infrastructure.web.controller;

import com.minitb.application.service.alarm.AlarmService;
import com.minitb.domain.alarm.Alarm;
import com.minitb.domain.alarm.AlarmSeverity;
import com.minitb.domain.alarm.AlarmStatus;
import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.DeviceId;
import com.minitb.infrastructure.web.dto.alarm.AlarmDto;
import com.minitb.infrastructure.web.dto.alarm.AlarmStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警 REST API 控制器
 */
@RestController
@RequestMapping("/api/alarms")
@Slf4j
public class AlarmController {
    
    private final AlarmService alarmService;
    
    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }
    
    /**
     * 获取所有活动告警
     */
    @GetMapping("/active")
    public List<AlarmDto> getActiveAlarms() {
        return alarmService.findAllActive().stream()
            .map(AlarmDto::fromDomain)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有未确认告警
     */
    @GetMapping("/unacknowledged")
    public List<AlarmDto> getUnacknowledgedAlarms() {
        return alarmService.findAllUnacknowledged().stream()
            .map(AlarmDto::fromDomain)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据设备ID获取告警
     * 
     * @param deviceId 设备ID
     * @param status 可选的状态过滤
     */
    @GetMapping("/device/{deviceId}")
    public List<AlarmDto> getDeviceAlarms(
            @PathVariable String deviceId,
            @RequestParam(required = false) AlarmStatus status) {
        
        DeviceId id = DeviceId.fromString(deviceId);
        List<Alarm> alarms;
        
        if (status != null) {
            alarms = alarmService.findByDeviceAndStatus(id, status);
        } else {
            alarms = alarmService.findByDevice(id);
        }
        
        return alarms.stream()
            .map(AlarmDto::fromDomain)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取告警详情
     */
    @GetMapping("/{alarmId}")
    public ResponseEntity<AlarmDto> getAlarm(@PathVariable String alarmId) {
        return alarmService.findById(AlarmId.fromString(alarmId))
            .map(alarm -> ResponseEntity.ok(AlarmDto.fromDomain(alarm)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 确认告警
     */
    @PostMapping("/{alarmId}/ack")
    public AlarmDto acknowledgeAlarm(@PathVariable String alarmId) {
        Alarm alarm = alarmService.acknowledgeAlarm(AlarmId.fromString(alarmId));
        return AlarmDto.fromDomain(alarm);
    }
    
    /**
     * 清除告警
     */
    @PostMapping("/{alarmId}/clear")
    public AlarmDto clearAlarm(@PathVariable String alarmId) {
        Alarm alarm = alarmService.clearAlarm(AlarmId.fromString(alarmId));
        return AlarmDto.fromDomain(alarm);
    }
    
    /**
     * 删除告警
     */
    @DeleteMapping("/{alarmId}")
    public ResponseEntity<Void> deleteAlarm(@PathVariable String alarmId) {
        alarmService.deleteAlarm(AlarmId.fromString(alarmId));
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取告警统计信息
     */
    @GetMapping("/stats")
    public AlarmStatsDto getAlarmStats() {
        List<Alarm> allAlarms = alarmService.findAllActive();
        
        long total = allAlarms.size();
        long unacknowledged = alarmService.countByStatus(AlarmStatus.ACTIVE_UNACK) + 
                              alarmService.countByStatus(AlarmStatus.CLEARED_UNACK);
        long cleared = alarmService.countByStatus(AlarmStatus.CLEARED_ACK) + 
                       alarmService.countByStatus(AlarmStatus.CLEARED_UNACK);
        
        // 按严重程度统计
        long critical = allAlarms.stream()
            .filter(a -> a.getSeverity() == AlarmSeverity.CRITICAL)
            .count();
        long major = allAlarms.stream()
            .filter(a -> a.getSeverity() == AlarmSeverity.MAJOR)
            .count();
        long minor = allAlarms.stream()
            .filter(a -> a.getSeverity() == AlarmSeverity.MINOR)
            .count();
        long warning = allAlarms.stream()
            .filter(a -> a.getSeverity() == AlarmSeverity.WARNING)
            .count();
        
        return AlarmStatsDto.builder()
            .total(total)
            .active(total - cleared)
            .unacknowledged(unacknowledged)
            .cleared(cleared)
            .critical(critical)
            .major(major)
            .minor(minor)
            .warning(warning)
            .build();
    }
}


