package com.minitb.relation;

import com.minitb.common.entity.DeviceId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 实体关系
 * 
 * 用于表示实体之间的关系，例如:
 * - Device → Asset (Contains)
 * - Asset → Customer (Manages)
 * - Dashboard → Device (Uses)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityRelation {
    
    // 常用关系类型常量
    public static final String CONTAINS_TYPE = "Contains";
    public static final String MANAGES_TYPE = "Manages";
    public static final String USES_TYPE = "Uses";
    public static final String BELONGS_TO_TYPE = "BelongsTo";
    
    /**
     * 关系起点实体ID
     */
    private UUID fromId;
    
    /**
     * 关系起点实体类型
     */
    private String fromType;
    
    /**
     * 关系终点实体ID
     */
    private UUID toId;
    
    /**
     * 关系终点实体类型
     */
    private String toType;
    
    /**
     * 关系类型 (Contains, Manages, Uses等)
     */
    private String relationType;
    
    /**
     * 关系类型组
     */
    private RelationTypeGroup typeGroup;
    
    /**
     * 创建时间
     */
    private long createdTime;
    
    /**
     * 构造方法 - 创建通用类型组的关系
     */
    public EntityRelation(UUID fromId, String fromType, UUID toId, String toType, String relationType) {
        this(fromId, fromType, toId, toType, relationType, RelationTypeGroup.COMMON);
    }
    
    /**
     * 构造方法 - 指定类型组
     */
    public EntityRelation(UUID fromId, String fromType, UUID toId, String toType, 
                          String relationType, RelationTypeGroup typeGroup) {
        this.fromId = fromId;
        this.fromType = fromType;
        this.toId = toId;
        this.toType = toType;
        this.relationType = relationType;
        this.typeGroup = typeGroup;
        this.createdTime = System.currentTimeMillis();
    }
    
    /**
     * 生成关系的唯一键
     */
    public String getKey() {
        return String.format("%s_%s_%s_%s_%s_%s", 
            fromId, fromType, toId, toType, relationType, typeGroup);
    }
    
    /**
     * 判断是否是反向关系
     */
    public boolean isReverse(EntityRelation other) {
        return this.fromId.equals(other.toId) && 
               this.toId.equals(other.fromId) &&
               this.relationType.equals(other.relationType) &&
               this.typeGroup.equals(other.typeGroup);
    }
}

