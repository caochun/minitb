package com.minitb.infrastructure.persistence.sqlite.mapper;

import com.minitb.domain.device.Device;
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
 */
@Component
public class DeviceRowMapper {
    
    /**
     * 映射单行结果到 Device 对象
     */
    public Device mapRow(ResultSet rs) throws SQLException {
        String deviceProfileIdStr = rs.getString("device_profile_id");
        
        return Device.builder()
                .id(DeviceId.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .type(rs.getString("type"))
                .accessToken(rs.getString("access_token"))
                .deviceProfileId(deviceProfileIdStr != null ? 
                    DeviceProfileId.fromString(deviceProfileIdStr) : null)
                .prometheusLabel(rs.getString("prometheus_label"))
                .createdTime(rs.getLong("created_time"))
                .build();
    }
}

