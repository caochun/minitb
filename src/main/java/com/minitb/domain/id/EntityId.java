package com.minitb.domain.id;

import java.io.Serializable;
import java.util.UUID;

/**
 * 实体ID抽象基类
 * 
 * 设计原则：
 * 1. 所有实体ID都基于UUID
 * 2. 不可变（线程安全）
 * 3. 缓存hashCode以提高性能
 * 4. 类型安全的equals
 */
public abstract class EntityId implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 空UUID常量（避免使用null）
     */
    public static final UUID NULL_UUID = UUID.fromString("13814000-1dd2-11b2-8080-808080808080");
    
    /**
     * 缓存hashCode（瞬态字段，不参与序列化）
     */
    private transient int hash;
    
    /**
     * 不可变的UUID（线程安全）
     */
    private final UUID id;
    
    /**
     * 默认构造器：生成随机UUID
     */
    protected EntityId() {
        this(UUID.randomUUID());
    }
    
    /**
     * 带参数构造器：使用指定UUID
     */
    protected EntityId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        this.id = id;
    }
    
    /**
     * 获取UUID
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * 获取实体类型 - 子类必须实现
     */
    public abstract EntityType getEntityType();
    
    /**
     * 判断是否为空UUID
     */
    public boolean isNullUid() {
        return NULL_UUID.equals(id);
    }
    
    @Override
    public int hashCode() {
        // 缓存hashCode，避免重复计算
        if (hash == 0) {
            final int prime = 31;
            int result = 1;
            hash = prime * result + id.hashCode();
        }
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        EntityId other = (EntityId) obj;
        return id.equals(other.id);
    }
    
    @Override
    public String toString() {
        return id.toString();
    }
}

