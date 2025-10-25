package com.minitb.dao.entity;

import com.minitb.common.entity.Device;
import com.minitb.common.entity.DeviceId;
import com.minitb.common.entity.DeviceProfileId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备实体类 - 数据库映射
 * 
 * 职责：
 * 1. 映射数据库表结构
 * 2. 处理类型转换（DeviceId → String, DeviceProfileId → String）
 * 3. 提供 Domain ↔ Entity 转换方法
 * 
 * 设计理念（借鉴 ThingsBoard）：
 * - 使用原生类型（String, Long）便于数据库操作
 * - 与业务对象 Device 分离，降低耦合
 * - Entity 层专注于持久化，Domain 层专注于业务逻辑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEntity {
    
    /**
     * 设备ID（数据库存储为字符串）
     */
    private String id;
    
    /**
     * 设备名称
     */
    private String name;
    
    /**
     * 设备类型
     */
    private String type;
    
    /**
     * 访问令牌（用于认证）
     */
    private String accessToken;
    
    /**
     * 设备配置文件ID（数据库存储为字符串）
     */
    private String deviceProfileId;
    
    /**
     * 创建时间（Unix 时间戳，毫秒）
     */
    private Long createdTime;
    
    // ==================== 转换方法 ====================
    
    /**
     * Domain → Entity 转换
     * 保存到数据库前调用
     * 
     * @param device 业务领域对象
     * @return 数据库实体对象
     */
    public static DeviceEntity fromDomain(Device device) {
        if (device == null) {
            return null;
        }
        
        return DeviceEntity.builder()
                .id(device.getId() != null ? device.getId().toString() : null)
                .name(device.getName())
                .type(device.getType())
                .accessToken(device.getAccessToken())
                .deviceProfileId(device.getDeviceProfileId() != null ? 
                    device.getDeviceProfileId().toString() : null)
                .createdTime(device.getCreatedTime())
                .build();
    }
    
    /**
     * Entity → Domain 转换
     * 从数据库查询后调用
     * 
     * @return 业务领域对象
     */
    public Device toDomain() {
        return Device.builder()
                .id(id != null ? DeviceId.fromString(id) : null)
                .name(name)
                .type(type)
                .accessToken(accessToken)
                .deviceProfileId(deviceProfileId != null ? 
                    DeviceProfileId.fromString(deviceProfileId) : null)
                .createdTime(createdTime != null ? createdTime : 0L)
                .build();
    }
}

