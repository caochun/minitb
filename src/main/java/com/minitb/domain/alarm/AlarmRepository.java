package com.minitb.domain.alarm;

import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.DeviceId;

import java.util.List;
import java.util.Optional;

/**
 * 告警仓储接口（Port）
 * 
 * 遵循六边形架构，领域层定义接口，基础设施层实现
 */
public interface AlarmRepository {
    
    /**
     * 保存告警
     */
    Alarm save(Alarm alarm);
    
    /**
     * 根据 ID 查找告警
     */
    Optional<Alarm> findById(AlarmId id);
    
    /**
     * 根据设备和告警类型查找最新的告警
     * 
     * @param deviceId 设备ID
     * @param alarmType 告警类型
     * @return 最新的告警（按 startTs 降序）
     */
    Optional<Alarm> findLatestByOriginatorAndType(DeviceId deviceId, String alarmType);
    
    /**
     * 查找设备的所有告警
     * 
     * @param deviceId 设备ID
     * @return 告警列表（按 startTs 降序）
     */
    List<Alarm> findByOriginator(DeviceId deviceId);
    
    /**
     * 查找设备指定状态的告警
     * 
     * @param deviceId 设备ID
     * @param status 告警状态
     * @return 告警列表（按 startTs 降序）
     */
    List<Alarm> findByOriginatorAndStatus(DeviceId deviceId, AlarmStatus status);
    
    /**
     * 查找所有未清除的告警
     * 
     * @return 告警列表（按 startTs 降序）
     */
    List<Alarm> findAllActive();
    
    /**
     * 查找所有未确认的告警
     * 
     * @return 告警列表（按 startTs 降序）
     */
    List<Alarm> findAllUnacknowledged();
    
    /**
     * 查找指定时间范围内的告警
     * 
     * @param startTs 开始时间（毫秒）
     * @param endTs 结束时间（毫秒）
     * @return 告警列表（按 startTs 降序）
     */
    List<Alarm> findByTimeRange(long startTs, long endTs);
    
    /**
     * 删除告警
     */
    void deleteById(AlarmId id);
    
    /**
     * 删除设备的所有告警
     */
    void deleteByOriginator(DeviceId deviceId);
    
    /**
     * 统计设备的告警数量
     */
    long countByOriginator(DeviceId deviceId);
    
    /**
     * 统计指定状态的告警数量
     */
    long countByStatus(AlarmStatus status);
}



