package com.minitb.application.service.alarm;

import com.minitb.domain.alarm.Alarm;
import com.minitb.domain.alarm.AlarmSeverity;
import com.minitb.domain.alarm.AlarmStatus;
import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.DeviceId;

import java.util.List;
import java.util.Optional;

/**
 * 告警服务接口
 * 
 * 定义告警的业务操作
 */
public interface AlarmService {
    
    /**
     * 创建告警
     * 
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @param alarmType 告警类型
     * @param severity 严重程度
     * @return 创建的告警
     */
    Alarm createAlarm(DeviceId deviceId, String deviceName, String alarmType, AlarmSeverity severity);
    
    /**
     * 创建或更新告警
     * 
     * 如果同类型的告警已存在且未清除，则更新严重程度；否则创建新告警
     * 
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @param alarmType 告警类型
     * @param severity 严重程度
     * @return 创建或更新的告警
     */
    Alarm createOrUpdateAlarm(DeviceId deviceId, String deviceName, String alarmType, AlarmSeverity severity);
    
    /**
     * 清除告警
     * 
     * @param alarmId 告警ID
     * @return 清除后的告警
     */
    Alarm clearAlarm(AlarmId alarmId);
    
    /**
     * 清除设备的指定类型告警
     * 
     * @param deviceId 设备ID
     * @param alarmType 告警类型
     * @return 清除的告警（如果存在）
     */
    Optional<Alarm> clearAlarmByType(DeviceId deviceId, String alarmType);
    
    /**
     * 确认告警
     * 
     * @param alarmId 告警ID
     * @return 确认后的告警
     */
    Alarm acknowledgeAlarm(AlarmId alarmId);
    
    /**
     * 根据 ID 查找告警
     */
    Optional<Alarm> findById(AlarmId id);
    
    /**
     * 根据设备和告警类型查找最新的告警
     */
    Optional<Alarm> findLatestByOriginatorAndType(DeviceId deviceId, String alarmType);
    
    /**
     * 查找设备的所有告警
     */
    List<Alarm> findByDevice(DeviceId deviceId);
    
    /**
     * 查找设备指定状态的告警
     */
    List<Alarm> findByDeviceAndStatus(DeviceId deviceId, AlarmStatus status);
    
    /**
     * 查找所有活动告警
     */
    List<Alarm> findAllActive();
    
    /**
     * 查找所有未确认告警
     */
    List<Alarm> findAllUnacknowledged();
    
    /**
     * 删除告警
     */
    void deleteAlarm(AlarmId id);
    
    /**
     * 统计设备的告警数量
     */
    long countByDevice(DeviceId deviceId);
    
    /**
     * 统计指定状态的告警数量
     */
    long countByStatus(AlarmStatus status);
}


