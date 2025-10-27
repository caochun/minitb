package com.minitb.infrastructure.web.dto;

import com.minitb.domain.device.Device;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDto {
    
    private String id;
    private String name;
    private String type;
    private String prometheusLabel;
    private Long createdTime;
    
    /**
     * 从领域对象转换
     */
    public static DeviceDto fromDomain(Device device) {
        return DeviceDto.builder()
                .id(device.getId().toString())
                .name(device.getName())
                .type(device.getType())
                .prometheusLabel(device.getPrometheusLabel())
                .createdTime(device.getCreatedTime())
                .build();
    }
}

