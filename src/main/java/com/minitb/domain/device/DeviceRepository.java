package com.minitb.domain.device;

import com.minitb.domain.id.DeviceId;

import java.util.List;
import java.util.Optional;

/**
 * Device Repository Port (Domain Interface)
 * 设备仓储端口（领域接口）
 * 
 * 定义设备持久化的领域能力，不依赖任何技术实现
 */
public interface DeviceRepository {
    
    /**
     * 保存设备（创建或更新）
     */
    Device save(Device device);
    
    /**
     * 根据 ID 查找设备
     */
    Optional<Device> findById(DeviceId deviceId);
    
    /**
     * 根据访问令牌查找设备
     */
    Optional<Device> findByAccessToken(String accessToken);
    
    /**
     * 查找所有设备
     */
    List<Device> findAll();
    
    /**
     * 删除设备
     */
    void deleteById(DeviceId deviceId);
}


