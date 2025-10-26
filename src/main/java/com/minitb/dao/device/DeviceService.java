package com.minitb.dao.device;

import com.minitb.domain.entity.Device;
import com.minitb.domain.entity.DeviceId;
import com.minitb.domain.entity.DeviceProfile;
import com.minitb.domain.entity.DeviceProfileId;
import com.minitb.domain.entity.TelemetryDefinition;
import com.minitb.dao.common.exception.MiniTbException;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备服务接口
 * 定义设备和设备配置相关的业务逻辑
 */
public interface DeviceService {

    // ==================== Device 管理 ====================

    /**
     * 保存设备
     */
    Device save(Device device) throws MiniTbException;

    /**
     * 根据ID查找设备
     */
    Optional<Device> findById(DeviceId deviceId);

    /**
     * 根据ID获取设备（不存在则抛出异常）
     */
    Device getById(DeviceId deviceId) throws MiniTbException;

    /**
     * 根据名称查找设备
     */
    Optional<Device> findByName(String name);

    /**
     * 根据访问令牌查找设备
     */
    Optional<Device> findByAccessToken(String accessToken);

    /**
     * 删除设备
     */
    void delete(DeviceId deviceId) throws MiniTbException;

    /**
     * 获取所有设备
     */
    List<Device> findAll();

    /**
     * 根据设备配置ID查找设备
     */
    List<Device> findByDeviceProfileId(DeviceProfileId deviceProfileId);

    /**
     * 检查设备名称是否已存在
     */
    boolean existsByName(String name);

    /**
     * 检查访问令牌是否已存在
     */
    boolean existsByAccessToken(String accessToken);

    /**
     * 更新设备配置
     */
    Device updateDeviceProfile(DeviceId deviceId, DeviceProfileId deviceProfileId) throws MiniTbException;

    /**
     * 更新设备访问令牌
     */
    Device updateAccessToken(DeviceId deviceId, String accessToken) throws MiniTbException;

    // ==================== DeviceProfile 管理 ====================

    /**
     * 保存设备配置
     */
    DeviceProfile saveProfile(DeviceProfile deviceProfile) throws MiniTbException;

    /**
     * 根据ID查找设备配置
     */
    Optional<DeviceProfile> findProfileById(DeviceProfileId deviceProfileId);

    /**
     * 根据ID获取设备配置（不存在则抛出异常）
     */
    DeviceProfile getProfileById(DeviceProfileId deviceProfileId) throws MiniTbException;

    /**
     * 根据名称查找设备配置
     */
    Optional<DeviceProfile> findProfileByName(String name);

    /**
     * 删除设备配置
     */
    void deleteProfile(DeviceProfileId deviceProfileId) throws MiniTbException;

    /**
     * 获取所有设备配置
     */
    List<DeviceProfile> findAllProfiles();

    /**
     * 根据数据源类型查找设备配置
     */
    List<DeviceProfile> findProfilesByDataSourceType(DeviceProfile.DataSourceType dataSourceType);

    /**
     * 检查设备配置名称是否已存在
     */
    boolean existsProfileByName(String name);

    /**
     * 添加遥测定义到设备配置
     */
    DeviceProfile addTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition)
            throws MiniTbException;

    /**
     * 从设备配置移除遥测定义
     */
    DeviceProfile removeTelemetryDefinition(DeviceProfileId deviceProfileId, String telemetryKey)
            throws MiniTbException;

    /**
     * 更新设备配置的遥测定义
     */
    DeviceProfile updateTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition)
            throws MiniTbException;

    /**
     * 获取设备配置的遥测定义列表
     */
    List<TelemetryDefinition> getTelemetryDefinitions(DeviceProfileId deviceProfileId) throws MiniTbException;
}
