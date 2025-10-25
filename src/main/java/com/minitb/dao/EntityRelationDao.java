package com.minitb.dao;

import com.minitb.domain.relation.EntityRelation;
import com.minitb.domain.relation.RelationTypeGroup;
import com.minitb.domain.entity.EntityId;
import com.minitb.service.MiniTbException;

import java.util.List;
import java.util.Optional;

/**
 * 实体关系 DAO 接口
 * 
 * 参考ThingsBoard的RelationDao设计
 */
public interface EntityRelationDao {
    
    /**
     * 保存关系 - 参考ThingsBoard的saveRelation
     */
    EntityRelation saveRelation(EntityRelation relation) throws MiniTbException;
    
    /**
     * 批量保存关系 - 参考ThingsBoard的saveRelations
     */
    List<EntityRelation> saveRelations(List<EntityRelation> relations) throws MiniTbException;
    
    /**
     * 删除关系 - 参考ThingsBoard的deleteRelation
     */
    EntityRelation deleteRelation(EntityRelation relation) throws MiniTbException;
    
    /**
     * 根据源实体查找关系 - 参考ThingsBoard的findAllByFrom
     */
    List<EntityRelation> findAllByFrom(EntityId from, RelationTypeGroup typeGroup) throws MiniTbException;
    
    /**
     * 根据源实体查找所有关系
     */
    List<EntityRelation> findAllByFrom(EntityId from) throws MiniTbException;
    
    /**
     * 根据源实体和关系类型查找关系
     */
    List<EntityRelation> findAllByFromAndType(EntityId from, String relationType, RelationTypeGroup typeGroup) throws MiniTbException;
    
    /**
     * 根据目标实体查找关系 - 参考ThingsBoard的findAllByTo
     */
    List<EntityRelation> findAllByTo(EntityId to, RelationTypeGroup typeGroup) throws MiniTbException;
    
    /**
     * 根据目标实体查找所有关系
     */
    List<EntityRelation> findAllByTo(EntityId to) throws MiniTbException;
    
    /**
     * 根据目标实体和关系类型查找关系
     */
    List<EntityRelation> findAllByToAndType(EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException;
    
    /**
     * 检查关系是否存在 - 参考ThingsBoard的checkRelation
     */
    boolean checkRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException;
    
    /**
     * 获取特定关系 - 参考ThingsBoard的getRelation
     */
    EntityRelation getRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException;
    
    /**
     * 删除源实体的所有出站关系 - 参考ThingsBoard的deleteOutboundRelations
     */
    List<EntityRelation> deleteOutboundRelations(EntityId entity) throws MiniTbException;
    
    /**
     * 删除源实体的特定类型组出站关系
     */
    List<EntityRelation> deleteOutboundRelations(EntityId entity, RelationTypeGroup relationTypeGroup) throws MiniTbException;
    
    /**
     * 删除目标实体的所有入站关系 - 参考ThingsBoard的deleteInboundRelations
     */
    List<EntityRelation> deleteInboundRelations(EntityId entity) throws MiniTbException;
    
    /**
     * 删除目标实体的特定类型组入站关系
     */
    List<EntityRelation> deleteInboundRelations(EntityId entity, RelationTypeGroup relationTypeGroup) throws MiniTbException;
    
    /**
     * 统计关系数量
     */
    long count() throws MiniTbException;
}