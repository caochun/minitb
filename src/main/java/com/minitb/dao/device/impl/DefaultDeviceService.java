package com.minitb.dao.device.impl;

import com.minitb.dao.device.DeviceService;
import com.minitb.dao.DeviceDao;
import com.minitb.dao.DeviceProfileDao;
import com.minitb.domain.device.Device;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.dao.common.AbstractEntityService;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备服务默认实现
 * 管理设备和设备配置
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultDeviceService extends AbstractEntityService implements DeviceService {

    private final DeviceDao deviceDao;
    private final DeviceProfileDao deviceProfileDao;

    @Override
    public Device save(Device device) throws MiniTbException {
        log.info("保存设备: {}", device.getName());
        
        // 1. 验证设备数据
        validateDevice(device);
        
        // 2. 检查名称冲突
        if (device.getId() == null) {
            // 新设备，检查名称是否已存在
            if (existsByName(device.getName())) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                    "Device with name '" + device.getName() + "' already exists");
            }
            // 检查访问令牌是否已存在
            if (device.getAccessToken() != null && existsByAccessToken(device.getAccessToken())) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                    "Device with access token already exists");
            }
        } else {
            // 更新设备，检查名称冲突（排除自己）
            Optional<Device> existingDevice = findById(device.getId());
            if (existingDevice.isPresent() && !existingDevice.get().getName().equals(device.getName())) {
                if (existsByName(device.getName())) {
                    throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                        "Device with name '" + device.getName() + "' already exists");
                }
            }
        }
        
        // 3. 保存设备
        Device savedDevice = deviceDao.save(device);
        
        // 4. 记录操作日志
        logEntityAction(savedDevice.getId(), "DEVICE_SAVED", "Device saved: " + savedDevice.getName());
        
        log.info("设备保存成功: {} (ID: {})", savedDevice.getName(), savedDevice.getId());
        return savedDevice;
    }

    @Override
    public Optional<Device> findById(DeviceId deviceId) {
        validateEntityId(deviceId);
        return deviceDao.findById(deviceId);
    }

    @Override
    public Device getById(DeviceId deviceId) throws MiniTbException {
        return checkNotNull(findById(deviceId), "Device not found with ID: " + deviceId);
    }

    @Override
    public Optional<Device> findByName(String name) {
        validateEntityName(name);
        return deviceDao.findByName(name);
    }

    @Override
    public Optional<Device> findByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return Optional.empty();
        }
        return deviceDao.findByAccessToken(accessToken);
    }

    @Override
    public void delete(DeviceId deviceId) throws MiniTbException {
        log.info("删除设备: {}", deviceId);
        
        // 1. 检查设备是否存在
        Device device = getById(deviceId);
        
        // 2. 删除设备
        deviceDao.delete(device);
        
        // 3. 记录操作日志
        logEntityAction(deviceId, "DEVICE_DELETED", "Device deleted: " + device.getName());
        
        log.info("设备删除成功: {} (ID: {})", device.getName(), deviceId);
    }

    @Override
    public List<Device> findAll() {
        return deviceDao.findAll();
    }

    @Override
    public List<Device> findByDeviceProfileId(DeviceProfileId deviceProfileId) {
        if (deviceProfileId == null) {
            return List.of();
        }
        return deviceDao.findByDeviceProfileId(deviceProfileId.toString());
    }

    @Override
    public boolean existsByName(String name) {
        validateEntityName(name);
        return deviceDao.existsByName(name);
    }

    @Override
    public boolean existsByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return false;
        }
        return deviceDao.existsByAccessToken(accessToken);
    }

    @Override
    public Device updateDeviceProfile(DeviceId deviceId, DeviceProfileId deviceProfileId) throws MiniTbException {
        log.info("更新设备配置: {} -> {}", deviceId, deviceProfileId);
        
        // 1. 获取设备
        Device device = getById(deviceId);
        
        // 2. 更新设备配置
        device.setDeviceProfileId(deviceProfileId);
        Device updatedDevice = deviceDao.save(device);
        
        // 3. 记录操作日志
        logEntityAction(deviceId, "DEVICE_PROFILE_UPDATED", "Device profile updated to: " + deviceProfileId);
        
        log.info("设备配置更新成功: {} -> {}", deviceId, deviceProfileId);
        return updatedDevice;
    }

    @Override
    public Device updateAccessToken(DeviceId deviceId, String accessToken) throws MiniTbException {
        log.info("更新设备访问令牌: {}", deviceId);
        
        // 1. 获取设备
        Device device = getById(deviceId);
        
        // 2. 检查访问令牌是否已存在
        if (accessToken != null && existsByAccessToken(accessToken)) {
            Optional<Device> existingDevice = findByAccessToken(accessToken);
            if (existingDevice.isPresent() && !existingDevice.get().getId().equals(deviceId)) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Access token already exists");
            }
        }
        
        // 3. 更新访问令牌
        device.setAccessToken(accessToken);
        Device updatedDevice = deviceDao.save(device);
        
        // 4. 记录操作日志
        logEntityAction(deviceId, "DEVICE_ACCESS_TOKEN_UPDATED", "Device access token updated");
        
        log.info("设备访问令牌更新成功: {}", deviceId);
        return updatedDevice;
    }

    // ==================== DeviceProfile 管理实现 ====================

    @Override
    public DeviceProfile saveProfile(DeviceProfile deviceProfile) throws MiniTbException {
        log.info("保存设备配置: {}", deviceProfile.getName());
        
        // 1. 验证设备配置数据
        validateDeviceProfile(deviceProfile);
        
        // 2. 检查名称冲突
        if (deviceProfile.getId() == null) {
            if (existsProfileByName(deviceProfile.getName())) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS,
                    "Device profile with name '" + deviceProfile.getName() + "' already exists");
            }
        } else {
            Optional<DeviceProfile> existingProfile = findProfileById(deviceProfile.getId());
            if (existingProfile.isPresent() && !existingProfile.get().getName().equals(deviceProfile.getName())) {
                if (existsProfileByName(deviceProfile.getName())) {
                    throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS,
                        "Device profile with name '" + deviceProfile.getName() + "' already exists");
                }
            }
        }
        
        DeviceProfile savedProfile = deviceProfileDao.save(deviceProfile);
        logEntityAction(savedProfile.getId(), "DEVICE_PROFILE_SAVED", "Device profile saved: " + savedProfile.getName());
        
        log.info("设备配置保存成功: {} (ID: {})", savedProfile.getName(), savedProfile.getId());
        return savedProfile;
    }

    @Override
    public Optional<DeviceProfile> findProfileById(DeviceProfileId deviceProfileId) {
        validateEntityId(deviceProfileId);
        return deviceProfileDao.findById(deviceProfileId);
    }

    @Override
    public DeviceProfile getProfileById(DeviceProfileId deviceProfileId) throws MiniTbException {
        return checkNotNull(findProfileById(deviceProfileId), "Device profile not found with ID: " + deviceProfileId);
    }

    @Override
    public Optional<DeviceProfile> findProfileByName(String name) {
        validateEntityName(name);
        return deviceProfileDao.findByName(name);
    }

    @Override
    public void deleteProfile(DeviceProfileId deviceProfileId) throws MiniTbException {
        log.info("删除设备配置: {}", deviceProfileId);
        
        DeviceProfile deviceProfile = getProfileById(deviceProfileId);
        
        if (deviceProfileDao.isUsedByDevices(deviceProfileId)) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS,
                "Device profile is being used by devices and cannot be deleted");
        }
        
        deviceProfileDao.delete(deviceProfile);
        logEntityAction(deviceProfileId, "DEVICE_PROFILE_DELETED", "Device profile deleted: " + deviceProfile.getName());
        
        log.info("设备配置删除成功: {} (ID: {})", deviceProfile.getName(), deviceProfileId);
    }

    @Override
    public List<DeviceProfile> findAllProfiles() {
        return deviceProfileDao.findAll();
    }

    @Override
    public List<DeviceProfile> findProfilesByDataSourceType(DeviceProfile.DataSourceType dataSourceType) {
        if (dataSourceType == null) {
            return List.of();
        }
        return deviceProfileDao.findByDataSourceType(dataSourceType);
    }

    @Override
    public boolean existsProfileByName(String name) {
        validateEntityName(name);
        return deviceProfileDao.existsByName(name);
    }

    @Override
    public DeviceProfile addTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition)
            throws MiniTbException {
        log.info("添加遥测定义: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        
        validateTelemetryDefinition(telemetryDefinition);
        DeviceProfile deviceProfile = getProfileById(deviceProfileId);
        
        if (deviceProfile.hasTelemetryDefinition(telemetryDefinition.getKey())) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS,
                "Telemetry definition with key '" + telemetryDefinition.getKey() + "' already exists");
        }
        
        deviceProfile.addTelemetryDefinition(telemetryDefinition);
        DeviceProfile updatedProfile = deviceProfileDao.save(deviceProfile);
        logEntityAction(deviceProfileId, "TELEMETRY_DEFINITION_ADDED", "Telemetry definition added: " + telemetryDefinition.getKey());
        
        log.info("遥测定义添加成功: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        return updatedProfile;
    }

    @Override
    public DeviceProfile removeTelemetryDefinition(DeviceProfileId deviceProfileId, String telemetryKey)
            throws MiniTbException {
        log.info("移除遥测定义: {} -> {}", deviceProfileId, telemetryKey);
        
        if (telemetryKey == null || telemetryKey.trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Telemetry key cannot be null or empty");
        }
        
        DeviceProfile deviceProfile = getProfileById(deviceProfileId);
        
        if (!deviceProfile.hasTelemetryDefinition(telemetryKey)) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND,
                "Telemetry definition with key '" + telemetryKey + "' not found");
        }
        
        deviceProfile.removeTelemetryDefinition(telemetryKey);
        DeviceProfile updatedProfile = deviceProfileDao.save(deviceProfile);
        logEntityAction(deviceProfileId, "TELEMETRY_DEFINITION_REMOVED", "Telemetry definition removed: " + telemetryKey);
        
        log.info("遥测定义移除成功: {} -> {}", deviceProfileId, telemetryKey);
        return updatedProfile;
    }

    @Override
    public DeviceProfile updateTelemetryDefinition(DeviceProfileId deviceProfileId, TelemetryDefinition telemetryDefinition)
            throws MiniTbException {
        log.info("更新遥测定义: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        
        validateTelemetryDefinition(telemetryDefinition);
        DeviceProfile deviceProfile = getProfileById(deviceProfileId);
        
        if (!deviceProfile.hasTelemetryDefinition(telemetryDefinition.getKey())) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND,
                "Telemetry definition with key '" + telemetryDefinition.getKey() + "' not found");
        }
        
        deviceProfile.updateTelemetryDefinition(telemetryDefinition);
        DeviceProfile updatedProfile = deviceProfileDao.save(deviceProfile);
        logEntityAction(deviceProfileId, "TELEMETRY_DEFINITION_UPDATED", "Telemetry definition updated: " + telemetryDefinition.getKey());
        
        log.info("遥测定义更新成功: {} -> {}", deviceProfileId, telemetryDefinition.getKey());
        return updatedProfile;
    }

    @Override
    public List<TelemetryDefinition> getTelemetryDefinitions(DeviceProfileId deviceProfileId) throws MiniTbException {
        DeviceProfile deviceProfile = getProfileById(deviceProfileId);
        return deviceProfile.getTelemetryDefinitions();
    }

    // ==================== 私有验证方法 ====================

    /**
     * 验证设备数据
     */
    private void validateDevice(Device device) throws MiniTbException {
        if (device == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Device cannot be null");
        }
        
        validateEntityName(device.getName());
        
        if (device.getType() == null || device.getType().trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Device type cannot be null or empty");
        }
        
        if (device.getCreatedTime() <= 0) {
            device.setCreatedTime(System.currentTimeMillis());
        }
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
