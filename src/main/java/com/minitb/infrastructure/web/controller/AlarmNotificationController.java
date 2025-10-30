package com.minitb.infrastructure.web.controller;

import com.minitb.application.service.alarm.AlarmNotificationService;
import com.minitb.infrastructure.web.dto.alarm.AlarmNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 告警通知控制器
 * 
 * 职责：
 * - 提供 SSE (Server-Sent Events) 端点用于实时推送告警
 * - 管理客户端连接
 */
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
@Slf4j
public class AlarmNotificationController {
    
    private final AlarmNotificationService notificationService;
    
    /**
     * SSE 端点：订阅实时告警通知
     * 
     * 使用方式：
     * const eventSource = new EventSource('/api/alarms/notifications/stream');
     * eventSource.addEventListener('alarm', (event) => {
     *   const alarm = JSON.parse(event.data);
     *   // 显示告警通知
     * });
     */
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlarmNotifications() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // 长连接
        
        // 注册到通知服务
        notificationService.addEmitter(emitter);
        
        // 处理连接关闭
        emitter.onCompletion(() -> {
            log.debug("SSE 连接完成");
            notificationService.removeEmitter(emitter);
        });
        
        emitter.onTimeout(() -> {
            log.debug("SSE 连接超时");
            notificationService.removeEmitter(emitter);
        });
        
        emitter.onError((e) -> {
            log.error("SSE 连接错误", e);
            notificationService.removeEmitter(emitter);
        });
        
        log.info("新的 SSE 告警订阅连接已建立");
        
        return emitter;
    }
}


