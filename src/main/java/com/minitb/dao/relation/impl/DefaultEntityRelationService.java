package com.minitb.dao.relation.impl;

import com.minitb.dao.relation.EntityRelationService;
import com.minitb.dao.EntityRelationDao;
import com.minitb.domain.relation.EntityRelation;
import com.minitb.domain.relation.RelationTypeGroup;
import com.minitb.domain.id.EntityId;
import com.minitb.dao.common.AbstractEntityService;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;

import java.util.List;

/**
 * 实体关系服务默认实现
 * 
 * 参考ThingsBoard的BaseRelationService设计
 */
public class DefaultEntityRelationService extends AbstractEntityService implements EntityRelationService {
    
    private final EntityRelationDao entityRelationDao;
    
    public DefaultEntityRelationService(EntityRelationDao entityRelationDao) {
        this.entityRelationDao = entityRelationDao;
    }
    
    @Override
    public EntityRelation saveRelation(EntityRelation relation) throws MiniTbException {
        validateEntityRelation(relation);
        return entityRelationDao.saveRelation(relation);
    }
    
    @Override
    public List<EntityRelation> saveRelations(List<EntityRelation> relations) throws MiniTbException {
        if (relations == null || relations.isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系列表不能为空");
        }
        
        for (EntityRelation relation : relations) {
            validateEntityRelation(relation);
        }
        
        return entityRelationDao.saveRelations(relations);
    }
    
    @Override
    public EntityRelation deleteRelation(EntityRelation relation) throws MiniTbException {
        validateEntityRelation(relation);
        return entityRelationDao.deleteRelation(relation);
    }
    
    @Override
    public List<EntityRelation> findAllByFrom(EntityId from, RelationTypeGroup typeGroup) throws MiniTbException {
        validateEntityId(from);
        if (typeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.findAllByFrom(from, typeGroup);
    }
    
    @Override
    public List<EntityRelation> findAllByFrom(EntityId from) throws MiniTbException {
        validateEntityId(from);
        return entityRelationDao.findAllByFrom(from);
    }
    
    @Override
    public List<EntityRelation> findAllByFromAndType(EntityId from, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        validateEntityId(from);
        validateRelationType(relationType);
        if (typeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.findAllByFromAndType(from, relationType, typeGroup);
    }
    
    @Override
    public List<EntityRelation> findAllByTo(EntityId to, RelationTypeGroup typeGroup) throws MiniTbException {
        validateEntityId(to);
        if (typeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.findAllByTo(to, typeGroup);
    }
    
    @Override
    public List<EntityRelation> findAllByTo(EntityId to) throws MiniTbException {
        validateEntityId(to);
        return entityRelationDao.findAllByTo(to);
    }
    
    @Override
    public List<EntityRelation> findAllByToAndType(EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        validateEntityId(to);
        validateRelationType(relationType);
        if (typeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.findAllByToAndType(to, relationType, typeGroup);
    }
    
    @Override
    public boolean checkRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        validateEntityId(from);
        validateEntityId(to);
        validateRelationType(relationType);
        if (typeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.checkRelation(from, to, relationType, typeGroup);
    }
    
    @Override
    public EntityRelation getRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        validateEntityId(from);
        validateEntityId(to);
        validateRelationType(relationType);
        if (typeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.getRelation(from, to, relationType, typeGroup);
    }
    
    @Override
    public List<EntityRelation> deleteOutboundRelations(EntityId entity) throws MiniTbException {
        validateEntityId(entity);
        return entityRelationDao.deleteOutboundRelations(entity);
    }
    
    @Override
    public List<EntityRelation> deleteOutboundRelations(EntityId entity, RelationTypeGroup relationTypeGroup) throws MiniTbException {
        validateEntityId(entity);
        if (relationTypeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.deleteOutboundRelations(entity, relationTypeGroup);
    }
    
    @Override
    public List<EntityRelation> deleteInboundRelations(EntityId entity) throws MiniTbException {
        validateEntityId(entity);
        return entityRelationDao.deleteInboundRelations(entity);
    }
    
    @Override
    public List<EntityRelation> deleteInboundRelations(EntityId entity, RelationTypeGroup relationTypeGroup) throws MiniTbException {
        validateEntityId(entity);
        if (relationTypeGroup == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        return entityRelationDao.deleteInboundRelations(entity, relationTypeGroup);
    }
    
    @Override
    public long count() throws MiniTbException {
        return entityRelationDao.count();
    }
    
    /**
     * 验证实体关系
     */
    private void validateEntityRelation(EntityRelation relation) throws MiniTbException {
        if (relation == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系不能为空");
        }
        
        if (relation.getFromId() == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "源实体ID不能为空");
        }
        
        if (relation.getToId() == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "目标实体ID不能为空");
        }
        
        validateRelationType(relation.getRelationType());
        
        if (relation.getTypeGroup() == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型组不能为空");
        }
        
        // 检查不能自引用
        if (relation.getFromId().equals(relation.getToId())) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系不能自引用");
        }
    }
    
    /**
     * 验证关系类型
     */
    private void validateRelationType(String relationType) throws MiniTbException {
        if (relationType == null || relationType.trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "关系类型不能为空");
        }
    }
}