package com.minitb.infrastructure.persistence.sqlite.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceConfiguration;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Device ResultSet 映射器
 * 
 * 职责：
 * - 将 SQLite ResultSet 转换为 Device 领域对象
 * - 处理 DeviceConfiguration 的 JSON 序列化/反序列化
 */
@Component
public class DeviceRowMapper {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 映射单行结果到 Device 对象
     */
    public Device mapRow(ResultSet rs) throws SQLException {
        String deviceProfileIdStr = rs.getString("device_profile_id");
        String configurationJson = rs.getString("configuration");
        
        // 反序列化 configuration
        DeviceConfiguration configuration = deserializeConfiguration(configurationJson);
        
        return Device.builder()
                .id(DeviceId.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .type(rs.getString("type"))
                .accessToken(rs.getString("access_token"))
                .deviceProfileId(deviceProfileIdStr != null ? 
                    DeviceProfileId.fromString(deviceProfileIdStr) : null)
                .configuration(configuration)
                .createdTime(rs.getLong("created_time"))
                .build();
    }
    
    /**
     * 序列化 DeviceConfiguration 为 JSON
     */
    public String serializeConfiguration(DeviceConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize device configuration", e);
        }
    }
    
    /**
     * 反序列化 JSON 为 DeviceConfiguration
     */
    private DeviceConfiguration deserializeConfiguration(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(json, DeviceConfiguration.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize device configuration: " + json, e);
        }
    }
}

