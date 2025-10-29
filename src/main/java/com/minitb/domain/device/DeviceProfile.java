package com.minitb.domain.device;

import com.minitb.domain.alarm.AlarmRule;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.id.RuleChainId;
import com.minitb.domain.telemetry.DataType;
import com.minitb.domain.protocol.PrometheusConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 设备配置文件
 * 定义设备的类型、遥测配置等
 * 
 * 设计理念：
 * 1. 一个 DeviceProfile 可以被多个 Device 共享
 * 2. 定义了设备应该有哪些遥测数据
 * 3. 支持 PromQL 查询定义（不只是简单的指标名）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProfile {
    
    /**
     * 配置文件唯一标识（强类型）
     */
    private DeviceProfileId id;
    
    /**
     * 配置文件名称
     * 例如: "温度传感器配置", "Web服务器监控", "数据库性能监控"
     */
    private String name;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 遥测定义列表
     */
    @Builder.Default
    private List<TelemetryDefinition> telemetryDefinitions = new ArrayList<>();
    
    /**
     * 告警规则列表
     */
    @Builder.Default
    private List<AlarmRule> alarmRules = new ArrayList<>();
    
    /**
     * 默认规则链ID
     * 如果设置，来自此DeviceProfile的设备的消息将路由到指定的规则链
     * 如果为null，则使用根规则链（Root Rule Chain）
     * 
     * 这允许不同类型的设备使用不同的规则链进行数据处理
     */
    private RuleChainId defaultRuleChainId;
    
    /**
     * 默认队列名称
     * 用于消息队列的负载均衡和优先级管理
     * 如果为null，则使用"Main"队列
     */
    private String defaultQueueName;
    
    /**
     * 是否严格模式
     * true: 只接受定义的遥测数据
     * false: 接受任意遥测数据，定义的只是"预期"的
     */
    @Builder.Default
    private boolean strictMode = false;
    
    /**
     * 数据源类型
     */
    @Builder.Default
    private DataSourceType dataSourceType = DataSourceType.MQTT;
    
    /**
     * Prometheus 设备标识标签键（仅 PROMETHEUS 类型使用）
     * 
     * 指定使用哪个 Prometheus 标签来识别设备
     * 示例: "instance", "job", "node", "host", "gpu"
     * 
     * 工作原理:
     * 1. PromQL 查询返回多个时间序列，每个都有标签
     * 2. 使用此字段指定的标签键来区分不同设备的数据
     * 3. Device.configuration (PrometheusDeviceConfiguration) 存储对应的标签值
     * 
     * 例如: prometheusDeviceLabelKey = "gpu"
     *      Device A: configuration.label = "gpu=0"
     *      Device B: configuration.label = "gpu=1"
     * 
     * 注意：Prometheus endpoint 移到了 Device.configuration 中
     *      每个设备可以连接不同的 Prometheus 服务器
     */
    private String prometheusDeviceLabelKey;
    
    /**
     * 创建时间
     */
    private long createdTime;
    
    /**
     * 添加遥测定义
     */
    public void addTelemetryDefinition(TelemetryDefinition definition) {
        if (this.telemetryDefinitions == null) {
            this.telemetryDefinitions = new ArrayList<>();
        }
        this.telemetryDefinitions.add(definition);
    }
    
    /**
     * 获取所有遥测键名
     */
    public Set<String> getTelemetryKeys() {
        Set<String> keys = new HashSet<>();
        if (telemetryDefinitions != null) {
            for (TelemetryDefinition def : telemetryDefinitions) {
                keys.add(def.getKey());
            }
        }
        return keys;
    }
    
    /**
     * 检查某个键是否在配置中
     */
    public boolean hasTelemetryKey(String key) {
        if (telemetryDefinitions == null) {
            return false;
        }
        return telemetryDefinitions.stream()
                .anyMatch(def -> def.getKey().equals(key));
    }
    
    /**
     * 检查是否包含指定的遥测定义
     */
    public boolean hasTelemetryDefinition(String key) {
        return telemetryDefinitions.stream()
                .anyMatch(def -> def.getKey().equals(key));
    }
    
    /**
     * 移除指定的遥测定义
     */
    public void removeTelemetryDefinition(String key) {
        telemetryDefinitions.removeIf(def -> def.getKey().equals(key));
    }
    
    /**
     * 更新遥测定义
     */
    public void updateTelemetryDefinition(TelemetryDefinition telemetryDefinition) {
        // 先移除旧的，再添加新的
        removeTelemetryDefinition(telemetryDefinition.getKey());
        addTelemetryDefinition(telemetryDefinition);
    }
    
    /**
     * 添加告警规则
     */
    public void addAlarmRule(AlarmRule rule) {
        if (this.alarmRules == null) {
            this.alarmRules = new ArrayList<>();
        }
        this.alarmRules.add(rule);
    }
    
    /**
     * 移除指定的告警规则
     */
    public void removeAlarmRule(String ruleId) {
        if (alarmRules != null) {
            alarmRules.removeIf(rule -> rule.getId().equals(ruleId));
        }
    }
    
    /**
     * 数据源类型
     */
    public enum DataSourceType {
        MQTT,           // MQTT 推送
        HTTP,           // HTTP 推送
        PROMETHEUS,     // Prometheus 拉取
        IPMI,           // IPMI 拉取（ipmitool）
        COAP            // CoAP 推送
    }
}

