package com.minitb.infrastructure.persistence.jpa;

import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceRepository;
import com.minitb.domain.id.DeviceId;
import com.minitb.infrastructure.persistence.jpa.entity.DeviceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA Adapter for Device Repository
 * 设备仓储的 JPA 适配器
 * 
 * 实现 Domain 层的 DeviceRepository 接口，将领域操作转换为 JPA 操作
 * 
 * 仅当 minitb.storage.type=jpa 时生效
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "jpa", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class JpaDeviceRepositoryAdapter implements DeviceRepository {
    
    private final SpringDataDeviceRepository jpaRepository;
    
    @Override
    public Device save(Device device) {
        log.debug("Saving device: {}", device.getId());
        
        DeviceEntity entity = DeviceEntity.fromDomain(device);
        DeviceEntity saved = jpaRepository.save(entity);
        
        return saved.toDomain();
    }
    
    @Override
    public Optional<Device> findById(DeviceId deviceId) {
        log.debug("Finding device by id: {}", deviceId);
        
        return jpaRepository.findById(deviceId.getId())
                .map(DeviceEntity::toDomain);
    }
    
    @Override
    public Optional<Device> findByAccessToken(String accessToken) {
        log.debug("Finding device by access token: {}", accessToken);
        
        return jpaRepository.findByAccessToken(accessToken)
                .map(DeviceEntity::toDomain);
    }
    
    @Override
    public List<Device> findAll() {
        log.debug("Finding all devices");
        
        return jpaRepository.findAll().stream()
                .map(DeviceEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteById(DeviceId deviceId) {
        log.debug("Deleting device by id: {}", deviceId);
        
        jpaRepository.deleteById(deviceId.getId());
    }
}


