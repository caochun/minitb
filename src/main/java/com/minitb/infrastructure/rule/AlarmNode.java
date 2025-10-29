package com.minitb.infrastructure.rule;

import com.minitb.domain.alarm.Alarm;
import com.minitb.domain.alarm.AlarmSeverity;
import com.minitb.domain.id.AlarmId;
import com.minitb.domain.messaging.Message;
import com.minitb.domain.messaging.MessageType;
import com.minitb.domain.id.RuleNodeId;
import com.minitb.domain.rule.RuleNode;
import com.minitb.domain.rule.RuleNodeConfig;
import com.minitb.domain.rule.RuleNodeContext;
import com.minitb.domain.telemetry.TsKvEntry;
import lombok.extern.slf4j.Slf4j;

/**
 * 告警节点（示例）
 * 
 * 功能：
 * - 根据遥测数据判断是否需要创建告警
 * - 简化版：仅检查温度阈值
 */
@Slf4j
public class AlarmNode implements RuleNode {
    
    private final RuleNodeId id;
    private final String name;
    private final String alarmType;
    private final String metricKey;
    private final double threshold;
    private final AlarmSeverity severity;
    private RuleNode next;
    
    /**
     * 构造器
     * 
     * @param name 节点名称
     * @param alarmType 告警类型
     * @param metricKey 监控指标键名
     * @param threshold 阈值
     * @param severity 严重级别
     */
    public AlarmNode(String name, String alarmType, String metricKey, 
                     double threshold, AlarmSeverity severity) {
        this.id = RuleNodeId.random();
        this.name = name;
        this.alarmType = alarmType;
        this.metricKey = metricKey;
        this.threshold = threshold;
        this.severity = severity;
    }
    
    @Override
    public void init(RuleNodeConfig config, RuleNodeContext context) {
        log.info("告警节点初始化: {} (监控: {} > {})", name, metricKey, threshold);
    }
    
    @Override
    public void onMsg(Message msg, RuleNodeContext context) {
        try {
            // 检查遥测数据
            if (msg.getTsKvEntries() != null) {
                for (TsKvEntry entry : msg.getTsKvEntries()) {
                    if (entry.getKey().equals(metricKey)) {
                        // 获取数值
                        Double value = entry.getDoubleValue().orElse(null);
                        if (value == null) {
                            value = entry.getLongValue().map(Long::doubleValue).orElse(null);
                        }
                        
                        if (value != null && value > threshold) {
                            // 创建告警
                            Alarm alarm = createAlarm(msg, value);
                            log.warn("🚨 告警触发: {} - {} = {} (阈值: {})", 
                                    alarmType, metricKey, value, threshold);
                            
                            // 可以在这里将告警保存到存储或发送通知
                            // context.saveAlarm(alarm);
                            // context.sendNotification(alarm);
                        }
                    }
                }
            }
            
            // 继续传递消息
            if (next != null) {
                next.onMsg(msg, context);
            }
            
        } catch (Exception e) {
            log.error("告警节点处理异常: {}", name, e);
        }
    }
    
    /**
     * 创建告警
     */
    private Alarm createAlarm(Message msg, double value) {
        long now = System.currentTimeMillis();
        return Alarm.builder()
                .id(AlarmId.random())
                .type(alarmType)
                .originator(msg.getOriginator())
                .severity(severity)
                .acknowledged(false)
                .cleared(false)
                .startTs(now)
                .endTs(now)
                .createdTime(now)
                .details(String.format("{\"metricKey\":\"%s\",\"value\":%.2f,\"threshold\":%.2f}", 
                        metricKey, value, threshold))
                .build();
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }
    
    @Override
    public RuleNodeId getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getNodeType() {
        return "ALARM";
    }
}

