package com.minitb.infrastructure.persistence.jpa;

import com.minitb.infrastructure.persistence.jpa.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for DeviceEntity
 * Spring Data JPA 设备实体仓储（技术实现）
 * 
 * 这是技术层面的接口，不应被 Application 层直接依赖
 * package-private: 只在 infrastructure.persistence.repository 包内可见
 */
interface SpringDataDeviceRepository extends JpaRepository<DeviceEntity, UUID> {
    
    /**
     * 根据访问令牌查找设备
     * 
     * @param accessToken 访问令牌
     * @return 设备实体（可选）
     */
    Optional<DeviceEntity> findByAccessToken(String accessToken);
    
    /**
     * 根据设备配置文件ID查找所有设备
     * 
     * @param deviceProfileId 设备配置文件ID
     * @return 设备实体列表
     */
    List<DeviceEntity> findByDeviceProfileId(UUID deviceProfileId);
    
    /**
     * 检查访问令牌是否存在
     * 
     * @param accessToken 访问令牌
     * @return 是否存在
     */
    boolean existsByAccessToken(String accessToken);
}

