package com.minitb.domain.relation;

import com.minitb.domain.id.EntityId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体关系
 * 
 * 用于表示实体之间的关系，例如:
 * - Device → Asset (Contains)
 * - Asset → Customer (Manages)
 * 
 * 设计说明：
 * - 使用 EntityId 强类型，确保类型安全
 * - EntityId 包含 UUID 和 EntityType，避免类型不匹配
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityRelation {
    
    // 常用关系类型常量
    public static final String CONTAINS = "Contains";
    public static final String MANAGES = "Manages";
    public static final String USES = "Uses";
    public static final String BELONGS_TO = "BelongsTo";
    
    /**
     * 关系起点实体
     */
    private EntityId from;
    
    /**
     * 关系终点实体
     */
    private EntityId to;
    
    /**
     * 关系类型 (Contains, Manages, Uses等)
     */
    private String type;
    
    /**
     * 创建时间
     */
    private long createdTime;
    
    /**
     * 便捷构造方法
     */
    public EntityRelation(EntityId from, EntityId to, String type) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.createdTime = System.currentTimeMillis();
    }
    
    /**
     * 生成关系的唯一键
     */
    public String getKey() {
        return String.format("%s_%s_%s_%s_%s", 
            from.getId(), from.getEntityType(), 
            to.getId(), to.getEntityType(), 
            type);
    }
    
    /**
     * 判断是否是反向关系
     */
    public boolean isReverse(EntityRelation other) {
        return this.from.equals(other.to) && 
               this.to.equals(other.from) &&
               this.type.equals(other.type);
    }
}

