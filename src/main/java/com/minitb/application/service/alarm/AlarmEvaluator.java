package com.minitb.application.service.alarm;

import com.minitb.domain.alarm.*;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.telemetry.TsKvEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警评估引擎
 * 
 * 核心职责：
 * 1. 评估设备的遥测数据是否满足告警规则
 * 2. 维护 DURATION 和 REPEATING 类型的评估上下文
 * 3. 生成告警动作（创建、更新、清除）
 */
@Component
@Slf4j
public class AlarmEvaluator {
    
    private final AlarmService alarmService;
    
    // 评估上下文缓存: deviceId#ruleId → context
    private final Map<String, AlarmEvaluationContext> contextCache = new ConcurrentHashMap<>();
    
    public AlarmEvaluator(AlarmService alarmService) {
        this.alarmService = alarmService;
    }
    
    /**
     * 评估设备的所有告警规则
     * 
     * @param device 设备
     * @param profile 设备配置
     * @param latestData 最新的遥测数据
     */
    public void evaluate(Device device, DeviceProfile profile, Map<String, TsKvEntry> latestData) {
        log.debug("[AlarmEvaluator] 开始评估设备 {} 的告警规则", device.getName());
        
        if (profile.getAlarmRules() == null || profile.getAlarmRules().isEmpty()) {
            log.debug("[AlarmEvaluator] 设备 {} 没有告警规则", device.getName());
            return;  // 没有告警规则
        }
        
        log.debug("[AlarmEvaluator] 设备 {} 有 {} 个告警规则", device.getName(), profile.getAlarmRules().size());
        
        for (AlarmRule rule : profile.getAlarmRules()) {
            try {
                log.debug("[AlarmEvaluator] 评估规则: {}", rule.getAlarmType());
                evaluateRule(device, rule, latestData);
            } catch (Exception e) {
                log.error("评估告警规则失败: {} - {}", rule.getAlarmType(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 评估单个告警规则
     */
    private void evaluateRule(Device device, AlarmRule rule, Map<String, TsKvEntry> latestData) {
        log.debug("[AlarmEvaluator] 评估规则 {} - 遥测数据: {}", rule.getAlarmType(), latestData.keySet());
        
        // 1. 评估创建条件（按严重程度从高到低）
        AlarmSeverity matchedSeverity = null;
        for (Map.Entry<AlarmSeverity, AlarmCondition> entry : rule.getSortedCreateConditions()) {
            log.debug("[AlarmEvaluator] 检查严重程度: {}", entry.getKey());
            if (evaluateCondition(device, rule, entry.getValue(), latestData)) {
                matchedSeverity = entry.getKey();
                log.info("[AlarmEvaluator] 条件满足! 严重程度: {}", matchedSeverity);
                break;  // 匹配到第一个（最高）严重程度就停止
            }
        }
        
        // 2. 查找当前是否有活动告警
        Optional<Alarm> existingAlarm = alarmService.findLatestByOriginatorAndType(
            device.getId(), rule.getAlarmType());
        
        if (matchedSeverity != null) {
            // 条件满足 → 创建或更新告警
            log.info("[AlarmEvaluator] 创建/更新告警: {} - {}", rule.getAlarmType(), matchedSeverity);
            alarmService.createOrUpdateAlarm(
                device.getId(), 
                device.getName(), 
                rule.getAlarmType(), 
                matchedSeverity
            );
        } else {
            log.debug("[AlarmEvaluator] 条件不满足，检查清除条件");
            // 3. 评估清除条件
            if (existingAlarm.isPresent() && !existingAlarm.get().isCleared()) {
                if (rule.getClearCondition() != null && 
                    evaluateCondition(device, rule, rule.getClearCondition(), latestData)) {
                    alarmService.clearAlarm(existingAlarm.get().getId());
                }
            }
        }
    }
    
    /**
     * 评估单个条件
     */
    private boolean evaluateCondition(
            Device device,
            AlarmRule rule,
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        switch (condition.getType()) {
            case SIMPLE:
                return evaluateSimple(condition, latestData);
            case DURATION:
                return evaluateDuration(device, rule, condition, latestData);
            case REPEATING:
                return evaluateRepeating(device, rule, condition, latestData);
            default:
                return false;
        }
    }
    
    /**
     * 简单条件：立即判断
     * 
     * 所有过滤器必须同时满足（AND 关系）
     */
    private boolean evaluateSimple(AlarmCondition condition, Map<String, TsKvEntry> latestData) {
        if (condition.getFilters() == null || condition.getFilters().isEmpty()) {
            log.debug("[AlarmEvaluator] 简单条件：没有过滤器");
            return false;
        }
        
        for (AlarmConditionFilter filter : condition.getFilters()) {
            TsKvEntry entry = latestData.get(filter.getKey());
            if (entry == null) {
                log.debug("[AlarmEvaluator] 简单条件：找不到key={}", filter.getKey());
                return false;
            }
            
            boolean matches = matchesFilter(entry, filter);
            log.debug("[AlarmEvaluator] 简单条件：key={}, value={}, operator={}, threshold={}, matches={}",
                filter.getKey(), entry.getValue(), filter.getOperator(), filter.getValue(), matches);
            
            if (!matches) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 持续条件：持续 N 秒满足
     * 
     * 需要维护首次满足时间
     */
    private boolean evaluateDuration(
            Device device,
            AlarmRule rule,
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        // 获取或创建评估上下文
        AlarmEvaluationContext context = getOrCreateContext(device, rule);
        
        // 当前是否满足简单条件
        boolean currentMatch = evaluateSimple(condition, latestData);
        long now = System.currentTimeMillis();
        
        if (currentMatch) {
            if (context.getFirstMatchTs() == null) {
                // 首次匹配，记录时间
                context.setFirstMatchTs(now);
                log.debug("开始持续评估: {} - {}", rule.getAlarmType(), device.getName());
                return false;  // 首次匹配，还未持续
            } else {
                // 计算持续时间
                long duration = now - context.getFirstMatchTs();
                int requiredDuration = condition.getSpec().getDurationSeconds() * 1000;
                
                if (duration >= requiredDuration) {
                    log.debug("持续条件满足: {} - {} ({} 秒)", 
                        rule.getAlarmType(), device.getName(), duration / 1000);
                    context.reset();  // 重置上下文
                    return true;
                }
                return false;  // 还未达到持续时间
            }
        } else {
            // 条件不满足，重置
            if (context.getFirstMatchTs() != null) {
                log.debug("持续评估中断: {} - {}", rule.getAlarmType(), device.getName());
            }
            context.setFirstMatchTs(null);
            return false;
        }
    }
    
    /**
     * 重复条件：连续 N 次满足
     */
    private boolean evaluateRepeating(
            Device device,
            AlarmRule rule,
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        // 获取或创建评估上下文
        AlarmEvaluationContext context = getOrCreateContext(device, rule);
        
        // 当前是否满足简单条件
        boolean currentMatch = evaluateSimple(condition, latestData);
        
        if (currentMatch) {
            context.incrementMatchCount();
            int requiredCount = condition.getSpec().getRepeatingCount();
            
            if (context.getMatchCount() >= requiredCount) {
                log.debug("重复条件满足: {} - {} ({} 次)", 
                    rule.getAlarmType(), device.getName(), context.getMatchCount());
                context.reset();  // 重置上下文
                return true;
            }
            
            log.debug("重复评估中: {} - {} ({}/{} 次)", 
                rule.getAlarmType(), device.getName(), context.getMatchCount(), requiredCount);
            return false;
        } else {
            // 条件不满足，重置计数
            if (context.getMatchCount() > 0) {
                log.debug("重复评估中断: {} - {}", rule.getAlarmType(), device.getName());
            }
            context.resetMatchCount();
            return false;
        }
    }
    
    /**
     * 判断遥测数据是否匹配过滤器
     */
    private boolean matchesFilter(TsKvEntry entry, AlarmConditionFilter filter) {
        Object value = filter.getValue();
        
        switch (filter.getOperator()) {
            case EQUAL:
                return compareEqual(entry, value);
            case NOT_EQUAL:
                return !compareEqual(entry, value);
            case GREATER_THAN:
                return compareGreaterThan(entry, value);
            case GREATER_OR_EQUAL:
                return compareGreaterThan(entry, value) || compareEqual(entry, value);
            case LESS_THAN:
                return compareLessThan(entry, value);
            case LESS_OR_EQUAL:
                return compareLessThan(entry, value) || compareEqual(entry, value);
            case CONTAINS:
                return compareContains(entry, value);
            case NOT_CONTAINS:
                return !compareContains(entry, value);
            case STARTS_WITH:
                return compareStartsWith(entry, value);
            case ENDS_WITH:
                return compareEndsWith(entry, value);
            default:
                return false;
        }
    }
    
    private boolean compareEqual(TsKvEntry entry, Object value) {
        if (entry.getLongValue().isPresent()) {
            return entry.getLongValue().get().equals(((Number) value).longValue());
        } else if (entry.getDoubleValue().isPresent()) {
            return Math.abs(entry.getDoubleValue().get() - ((Number) value).doubleValue()) < 0.0001;
        } else if (entry.getStrValue().isPresent()) {
            return entry.getStrValue().get().equals(value.toString());
        } else if (entry.getBooleanValue().isPresent()) {
            return entry.getBooleanValue().get().equals(value);
        }
        return false;
    }
    
    private boolean compareGreaterThan(TsKvEntry entry, Object value) {
        double threshold = ((Number) value).doubleValue();
        if (entry.getDoubleValue().isPresent()) {
            return entry.getDoubleValue().get() > threshold;
        } else if (entry.getLongValue().isPresent()) {
            return entry.getLongValue().get() > threshold;
        }
        return false;
    }
    
    private boolean compareLessThan(TsKvEntry entry, Object value) {
        double threshold = ((Number) value).doubleValue();
        if (entry.getDoubleValue().isPresent()) {
            return entry.getDoubleValue().get() < threshold;
        } else if (entry.getLongValue().isPresent()) {
            return entry.getLongValue().get() < threshold;
        }
        return false;
    }
    
    private boolean compareContains(TsKvEntry entry, Object value) {
        if (entry.getStrValue().isPresent()) {
            return entry.getStrValue().get().contains(value.toString());
        }
        return false;
    }
    
    private boolean compareStartsWith(TsKvEntry entry, Object value) {
        if (entry.getStrValue().isPresent()) {
            return entry.getStrValue().get().startsWith(value.toString());
        }
        return false;
    }
    
    private boolean compareEndsWith(TsKvEntry entry, Object value) {
        if (entry.getStrValue().isPresent()) {
            return entry.getStrValue().get().endsWith(value.toString());
        }
        return false;
    }
    
    /**
     * 获取或创建评估上下文
     */
    private AlarmEvaluationContext getOrCreateContext(Device device, AlarmRule rule) {
        String key = device.getId().toString() + "#" + rule.getId();
        return contextCache.computeIfAbsent(key, k -> new AlarmEvaluationContext(rule.getId()));
    }
    
    /**
     * 清理设备的评估上下文
     */
    public void clearContext(Device device) {
        String prefix = device.getId().toString() + "#";
        contextCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    /**
     * 清理设备特定告警类型的评估上下文
     * 
     * 使用场景：
     * - 告警被清除或确认后，重置 Duration/Repeating 计时
     * - 确保下次评估时重新开始计时
     */
    public void clearContextByAlarmType(DeviceId deviceId, String alarmType) {
        // 找到所有匹配的 key 并清理
        String devicePrefix = deviceId.toString() + "#";
        
        // 因为 contextCache 的 key 格式是 "deviceId#ruleId"，而不是 "deviceId#alarmType"
        // 所以我们需要遍历所有 key，检查对应的规则是否匹配该 alarmType
        // 但由于我们没有直接存储 alarmType -> ruleId 的映射，
        // 这里采用简化方案：清理所有该设备的上下文
        // 更精确的方案需要在上下文中存储 alarmType
        
        contextCache.keySet().removeIf(key -> key.startsWith(devicePrefix));
        
        log.debug("清理设备 {} 的告警类型 {} 的评估上下文", deviceId, alarmType);
    }
}

