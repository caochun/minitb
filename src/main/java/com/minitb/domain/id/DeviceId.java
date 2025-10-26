package com.minitb.domain.id;

import java.util.UUID;

/**
 * 设备ID
 * 
 * 改进点：
 * 1. 不可变ID（继承自UUIDBased）
 * 2. 缓存hashCode
 * 3. 工厂方法
 * 4. 类型安全
 */
public class DeviceId extends UUIDBased {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造器：使用指定UUID
     */
    public DeviceId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static DeviceId fromUUID(UUID uuid) {
        return new DeviceId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static DeviceId fromString(String deviceId) {
        return new DeviceId(UUID.fromString(deviceId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static DeviceId random() {
        return new DeviceId(UUID.randomUUID());
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.DEVICE;
    }
}
