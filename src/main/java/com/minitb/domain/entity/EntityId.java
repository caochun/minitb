package com.minitb.domain.entity;

import java.io.Serializable;
import java.util.UUID;

/**
 * 实体ID接口
 * 所有实体ID的统一契约
 * 
 * 借鉴 ThingsBoard 设计：
 * 1. 统一接口抽象
 * 2. 类型安全
 * 3. 支持序列化
 */
public interface EntityId extends Serializable {
    
    /**
     * 空UUID常量（避免使用null）
     */
    UUID NULL_UUID = UUID.fromString("13814000-1dd2-11b2-8080-808080808080");
    
    /**
     * 获取UUID
     */
    UUID getId();
    
    /**
     * 获取实体类型
     */
    EntityType getEntityType();
    
    /**
     * 判断是否为空UUID
     */
    default boolean isNullUid() {
        return NULL_UUID.equals(getId());
    }
}

