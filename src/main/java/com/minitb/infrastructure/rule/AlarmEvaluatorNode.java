package com.minitb.infrastructure.rule;

import com.minitb.application.service.DeviceService;
import com.minitb.application.service.alarm.AlarmEvaluator;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.RuleNodeId;
import com.minitb.domain.messaging.Message;
import com.minitb.domain.rule.RuleNode;
import com.minitb.domain.rule.RuleNodeContext;
import com.minitb.domain.telemetry.TsKvEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 告警评估规则节点
 * 
 * 职责：
 * 1. 在遥测数据保存后触发
 * 2. 获取设备的最新遥测数据
 * 3. 评估设备配置中定义的所有告警规则
 * 4. 根据评估结果创建、更新或清除告警
 */
@Slf4j
public class AlarmEvaluatorNode implements RuleNode {
    
    private final String label;
    private final AlarmEvaluator alarmEvaluator;
    private final DeviceService deviceService;
    private RuleNode next;
    
    public AlarmEvaluatorNode(
            String label,
            AlarmEvaluator alarmEvaluator,
            DeviceService deviceService) {
        this.label = label;
        this.alarmEvaluator = alarmEvaluator;
        this.deviceService = deviceService;
    }
    
    public AlarmEvaluatorNode(
            AlarmEvaluator alarmEvaluator,
            DeviceService deviceService) {
        this("告警评估", alarmEvaluator, deviceService);
    }
    
    @Override
    public void onMsg(Message msg, RuleNodeContext context) {
        try {
            // 1. 获取设备信息
            Optional<Device> deviceOpt = deviceService.findById(msg.getOriginator());
            if (deviceOpt.isEmpty()) {
                log.warn("[{}] 设备不存在: {}", label, msg.getOriginator());
                tellNext(msg, context);
                return;
            }
            
            Device device = deviceOpt.get();
            
            // 2. 获取设备配置
            Optional<DeviceProfile> profileOpt = deviceService.findProfileById(device.getDeviceProfileId());
            if (profileOpt.isEmpty()) {
                log.warn("[{}] 设备配置不存在: {}", label, device.getDeviceProfileId());
                tellNext(msg, context);
                return;
            }
            
            DeviceProfile profile = profileOpt.get();
            
            // 3. 检查是否有告警规则
            if (profile.getAlarmRules() == null || profile.getAlarmRules().isEmpty()) {
                // 没有告警规则，直接跳过
                tellNext(msg, context);
                return;
            }
            
            // 4. 获取设备的最新遥测数据
            Map<String, TsKvEntry> latestData = getLatestTelemetry(msg);
            
            if (latestData.isEmpty()) {
                log.debug("[{}] 没有遥测数据可用于评估", label);
                tellNext(msg, context);
                return;
            }
            
            // 5. 评估告警规则
            log.debug("[{}] 开始评估告警规则: {} - {} 个规则", 
                label, device.getName(), profile.getAlarmRules().size());
            
            alarmEvaluator.evaluate(device, profile, latestData);
            
        } catch (Exception e) {
            log.error("[{}] 告警评估失败: {}", label, e.getMessage(), e);
        } finally {
            // 总是传递到下一个节点
            tellNext(msg, context);
        }
    }
    
    /**
     * 从消息中提取最新遥测数据
     */
    private Map<String, TsKvEntry> getLatestTelemetry(Message msg) {
        Map<String, TsKvEntry> latestData = new HashMap<>();
        
        if (msg.hasTsKvEntries() && msg.getTsKvEntries() != null) {
            for (TsKvEntry entry : msg.getTsKvEntries()) {
                latestData.put(entry.getKey(), entry);
            }
        }
        
        return latestData;
    }
    
    private void tellNext(Message msg, RuleNodeContext context) {
        if (next != null) {
            next.onMsg(msg, context);
        }
    }
    
    @Override
    public void init(com.minitb.domain.rule.RuleNodeConfig config, RuleNodeContext context) {
        // 告警评估节点不需要额外配置
    }
    
    @Override
    public RuleNodeId getId() {
        return RuleNodeId.random();
    }
    
    @Override
    public String getName() {
        return label;
    }
    
    @Override
    public String getNodeType() {
        return "ALARM_EVALUATOR";
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }
    
    public RuleNode getNext() {
        return next;
    }
}

