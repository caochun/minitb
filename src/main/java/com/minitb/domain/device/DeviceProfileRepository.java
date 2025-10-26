package com.minitb.domain.device;

import com.minitb.domain.id.DeviceProfileId;

import java.util.List;
import java.util.Optional;

/**
 * DeviceProfile Repository Port (Domain Interface)
 * 设备配置仓储端口（领域接口）
 * 
 * 定义设备配置持久化的领域能力，不依赖任何技术实现
 */
public interface DeviceProfileRepository {
    
    /**
     * 保存设备配置（创建或更新）
     */
    DeviceProfile save(DeviceProfile deviceProfile);
    
    /**
     * 根据 ID 查找设备配置
     */
    Optional<DeviceProfile> findById(DeviceProfileId profileId);
    
    /**
     * 查找所有设备配置
     */
    List<DeviceProfile> findAll();
    
    /**
     * 删除设备配置
     */
    void deleteById(DeviceProfileId profileId);
}

