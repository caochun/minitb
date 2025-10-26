package com.minitb.domain.id;

import java.io.Serializable;
import java.util.UUID;

/**
 * 基于UUID的实体ID抽象基类
 * 
 * 借鉴 ThingsBoard 设计：
 * 1. 不可变ID（final）
 * 2. 缓存hashCode以提高性能
 * 3. 类型安全的equals
 * 4. 线程安全
 */
public abstract class UUIDBased implements EntityId, Serializable {
    
    private static final long serialVersionUID = 1L;
    
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
    protected UUIDBased() {
        this(UUID.randomUUID());
    }
    
    /**
     * 带参数构造器：使用指定UUID
     */
    protected UUIDBased(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        this.id = id;
    }
    
    @Override
    public UUID getId() {
        return id;
    }
    
    /**
     * 获取实体类型 - 子类必须实现
     */
    @Override
    public abstract EntityType getEntityType();
    
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
        UUIDBased other = (UUIDBased) obj;
        return id.equals(other.id);
    }
    
    @Override
    public String toString() {
        return id.toString();
    }
}

