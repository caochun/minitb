package com.minitb.dao.deviceprofile;

import com.minitb.domain.entity.DeviceProfile;
import com.minitb.domain.entity.DeviceProfileId;
import com.minitb.domain.entity.TelemetryDefinition;
import com.minitb.dao.common.exception.MiniTbException;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备配置服务接口
 * 定义设备配置相关的业务逻辑
 */
public interface DeviceProfileService {

    /**
     * 保存设备配置
     */
    DeviceProfile save(DeviceProfile deviceProfile) throws MiniTbException;

    /**
     * 根据ID查找设备配置
     */
    Optional<DeviceProfile> findById(DeviceProfileId deviceProfileId);

    /**
     * 根据ID获取设备配置（不存在则抛出异常）
     */
    DeviceProfile getById(DeviceProfileId deviceProfileId) throws MiniTbException;

    /**
     * 根据名称查找设备配置
     */
    Optional<DeviceProfile> findByName(String name);

    /**
     * 删除设备配置
     */
    void delete(DeviceProfileId deviceProfileId) throws MiniTbException;

    /**
     * 获取所有设备配置
     */
    List<DeviceProfile> findAll();

    /**
     * 根据数据源类型查找设备配置
     */
    List<DeviceProfile> findByDataSourceType(DeviceProfile.DataSourceType dataSourceType);

    /**
     * 检查设备配置名称是否已存在
     */
    boolean existsByName(String name);

    /**
     * 添加遥测定义
     */
    DeviceProfile addTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition) 
            throws MiniTbException;

    /**
     * 移除遥测定义
     */
    DeviceProfile removeTelemetryDefinition(DeviceProfileId deviceProfileId, String telemetryKey) 
            throws MiniTbException;

    /**
     * 更新遥测定义
     */
    DeviceProfile updateTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition) 
            throws MiniTbException;

    /**
     * 获取遥测定义列表
     */
    List<TelemetryDefinition> getTelemetryDefinitions(DeviceProfileId deviceProfileId) throws MiniTbException;
}
