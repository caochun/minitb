package com.minitb.service.device;

import com.minitb.dao.DeviceDao;
import com.minitb.domain.entity.Device;
import com.minitb.domain.entity.DeviceId;
import com.minitb.domain.entity.DeviceProfileId;
import com.minitb.service.AbstractEntityService;
import com.minitb.service.MiniTbException;
import com.minitb.service.MiniTbErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备服务默认实现
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultDeviceService extends AbstractEntityService implements DeviceService {

    private final DeviceDao deviceDao;

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
}
