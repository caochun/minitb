package com.minitb.dao.entity;

import com.minitb.domain.relation.EntityRelation;
import com.minitb.domain.relation.RelationTypeGroup;
import lombok.Data;

import java.util.UUID;

/**
 * 实体关系数据库实体
 * 
 * 对应数据库表: entity_relation
 * 主键: (from_id, to_id, relation_type)
 */
@Data
public class EntityRelationEntity {
    
    private UUID fromId;
    private String fromType;
    private UUID toId;
    private String toType;
    private String relationType;
    private String relationTypeGroup;
    private Long version;
    private String additionalInfo;
    private Long createdTime;
    
    public EntityRelationEntity() {
        super();
    }
    
    public EntityRelationEntity(EntityRelation relation) {
        this.fromId = relation.getFromId();
        this.fromType = relation.getFromType();
        this.toId = relation.getToId();
        this.toType = relation.getToType();
        this.relationType = relation.getRelationType();
        this.relationTypeGroup = relation.getTypeGroup().name();
        this.version = 0L; // 简化版本控制
        this.additionalInfo = null; // 简化附加信息
        this.createdTime = relation.getCreatedTime();
    }
    
    /**
     * 转换为领域对象
     */
    public EntityRelation toDomain() {
        EntityRelation relation = new EntityRelation();
        relation.setFromId(this.fromId);
        relation.setFromType(this.fromType);
        relation.setToId(this.toId);
        relation.setToType(this.toType);
        relation.setRelationType(this.relationType);
        relation.setTypeGroup(RelationTypeGroup.valueOf(this.relationTypeGroup));
        relation.setCreatedTime(this.createdTime);
        return relation;
    }
    
    /**
     * 从领域对象创建
     */
    public static EntityRelationEntity fromDomain(EntityRelation relation) {
        return new EntityRelationEntity(relation);
    }
}
