package com.minitb.infrastructure.persistence.jpa.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceConfiguration;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * 设备持久化实体
 * 
 * 职责：
 * - 映射到数据库表 device
 * - 提供 Domain Object ↔ Entity 转换
 * - 处理数据库特定的类型转换
 */
@Entity
@Table(name = "device", indexes = {
    @Index(name = "idx_device_access_token", columnList = "access_token", unique = true),
    @Index(name = "idx_device_profile_id", columnList = "device_profile_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEntity {
    
    /**
     * 设备ID（主键）
     */
    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;
    
    /**
     * 设备名称
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    /**
     * 设备类型
     */
    @Column(name = "type", nullable = false, length = 100)
    private String type;
    
    /**
     * 访问令牌（唯一）
     */
    @Column(name = "access_token", nullable = false, unique = true, length = 100)
    private String accessToken;
    
    /**
     * 设备配置文件ID（外键）
     */
    @Column(name = "device_profile_id", columnDefinition = "BINARY(16)")
    private UUID deviceProfileId;
    
    /**
     * 设备配置（JSON 格式）
     * 
     * 存储不同类型设备的配置信息：
     * - Prometheus: {"type":"PROMETHEUS","endpoint":"http://...","label":"gpu=0"}
     * - IPMI: {"type":"IPMI","host":"114.212.81.58","username":"admin",...}
     * 
     * 使用 TEXT 类型存储 JSON 字符串
     */
    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configurationJson;
    
    /**
     * 创建时间
     */
    @Column(name = "created_time", nullable = false)
    private Long createdTime;
    
    /**
     * ObjectMapper 用于 JSON 序列化/反序列化
     * 使用静态实例避免重复创建
     */
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 从领域对象转换为持久化实体
     */
    public static DeviceEntity fromDomain(Device device) {
        String configJson = null;
        if (device.getConfiguration() != null) {
            try {
                configJson = objectMapper.writeValueAsString(device.getConfiguration());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize device configuration", e);
            }
        }
        
        return DeviceEntity.builder()
                .id(device.getId().getId())
                .name(device.getName())
                .type(device.getType())
                .accessToken(device.getAccessToken())
                .deviceProfileId(device.getDeviceProfileId() != null ? 
                        device.getDeviceProfileId().getId() : null)
                .configurationJson(configJson)
                .createdTime(device.getCreatedTime())
                .build();
    }
    
    /**
     * 转换为领域对象
     */
    public Device toDomain() {
        DeviceConfiguration configuration = null;
        if (configurationJson != null && !configurationJson.isEmpty()) {
            try {
                configuration = objectMapper.readValue(configurationJson, DeviceConfiguration.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize device configuration", e);
            }
        }
        
        return Device.builder()
                .id(new DeviceId(id))
                .name(name)
                .type(type)
                .accessToken(accessToken)
                .deviceProfileId(deviceProfileId != null ? 
                        new DeviceProfileId(deviceProfileId) : null)
                .configuration(configuration)
                .createdTime(createdTime)
                .build();
    }
}

