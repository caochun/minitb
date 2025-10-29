package com.minitb.infrastructure.persistence.sqlite.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.domain.alarm.AlarmRule;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.id.RuleChainId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DeviceProfile ResultSet 映射器
 * 
 * 职责：
 * - 将 SQLite ResultSet 转换为 DeviceProfile 领域对象
 * - 处理 JSON 字段的反序列化
 */
@Component
@Slf4j
public class DeviceProfileRowMapper {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 映射单行结果到 DeviceProfile 对象
     */
    public DeviceProfile mapRow(ResultSet rs) throws SQLException {
        DeviceProfile.DeviceProfileBuilder builder = DeviceProfile.builder()
                .id(DeviceProfileId.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .strictMode(rs.getInt("strict_mode") == 1)
                .createdTime(rs.getLong("created_time"));
        
        // 数据源类型
        String dataSourceType = rs.getString("data_source_type");
        if (dataSourceType != null) {
            builder.dataSourceType(DeviceProfile.DataSourceType.valueOf(dataSourceType));
        }
        
        // Prometheus 配置
        // 注意: prometheusEndpoint 已移到 Device.configuration 中
        // builder.prometheusEndpoint(rs.getString("prometheus_endpoint"));
        builder.prometheusDeviceLabelKey(rs.getString("prometheus_device_label_key"));
        
        // 解析遥测定义 JSON
        String telemetryJson = rs.getString("telemetry_definitions_json");
        List<TelemetryDefinition> telemetryDefinitions = parseTelemetryDefinitions(telemetryJson);
        builder.telemetryDefinitions(telemetryDefinitions);
        
        // 解析告警规则 JSON
        String alarmRulesJson = rs.getString("alarm_rules_json");
        List<AlarmRule> alarmRules = parseAlarmRules(alarmRulesJson);
        builder.alarmRules(alarmRules);
        
        // 规则链和队列配置
        String ruleChainIdStr = rs.getString("default_rule_chain_id");
        if (ruleChainIdStr != null && !ruleChainIdStr.isEmpty()) {
            builder.defaultRuleChainId(RuleChainId.fromString(ruleChainIdStr));
        }
        builder.defaultQueueName(rs.getString("default_queue_name"));
        
        return builder.build();
    }
    
    /**
     * 解析遥测定义 JSON
     */
    private List<TelemetryDefinition> parseTelemetryDefinitions(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<List<TelemetryDefinition>>() {});
        } catch (Exception e) {
            log.error("解析遥测定义 JSON 失败: {}", json, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 解析告警规则 JSON
     */
    private List<AlarmRule> parseAlarmRules(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<List<AlarmRule>>() {});
        } catch (Exception e) {
            log.error("解析告警规则 JSON 失败: {}", json, e);
            return new ArrayList<>();
        }
    }
}


