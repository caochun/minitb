package com.minitb.application.service.impl;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.DeviceProfileRepository;
import com.minitb.domain.device.DeviceRepository;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 设备服务实现（Application 层）
 * 
 * 职责：
 * - 编排用例（Use Case Orchestration）
 * - 协调 Domain Repository 和领域对象
 * - 实现应用层的业务逻辑
 * 
 * 依赖：
 * - 只依赖 Domain 层的接口（DeviceRepository, DeviceProfileRepository）
 * - 不依赖 Infrastructure 层的实现细节
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceServiceImpl implements DeviceService {
    
    // ✅ 只依赖 Domain 层的接口
    private final DeviceRepository deviceRepository;
    private final DeviceProfileRepository deviceProfileRepository;
    
    // ==================== Device 管理 ====================
    
    @Override
    @Transactional
    public Device save(Device device) {
        // 确保 Device 有 ID
        if (device.getId() == null) {
            device.setId(DeviceId.random());
        }
        
        // 确保有创建时间
        if (device.getCreatedTime() == 0) {
            device.setCreatedTime(System.currentTimeMillis());
        }
        
        // ✅ 直接调用 Domain Repository，Adapter 负责转换
        Device saved = deviceRepository.save(device);
        
        log.debug("保存设备: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Device> findById(DeviceId deviceId) {
        return deviceRepository.findById(deviceId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Device> findByAccessToken(String accessToken) {
        return deviceRepository.findByAccessToken(accessToken);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Device> findAll() {
        return deviceRepository.findAll();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Device> findByDeviceProfileId(DeviceProfileId deviceProfileId) {
        // TODO: 需要在 DeviceRepository 接口中添加此方法
        log.warn("findByDeviceProfileId not yet supported in Domain Repository");
        return List.of();
    }
    
    @Override
    @Transactional
    public void delete(DeviceId deviceId) {
        deviceRepository.deleteById(deviceId);
        log.debug("删除设备: id={}", deviceId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsByAccessToken(String accessToken) {
        // TODO: 需要在 DeviceRepository 接口中添加此方法
        return findByAccessToken(accessToken).isPresent();
    }
    
    // ==================== DeviceProfile 管理 ====================
    
    @Override
    @Transactional
    public DeviceProfile saveProfile(DeviceProfile profile) {
        // 确保 DeviceProfile 有 ID
        if (profile.getId() == null) {
            profile.setId(DeviceProfileId.random());
        }
        
        // 确保有创建时间
        if (profile.getCreatedTime() == 0) {
            profile.setCreatedTime(System.currentTimeMillis());
        }
        
        // ✅ 直接调用 Domain Repository，Adapter 负责转换
        DeviceProfile saved = deviceProfileRepository.save(profile);
        
        log.debug("保存设备配置文件: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceProfile> findProfileById(DeviceProfileId profileId) {
        return deviceProfileRepository.findById(profileId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceProfile> findProfileByName(String name) {
        // TODO: 需要在 DeviceProfileRepository 接口中添加此方法
        log.warn("findProfileByName not yet supported in Domain Repository");
        return Optional.empty();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<DeviceProfile> findAllProfiles() {
        return deviceProfileRepository.findAll();
    }
    
    @Override
    @Transactional
    public void deleteProfile(DeviceProfileId profileId) {
        deviceProfileRepository.deleteById(profileId);
        log.debug("删除设备配置文件: id={}", profileId);
    }
}

