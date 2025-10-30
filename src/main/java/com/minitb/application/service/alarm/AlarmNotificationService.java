package com.minitb.application.service.alarm;

import com.minitb.domain.alarm.Alarm;
import com.minitb.infrastructure.web.dto.alarm.AlarmNotificationDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警通知服务
 * 
 * 职责：
 * - 管理 SSE 连接
 * - 向所有订阅者推送告警通知
 * - 处理连接生命周期
 * - 定期清理无效连接
 */
@Service
@Slf4j
public class AlarmNotificationService {
    
    // 使用线程安全的列表存储所有 SSE 连接
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    @PostConstruct
    public void init() {
        log.info("告警通知服务已启动");
    }
    
    /**
     * 添加新的 SSE 连接
     */
    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        log.info("新增告警通知订阅者，当前订阅数: {}", emitters.size());
    }
    
    /**
     * 移除 SSE 连接
     */
    public void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
        log.info("移除告警通知订阅者，当前订阅数: {}", emitters.size());
    }
    
    /**
     * 推送告警通知到所有订阅者
     */
    public void notifyAlarmCreated(Alarm alarm) {
        if (emitters.isEmpty()) {
            log.debug("没有订阅者，跳过告警通知推送");
            return;
        }
        
        AlarmNotificationDto notification = AlarmNotificationDto.fromAlarm(alarm, "created");
        
        log.info("推送告警通知到 {} 个订阅者: {} [{}]", 
            emitters.size(), alarm.getType(), alarm.getSeverity());
        
        // 向所有订阅者发送通知
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("alarm")
                    .data(notification));
            } catch (IOException e) {
                log.warn("推送告警通知失败，连接将被自动移除: {}", e.getMessage());
                // 不在这里移除，让 onError 回调处理
                emitter.completeWithError(e);
            }
        });
    }
    
    /**
     * 推送告警更新通知
     */
    public void notifyAlarmUpdated(Alarm alarm) {
        if (emitters.isEmpty()) {
            return;
        }
        
        AlarmNotificationDto notification = AlarmNotificationDto.fromAlarm(alarm, "updated");
        
        log.info("推送告警更新通知: {} [{}]", alarm.getType(), alarm.getSeverity());
        
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("alarm")
                    .data(notification));
            } catch (IOException e) {
                log.warn("推送告警更新通知失败，连接将被自动移除: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }
    
    /**
     * 推送告警清除通知
     */
    public void notifyAlarmCleared(Alarm alarm) {
        if (emitters.isEmpty()) {
            return;
        }
        
        AlarmNotificationDto notification = AlarmNotificationDto.fromAlarm(alarm, "cleared");
        
        log.info("推送告警清除通知: {}", alarm.getType());
        
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("alarm")
                    .data(notification));
            } catch (IOException e) {
                log.warn("推送告警清除通知失败，连接将被自动移除: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }
    
    /**
     * 推送告警重复提醒
     */
    public void notifyAlarmRepeat(Alarm alarm) {
        if (emitters.isEmpty()) {
            return;
        }
        
        AlarmNotificationDto notification = AlarmNotificationDto.fromAlarm(alarm, "repeat");
        
        log.info("推送告警重复提醒（第 {} 次）到 {} 个订阅者: {} [{}]", 
            alarm.getNotificationCount(), emitters.size(), alarm.getType(), alarm.getSeverity());
        
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("alarm")
                    .data(notification));
            } catch (IOException e) {
                log.warn("推送告警重复提醒失败，连接将被自动移除: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }
    
    /**
     * 获取当前订阅者数量
     */
    public int getSubscriberCount() {
        return emitters.size();
    }
    
    /**
     * 定期清理无效的 SSE 连接
     * 每30秒执行一次心跳检测
     */
    @Scheduled(fixedRate = 30000)
    public void cleanupDeadConnections() {
        if (emitters.isEmpty()) {
            return;
        }
        
        int beforeCount = emitters.size();
        
        // 发送心跳消息，失败的连接会被自动移除
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .comment("heartbeat")
                    .data(""));
            } catch (IOException e) {
                log.debug("心跳检测失败，移除无效连接: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        
        int afterCount = emitters.size();
        if (beforeCount != afterCount) {
            log.info("清理了 {} 个无效连接，当前订阅数: {}", beforeCount - afterCount, afterCount);
        }
    }
}

