package com.minitb.application.service.alarm;

import com.minitb.domain.alarm.*;
import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.DeviceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 告警服务实现
 */
@Service
@Slf4j
public class AlarmServiceImpl implements AlarmService {
    
    private final AlarmRepository alarmRepository;
    
    public AlarmServiceImpl(AlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }
    
    @Override
    public Alarm createAlarm(DeviceId deviceId, String deviceName, String alarmType, AlarmSeverity severity) {
        long now = System.currentTimeMillis();
        
        Alarm alarm = Alarm.builder()
            .id(AlarmId.random())
            .originator(deviceId)
            .originatorName(deviceName)
            .type(alarmType)
            .severity(severity)
            .startTs(now)
            .endTs(now)
            .createdTime(now)
            .build();
        
        Alarm saved = alarmRepository.save(alarm);
        log.info("✅ 告警已创建: {} [{}] - {}", alarmType, severity, deviceName);
        
        return saved;
    }
    
    @Override
    public Alarm createOrUpdateAlarm(DeviceId deviceId, String deviceName, String alarmType, AlarmSeverity severity) {
        // 查找是否存在相同类型的活动告警
        Optional<Alarm> existing = findLatestByOriginatorAndType(deviceId, alarmType);
        
        if (existing.isPresent() && !existing.get().isCleared()) {
            // 存在活动告警，检查是否需要更新严重程度
            Alarm alarm = existing.get();
            if (!alarm.getSeverity().equals(severity)) {
                alarm.updateSeverity(severity);
                Alarm updated = alarmRepository.save(alarm);
                log.info("⚠️ 告警严重程度已更新: {} {} → {} - {}", 
                    alarmType, existing.get().getSeverity(), severity, deviceName);
                return updated;
            }
            // 严重程度相同，不需要更新
            return alarm;
        } else {
            // 不存在活动告警，创建新告警
            return createAlarm(deviceId, deviceName, alarmType, severity);
        }
    }
    
    @Override
    public Alarm clearAlarm(AlarmId alarmId) {
        Optional<Alarm> existing = alarmRepository.findById(alarmId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Alarm not found: " + alarmId);
        }
        
        Alarm alarm = existing.get();
        if (alarm.isCleared()) {
            log.debug("告警已经被清除: {}", alarmId);
            return alarm;
        }
        
        alarm.clear();
        Alarm saved = alarmRepository.save(alarm);
        log.info("🔕 告警已清除: {} - {}", alarm.getType(), alarm.getOriginatorName());
        
        return saved;
    }
    
    @Override
    public Optional<Alarm> clearAlarmByType(DeviceId deviceId, String alarmType) {
        Optional<Alarm> existing = findLatestByOriginatorAndType(deviceId, alarmType);
        
        if (existing.isPresent() && !existing.get().isCleared()) {
            Alarm cleared = clearAlarm(existing.get().getId());
            return Optional.of(cleared);
        }
        
        return Optional.empty();
    }
    
    @Override
    public Alarm acknowledgeAlarm(AlarmId alarmId) {
        Optional<Alarm> existing = alarmRepository.findById(alarmId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Alarm not found: " + alarmId);
        }
        
        Alarm alarm = existing.get();
        if (alarm.isAcknowledged()) {
            log.debug("告警已经被确认: {}", alarmId);
            return alarm;
        }
        
        alarm.acknowledge();
        Alarm saved = alarmRepository.save(alarm);
        log.info("✔️ 告警已确认: {} - {}", alarm.getType(), alarm.getOriginatorName());
        
        return saved;
    }
    
    @Override
    public Optional<Alarm> findById(AlarmId id) {
        return alarmRepository.findById(id);
    }
    
    @Override
    public Optional<Alarm> findLatestByOriginatorAndType(DeviceId deviceId, String alarmType) {
        return alarmRepository.findLatestByOriginatorAndType(deviceId, alarmType);
    }
    
    @Override
    public List<Alarm> findByDevice(DeviceId deviceId) {
        return alarmRepository.findByOriginator(deviceId);
    }
    
    @Override
    public List<Alarm> findByDeviceAndStatus(DeviceId deviceId, AlarmStatus status) {
        return alarmRepository.findByOriginatorAndStatus(deviceId, status);
    }
    
    @Override
    public List<Alarm> findAllActive() {
        return alarmRepository.findAllActive();
    }
    
    @Override
    public List<Alarm> findAllUnacknowledged() {
        return alarmRepository.findAllUnacknowledged();
    }
    
    @Override
    public void deleteAlarm(AlarmId id) {
        alarmRepository.deleteById(id);
        log.info("🗑️ 告警已删除: {}", id);
    }
    
    @Override
    public long countByDevice(DeviceId deviceId) {
        return alarmRepository.countByOriginator(deviceId);
    }
    
    @Override
    public long countByStatus(AlarmStatus status) {
        return alarmRepository.countByStatus(status);
    }
}


