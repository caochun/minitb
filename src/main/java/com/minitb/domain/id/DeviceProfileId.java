package com.minitb.domain.id;

import java.util.UUID;

/**
 * 设备配置文件ID
 */
public class DeviceProfileId extends EntityId {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造器：使用指定UUID
     */
    public DeviceProfileId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static DeviceProfileId fromUUID(UUID uuid) {
        return new DeviceProfileId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static DeviceProfileId fromString(String profileId) {
        return new DeviceProfileId(UUID.fromString(profileId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static DeviceProfileId random() {
        return new DeviceProfileId(UUID.randomUUID());
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.DEVICE_PROFILE;
    }
}

