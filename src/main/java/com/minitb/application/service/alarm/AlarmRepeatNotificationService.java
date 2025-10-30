package com.minitb.application.service.alarm;

import com.minitb.domain.alarm.Alarm;
import com.minitb.domain.alarm.AlarmSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警重复通知服务
 * 
 * 职责：
 * - 定期检查未确认的活动告警
 * - 根据严重程度的不同间隔重复推送
 * - 更新告警的通知时间和次数
 * 
 * 设计原则：
 * - 未确认的告警持续推送，直到被确认或清除
 * - 不设最大推送次数限制
 * - 严重程度越高，推送间隔越短
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmRepeatNotificationService {
    
    private final AlarmService alarmService;
    private final AlarmNotificationService notificationService;
    
    /**
     * 定期检查并重复推送未确认告警
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)  // 每分钟
    public void checkAndRepeatNotifications() {
        try {
            List<Alarm> unacknowledgedAlarms = alarmService.findAllUnacknowledged();
            
            if (unacknowledgedAlarms.isEmpty()) {
                log.debug("没有未确认的告警需要检查");
                return;
            }
            
            log.debug("检查 {} 个未确认告警是否需要重复推送", unacknowledgedAlarms.size());
            
            int repeatedCount = 0;
            for (Alarm alarm : unacknowledgedAlarms) {
                long intervalMillis = getRepeatInterval(alarm.getSeverity());
                
                if (alarm.needsRepeatNotification(intervalMillis)) {
                    // 记录通知
                    alarm.recordNotification();
                    alarmService.save(alarm);
                    
                    // 推送通知
                    notificationService.notifyAlarmRepeat(alarm);
                    
                    repeatedCount++;
                    log.info("🔔 重复推送告警（第 {} 次）: {} [{}] - {}", 
                        alarm.getNotificationCount(), 
                        alarm.getType(), 
                        alarm.getSeverity(), 
                        alarm.getOriginatorName());
                }
            }
            
            if (repeatedCount > 0) {
                log.info("本轮重复推送了 {} 个告警", repeatedCount);
            }
            
        } catch (Exception e) {
            log.error("检查重复告警推送时发生错误", e);
        }
    }
    
    /**
     * 根据严重程度获取重复推送间隔
     * 
     * @param severity 告警严重程度
     * @return 间隔时间（毫秒）
     */
    private long getRepeatInterval(AlarmSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 5 * 60 * 1000;   // 5 分钟
            case MAJOR    -> 10 * 60 * 1000;  // 10 分钟
            case MINOR    -> 15 * 60 * 1000;  // 15 分钟
            case WARNING  -> 30 * 60 * 1000;  // 30 分钟
            default       -> 15 * 60 * 1000;  // 默认 15 分钟
        };
    }
}

