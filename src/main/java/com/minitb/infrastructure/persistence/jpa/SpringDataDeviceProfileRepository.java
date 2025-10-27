package com.minitb.infrastructure.persistence.jpa;

import com.minitb.infrastructure.persistence.jpa.entity.DeviceProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for DeviceProfileEntity
 * Spring Data JPA 设备配置实体仓储（技术实现）
 * 
 * 这是技术层面的接口，不应被 Application 层直接依赖
 * package-private: 只在 infrastructure.persistence.repository 包内可见
 */
interface SpringDataDeviceProfileRepository extends JpaRepository<DeviceProfileEntity, UUID> {
    
    /**
     * 根据名称查找设备配置文件
     * 
     * @param name 配置文件名称
     * @return 配置文件实体（可选）
     */
    Optional<DeviceProfileEntity> findByName(String name);
    
    /**
     * 检查名称是否存在
     * 
     * @param name 配置文件名称
     * @return 是否存在
     */
    boolean existsByName(String name);
}

