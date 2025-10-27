package com.minitb.application.service;

import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;

import java.util.List;
import java.util.Optional;

/**
 * 设备服务接口
 * 
 * 职责：
 * - 设备的业务逻辑处理
 * - 设备配置文件管理
 * - 对外提供统一的业务接口
 */
public interface DeviceService {
    
    // ==================== Device 管理 ====================
    
    /**
     * 保存设备
     */
    Device save(Device device);
    
    /**
     * 根据ID查找设备
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
     * 根据设备配置文件ID查找设备
     */
    List<Device> findByDeviceProfileId(DeviceProfileId deviceProfileId);
    
    /**
     * 删除设备
     */
    void delete(DeviceId deviceId);
    
    /**
     * 检查访问令牌是否存在
     */
    boolean existsByAccessToken(String accessToken);
    
    // ==================== DeviceProfile 管理 ====================
    
    /**
     * 保存设备配置文件
     */
    DeviceProfile saveProfile(DeviceProfile profile);
    
    /**
     * 根据ID查找设备配置文件
     */
    Optional<DeviceProfile> findProfileById(DeviceProfileId profileId);
    
    /**
     * 根据名称查找设备配置文件
     */
    Optional<DeviceProfile> findProfileByName(String name);
    
    /**
     * 查找所有设备配置文件
     */
    List<DeviceProfile> findAllProfiles();
    
    /**
     * 删除设备配置文件
     */
    void deleteProfile(DeviceProfileId profileId);
}


