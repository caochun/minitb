package com.minitb.web.dto;

import com.minitb.domain.device.Device;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDto {
    private String id;
    private String name;
    private String type;
    private String accessToken;
    private String deviceProfileId;
    private Long createdTime;
    
    /**
     * 从领域对象转换为DTO
     */
    public static DeviceDto fromDomain(Device device) {
        return DeviceDto.builder()
                .id(device.getId() != null ? device.getId().toString() : null)
                .name(device.getName())
                .type(device.getType())
                .accessToken(device.getAccessToken())
                .deviceProfileId(device.getDeviceProfileId() != null ? device.getDeviceProfileId().toString() : null)
                .createdTime(device.getCreatedTime())
                .build();
    }
}

