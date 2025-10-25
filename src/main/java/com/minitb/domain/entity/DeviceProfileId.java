package com.minitb.domain.entity;

import java.util.UUID;

/**
 * 设备配置文件ID
 * 
 * 改进点：
 * 1. 不可变ID（继承自UUIDBased）
 * 2. 缓存hashCode
 * 3. 工厂方法
 * 4. 类型安全（与DeviceId、AssetId一致）
 */
public class DeviceProfileId extends UUIDBased {
    
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
}

