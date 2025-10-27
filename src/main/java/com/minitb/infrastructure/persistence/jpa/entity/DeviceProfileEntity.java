package com.minitb.infrastructure.persistence.jpa.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceProfileId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 设备配置文件持久化实体
 * 
 * 职责：
 * - 映射到数据库表 device_profile
 * - 处理复杂对象序列化（List<TelemetryDefinition> → JSON String）
 * - 提供 Domain Object ↔ Entity 转换
 */
@Entity
@Table(name = "device_profile", indexes = {
    @Index(name = "idx_device_profile_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class DeviceProfileEntity {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * 配置文件ID（主键）
     */
    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;
    
    /**
     * 配置文件名称
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    /**
     * 描述
     */
    @Column(name = "description", length = 1000)
    private String description;
    
    /**
     * 遥测定义（JSON 格式存储）
     */
    @Column(name = "telemetry_definitions", columnDefinition = "TEXT")
    private String telemetryDefinitionsJson;
    
    /**
     * 是否严格模式
     */
    @Column(name = "strict_mode", nullable = false)
    private Boolean strictMode;
    
    /**
     * 数据源类型
     */
    @Column(name = "data_source_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DeviceProfile.DataSourceType dataSourceType;
    
    /**
     * Prometheus 端点 URL（可选，仅 PROMETHEUS 类型使用）
     */
    @Column(name = "prometheus_endpoint", length = 255)
    private String prometheusEndpoint;
    
    /**
     * Prometheus 设备标识标签键（可选，仅 PROMETHEUS 类型使用）
     * 例如: "instance", "job", "node"
     */
    @Column(name = "prometheus_device_label_key", length = 100)
    private String prometheusDeviceLabelKey;
    
    /**
     * 创建时间
     */
    @Column(name = "created_time", nullable = false)
    private Long createdTime;
    
    /**
     * 从领域对象转换为持久化实体
     */
    public static DeviceProfileEntity fromDomain(DeviceProfile profile) {
        DeviceProfileEntityBuilder builder = DeviceProfileEntity.builder()
                .id(profile.getId().getId())
                .name(profile.getName())
                .description(profile.getDescription())
                .strictMode(profile.isStrictMode())
                .dataSourceType(profile.getDataSourceType())
                .prometheusEndpoint(profile.getPrometheusEndpoint())
                .prometheusDeviceLabelKey(profile.getPrometheusDeviceLabelKey())
                .createdTime(profile.getCreatedTime());
        
        // 序列化 TelemetryDefinitions 为 JSON
        if (profile.getTelemetryDefinitions() != null && !profile.getTelemetryDefinitions().isEmpty()) {
            try {
                String json = OBJECT_MAPPER.writeValueAsString(profile.getTelemetryDefinitions());
                builder.telemetryDefinitionsJson(json);
            } catch (JsonProcessingException e) {
                log.error("序列化 TelemetryDefinitions 失败", e);
                builder.telemetryDefinitionsJson("[]");
            }
        } else {
            builder.telemetryDefinitionsJson("[]");
        }
        
        return builder.build();
    }
    
    /**
     * 转换为领域对象
     */
    public DeviceProfile toDomain() {
        DeviceProfile.DeviceProfileBuilder builder = DeviceProfile.builder()
                .id(new DeviceProfileId(id))
                .name(name)
                .description(description)
                .strictMode(strictMode)
                .dataSourceType(dataSourceType)
                .prometheusEndpoint(prometheusEndpoint)
                .prometheusDeviceLabelKey(prometheusDeviceLabelKey)
                .createdTime(createdTime);
        
        // 反序列化 JSON 为 TelemetryDefinitions
        if (telemetryDefinitionsJson != null && !telemetryDefinitionsJson.isEmpty()) {
            try {
                List<TelemetryDefinition> definitions = OBJECT_MAPPER.readValue(
                        telemetryDefinitionsJson,
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(
                                List.class, TelemetryDefinition.class
                        )
                );
                builder.telemetryDefinitions(definitions);
            } catch (JsonProcessingException e) {
                log.error("反序列化 TelemetryDefinitions 失败: {}", telemetryDefinitionsJson, e);
                builder.telemetryDefinitions(new ArrayList<>());
            }
        } else {
            builder.telemetryDefinitions(new ArrayList<>());
        }
        
        return builder.build();
    }
}

