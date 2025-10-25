package com.minitb.service.deviceprofile;

import com.minitb.dao.DeviceProfileDao;
import com.minitb.domain.entity.DeviceProfile;
import com.minitb.domain.entity.DeviceProfileId;
import com.minitb.domain.entity.TelemetryDefinition;
import com.minitb.service.AbstractEntityService;
import com.minitb.service.MiniTbException;
import com.minitb.service.MiniTbErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备配置服务默认实现
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultDeviceProfileService extends AbstractEntityService implements DeviceProfileService {

    private final DeviceProfileDao deviceProfileDao;

    @Override
    public DeviceProfile save(DeviceProfile deviceProfile) throws MiniTbException {
        log.info("保存设备配置: {}", deviceProfile.getName());
        
        // 1. 验证设备配置数据
        validateDeviceProfile(deviceProfile);
        
        // 2. 检查名称冲突
        if (deviceProfile.getId() == null) {
            // 新设备配置，检查名称是否已存在
            if (existsByName(deviceProfile.getName())) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                    "Device profile with name '" + deviceProfile.getName() + "' already exists");
            }
        } else {
            // 更新设备配置，检查名称冲突（排除自己）
            Optional<DeviceProfile> existingProfile = findById(deviceProfile.getId());
            if (existingProfile.isPresent() && !existingProfile.get().getName().equals(deviceProfile.getName())) {
                if (existsByName(deviceProfile.getName())) {
                    throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                        "Device profile with name '" + deviceProfile.getName() + "' already exists");
                }
            }
        }
        
        // 3. 保存设备配置
        DeviceProfile savedProfile = deviceProfileDao.save(deviceProfile);
        
        // 4. 记录操作日志
        logEntityAction(savedProfile.getId(), "DEVICE_PROFILE_SAVED", "Device profile saved: " + savedProfile.getName());
        
        log.info("设备配置保存成功: {} (ID: {})", savedProfile.getName(), savedProfile.getId());
        return savedProfile;
    }

    @Override
    public Optional<DeviceProfile> findById(DeviceProfileId deviceProfileId) {
        validateEntityId(deviceProfileId);
        return deviceProfileDao.findById(deviceProfileId);
    }

    @Override
    public DeviceProfile getById(DeviceProfileId deviceProfileId) throws MiniTbException {
        return checkNotNull(findById(deviceProfileId), "Device profile not found with ID: " + deviceProfileId);
    }

    @Override
    public Optional<DeviceProfile> findByName(String name) {
        validateEntityName(name);
        return deviceProfileDao.findByName(name);
    }

    @Override
    public void delete(DeviceProfileId deviceProfileId) throws MiniTbException {
        log.info("删除设备配置: {}", deviceProfileId);
        
        // 1. 检查设备配置是否存在
        DeviceProfile deviceProfile = getById(deviceProfileId);
        
        // 2. 检查是否被设备使用
        if (deviceProfileDao.isUsedByDevices(deviceProfileId)) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                "Device profile is being used by devices and cannot be deleted");
        }
        
        // 3. 删除设备配置
        deviceProfileDao.delete(deviceProfile);
        
        // 4. 记录操作日志
        logEntityAction(deviceProfileId, "DEVICE_PROFILE_DELETED", "Device profile deleted: " + deviceProfile.getName());
        
        log.info("设备配置删除成功: {} (ID: {})", deviceProfile.getName(), deviceProfileId);
    }

    @Override
    public List<DeviceProfile> findAll() {
        return deviceProfileDao.findAll();
    }

    @Override
    public List<DeviceProfile> findByDataSourceType(DeviceProfile.DataSourceType dataSourceType) {
        if (dataSourceType == null) {
            return List.of();
        }
        return deviceProfileDao.findByDataSourceType(dataSourceType);
    }

    @Override
    public boolean existsByName(String name) {
        validateEntityName(name);
        return deviceProfileDao.existsByName(name);
    }

    @Override
    public DeviceProfile addTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition) 
            throws MiniTbException {
        log.info("添加遥测定义: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        
        // 1. 验证遥测定义
        validateTelemetryDefinition(telemetryDefinition);
        
        // 2. 获取设备配置
        DeviceProfile deviceProfile = getById(deviceProfileId);
        
        // 3. 检查遥测定义是否已存在
        if (deviceProfile.hasTelemetryDefinition(telemetryDefinition.getKey())) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                "Telemetry definition with key '" + telemetryDefinition.getKey() + "' already exists");
        }
        
        // 4. 添加遥测定义
        deviceProfile.addTelemetryDefinition(telemetryDefinition);
        DeviceProfile updatedProfile = deviceProfileDao.save(deviceProfile);
        
        // 5. 记录操作日志
        logEntityAction(deviceProfileId, "TELEMETRY_DEFINITION_ADDED", "Telemetry definition added: " + telemetryDefinition.getKey());
        
        log.info("遥测定义添加成功: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        return updatedProfile;
    }

    @Override
    public DeviceProfile removeTelemetryDefinition(DeviceProfileId deviceProfileId, String telemetryKey) 
            throws MiniTbException {
        log.info("移除遥测定义: {} -> {}", deviceProfileId, telemetryKey);
        
        // 1. 验证遥测键
        if (telemetryKey == null || telemetryKey.trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Telemetry key cannot be null or empty");
        }
        
        // 2. 获取设备配置
        DeviceProfile deviceProfile = getById(deviceProfileId);
        
        // 3. 检查遥测定义是否存在
        if (!deviceProfile.hasTelemetryDefinition(telemetryKey)) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, 
                "Telemetry definition with key '" + telemetryKey + "' not found");
        }
        
        // 4. 移除遥测定义
        deviceProfile.removeTelemetryDefinition(telemetryKey);
        DeviceProfile updatedProfile = deviceProfileDao.save(deviceProfile);
        
        // 5. 记录操作日志
        logEntityAction(deviceProfileId, "TELEMETRY_DEFINITION_REMOVED", "Telemetry definition removed: " + telemetryKey);
        
        log.info("遥测定义移除成功: {} -> {}", deviceProfileId, telemetryKey);
        return updatedProfile;
    }

    @Override
    public DeviceProfile updateTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition) 
            throws MiniTbException {
        log.info("更新遥测定义: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        
        // 1. 验证遥测定义
        validateTelemetryDefinition(telemetryDefinition);
        
        // 2. 获取设备配置
        DeviceProfile deviceProfile = getById(deviceProfileId);
        
        // 3. 检查遥测定义是否存在
        if (!deviceProfile.hasTelemetryDefinition(telemetryDefinition.getKey())) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, 
                "Telemetry definition with key '" + telemetryDefinition.getKey() + "' not found");
        }
        
        // 4. 更新遥测定义
        deviceProfile.updateTelemetryDefinition(telemetryDefinition);
        DeviceProfile updatedProfile = deviceProfileDao.save(deviceProfile);
        
        // 5. 记录操作日志
        logEntityAction(deviceProfileId, "TELEMETRY_DEFINITION_UPDATED", "Telemetry definition updated: " + telemetryDefinition.getKey());
        
        log.info("遥测定义更新成功: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        return updatedProfile;
    }

    @Override
    public List<TelemetryDefinition> getTelemetryDefinitions(DeviceProfileId deviceProfileId) throws MiniTbException {
        DeviceProfile deviceProfile = getById(deviceProfileId);
        return deviceProfile.getTelemetryDefinitions();
    }

    /**
     * 验证设备配置数据
     */
    private void validateDeviceProfile(DeviceProfile deviceProfile) throws MiniTbException {
        if (deviceProfile == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Device profile cannot be null");
        }
        
        validateEntityName(deviceProfile.getName());
        
        if (deviceProfile.getDataSourceType() == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Device profile data source type cannot be null");
        }
        
        if (deviceProfile.getCreatedTime() <= 0) {
            deviceProfile.setCreatedTime(System.currentTimeMillis());
        }
    }

    /**
     * 验证遥测定义数据
     */
    private void validateTelemetryDefinition(TelemetryDefinition telemetryDefinition) throws MiniTbException {
        if (telemetryDefinition == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Telemetry definition cannot be null");
        }
        
        if (telemetryDefinition.getKey() == null || telemetryDefinition.getKey().trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Telemetry definition key cannot be null or empty");
        }
        
        if (telemetryDefinition.getDataType() == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Telemetry definition data type cannot be null");
        }
    }
}
