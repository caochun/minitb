package com.minitb.infrastructure.persistence.jpa;

import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.DeviceProfileRepository;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.infrastructure.persistence.jpa.entity.DeviceProfileEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA Adapter for DeviceProfile Repository
 * 设备配置仓储的 JPA 适配器
 * 
 * 实现 Domain 层的 DeviceProfileRepository 接口，将领域操作转换为 JPA 操作
 * 
 * 仅当 minitb.storage.type=jpa 时生效
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "jpa", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class JpaDeviceProfileRepositoryAdapter implements DeviceProfileRepository {
    
    private final SpringDataDeviceProfileRepository jpaRepository;
    
    @Override
    public DeviceProfile save(DeviceProfile deviceProfile) {
        log.debug("Saving device profile: {}", deviceProfile.getId());
        
        DeviceProfileEntity entity = DeviceProfileEntity.fromDomain(deviceProfile);
        DeviceProfileEntity saved = jpaRepository.save(entity);
        
        return saved.toDomain();
    }
    
    @Override
    public Optional<DeviceProfile> findById(DeviceProfileId profileId) {
        log.debug("Finding device profile by id: {}", profileId);
        
        return jpaRepository.findById(profileId.getId())
                .map(DeviceProfileEntity::toDomain);
    }
    
    @Override
    public Optional<DeviceProfile> findByName(String name) {
        log.debug("Finding device profile by name: {}", name);
        
        return jpaRepository.findByName(name)
                .map(DeviceProfileEntity::toDomain);
    }
    
    @Override
    public List<DeviceProfile> findAll() {
        log.debug("Finding all device profiles");
        
        return jpaRepository.findAll().stream()
                .map(DeviceProfileEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteById(DeviceProfileId profileId) {
        log.debug("Deleting device profile by id: {}", profileId);
        
        jpaRepository.deleteById(profileId.getId());
    }
}

