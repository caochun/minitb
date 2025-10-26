package com.minitb.domain.device;

import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
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
    private String name;
    private String type;
    private String accessToken;
    private long createdTime;
    
    /**
     * 设备配置文件ID（强类型）
     * 定义了设备应该有哪些遥测数据
     */
    private DeviceProfileId deviceProfileId;

    public Device(String name, String type, String accessToken) {
        this.id = DeviceId.random();
        this.name = name;
        this.type = type;
        this.accessToken = accessToken;
        this.createdTime = System.currentTimeMillis();
    }
    
    public Device(String name, String type, String accessToken, DeviceProfileId deviceProfileId) {
        this(name, type, accessToken);
        this.deviceProfileId = deviceProfileId;
    }
}
