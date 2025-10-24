package com.minitb.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    private DeviceId id;
    private TenantId tenantId;
    private String name;
    private String type;
    private String accessToken;
    private long createdTime;
    
    /**
     * 设备配置文件ID（新增）
     * 定义了设备应该有哪些遥测数据
     */
    private String deviceProfileId;

    public Device(String name, String type, String accessToken) {
        this.id = DeviceId.fromUUID(java.util.UUID.randomUUID());
        this.tenantId = TenantId.systemTenantId();
        this.name = name;
        this.type = type;
        this.accessToken = accessToken;
        this.createdTime = System.currentTimeMillis();
    }
    
    public Device(String name, String type, String accessToken, String deviceProfileId) {
        this(name, type, accessToken);
        this.deviceProfileId = deviceProfileId;
    }
}
