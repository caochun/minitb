package com.minitb.dao.impl;

import com.minitb.dao.EntityRelationDao;
import com.minitb.dao.entity.EntityRelationEntity;
import com.minitb.domain.relation.EntityRelation;
import com.minitb.domain.relation.RelationTypeGroup;
import com.minitb.domain.id.EntityId;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 实体关系 DAO 实现
 * 
 * 参考ThingsBoard的JpaRelationDao设计
 */
public class EntityRelationDaoImpl implements EntityRelationDao {
    
    private final Connection connection;
    
    public EntityRelationDaoImpl(Connection connection) {
        this.connection = connection;
    }
    
    @Override
    public EntityRelation saveRelation(EntityRelation relation) throws MiniTbException {
        try {
            EntityRelationEntity entity = EntityRelationEntity.fromDomain(relation);
            
            String sql = "INSERT INTO entity_relation (from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (from_id, to_id, relation_type) DO UPDATE SET " +
                        "from_type = EXCLUDED.from_type, to_type = EXCLUDED.to_type, " +
                        "relation_type_group = EXCLUDED.relation_type_group, version = version + 1, created_time = EXCLUDED.created_time " +
                        "RETURNING from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, entity.getFromId());
                stmt.setString(2, entity.getFromType());
                stmt.setObject(3, entity.getToId());
                stmt.setString(4, entity.getToType());
                stmt.setString(5, entity.getRelationType());
                stmt.setString(6, entity.getRelationTypeGroup());
                stmt.setLong(7, entity.getVersion());
                stmt.setLong(8, entity.getCreatedTime());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToEntityRelation(rs);
                    } else {
                        throw new MiniTbException(MiniTbErrorCode.GENERAL, "保存关系失败");
                    }
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "保存关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> saveRelations(List<EntityRelation> relations) throws MiniTbException {
        List<EntityRelation> savedRelations = new ArrayList<>();
        try {
            connection.setAutoCommit(false);
            
            for (EntityRelation relation : relations) {
                EntityRelation saved = saveRelation(relation);
                savedRelations.add(saved);
            }
            
            connection.commit();
            return savedRelations;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                // 忽略回滚异常
            }
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "批量保存关系失败: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                // 忽略设置自动提交异常
            }
        }
    }
    
    @Override
    public EntityRelation deleteRelation(EntityRelation relation) throws MiniTbException {
        try {
            String sql = "DELETE FROM entity_relation WHERE from_id = ? AND to_id = ? AND relation_type = ? " +
                        "RETURNING from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, relation.getFromId());
                stmt.setObject(2, relation.getToId());
                stmt.setString(3, relation.getRelationType());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToEntityRelation(rs);
                    } else {
                        throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, "关系不存在");
                    }
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "删除关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> findAllByFrom(EntityId from, RelationTypeGroup typeGroup) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE from_id = ? AND from_type = ? AND relation_type_group = ? ORDER BY created_time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, from.getId());
                stmt.setString(2, from.getEntityType().name());
                stmt.setString(3, typeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(mapResultSetToEntityRelation(rs));
                    }
                    return relations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "根据源实体查找关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> findAllByFrom(EntityId from) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE from_id = ? AND from_type = ? ORDER BY created_time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, from.getId());
                stmt.setString(2, from.getEntityType().name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(mapResultSetToEntityRelation(rs));
                    }
                    return relations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "根据源实体查找所有关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> findAllByFromAndType(EntityId from, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE from_id = ? AND from_type = ? AND relation_type = ? AND relation_type_group = ? ORDER BY created_time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, from.getId());
                stmt.setString(2, from.getEntityType().name());
                stmt.setString(3, relationType);
                stmt.setString(4, typeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(mapResultSetToEntityRelation(rs));
                    }
                    return relations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "根据源实体和关系类型查找关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> findAllByTo(EntityId to, RelationTypeGroup typeGroup) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE to_id = ? AND to_type = ? AND relation_type_group = ? ORDER BY created_time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, to.getId());
                stmt.setString(2, to.getEntityType().name());
                stmt.setString(3, typeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(mapResultSetToEntityRelation(rs));
                    }
                    return relations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "根据目标实体查找关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> findAllByTo(EntityId to) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE to_id = ? AND to_type = ? ORDER BY created_time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, to.getId());
                stmt.setString(2, to.getEntityType().name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(mapResultSetToEntityRelation(rs));
                    }
                    return relations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "根据目标实体查找所有关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> findAllByToAndType(EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE to_id = ? AND to_type = ? AND relation_type = ? AND relation_type_group = ? ORDER BY created_time DESC";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, to.getId());
                stmt.setString(2, to.getEntityType().name());
                stmt.setString(3, relationType);
                stmt.setString(4, typeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(mapResultSetToEntityRelation(rs));
                    }
                    return relations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "根据目标实体和关系类型查找关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean checkRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        try {
            String sql = "SELECT 1 FROM entity_relation WHERE from_id = ? AND from_type = ? AND to_id = ? AND to_type = ? AND relation_type = ? AND relation_type_group = ? LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, from.getId());
                stmt.setString(2, from.getEntityType().name());
                stmt.setObject(3, to.getId());
                stmt.setString(4, to.getEntityType().name());
                stmt.setString(5, relationType);
                stmt.setString(6, typeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "检查关系是否存在失败: " + e.getMessage());
        }
    }
    
    @Override
    public EntityRelation getRelation(EntityId from, EntityId to, String relationType, RelationTypeGroup typeGroup) throws MiniTbException {
        try {
            String sql = "SELECT * FROM entity_relation WHERE from_id = ? AND from_type = ? AND to_id = ? AND to_type = ? AND relation_type = ? AND relation_type_group = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, from.getId());
                stmt.setString(2, from.getEntityType().name());
                stmt.setObject(3, to.getId());
                stmt.setString(4, to.getEntityType().name());
                stmt.setString(5, relationType);
                stmt.setString(6, typeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToEntityRelation(rs);
                    } else {
                        throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, "关系不存在");
                    }
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "获取关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> deleteOutboundRelations(EntityId entity) throws MiniTbException {
        try {
            String sql = "DELETE FROM entity_relation WHERE from_id = ? AND from_type = ? " +
                        "RETURNING from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, entity.getId());
                stmt.setString(2, entity.getEntityType().name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> deletedRelations = new ArrayList<>();
                    while (rs.next()) {
                        deletedRelations.add(mapResultSetToEntityRelation(rs));
                    }
                    return deletedRelations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "删除出站关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> deleteOutboundRelations(EntityId entity, RelationTypeGroup relationTypeGroup) throws MiniTbException {
        try {
            String sql = "DELETE FROM entity_relation WHERE from_id = ? AND from_type = ? AND relation_type_group = ? " +
                        "RETURNING from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, entity.getId());
                stmt.setString(2, entity.getEntityType().name());
                stmt.setString(3, relationTypeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> deletedRelations = new ArrayList<>();
                    while (rs.next()) {
                        deletedRelations.add(mapResultSetToEntityRelation(rs));
                    }
                    return deletedRelations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "删除出站关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> deleteInboundRelations(EntityId entity) throws MiniTbException {
        try {
            String sql = "DELETE FROM entity_relation WHERE to_id = ? AND to_type = ? " +
                        "RETURNING from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, entity.getId());
                stmt.setString(2, entity.getEntityType().name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> deletedRelations = new ArrayList<>();
                    while (rs.next()) {
                        deletedRelations.add(mapResultSetToEntityRelation(rs));
                    }
                    return deletedRelations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "删除入站关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<EntityRelation> deleteInboundRelations(EntityId entity, RelationTypeGroup relationTypeGroup) throws MiniTbException {
        try {
            String sql = "DELETE FROM entity_relation WHERE to_id = ? AND to_type = ? AND relation_type_group = ? " +
                        "RETURNING from_id, from_type, to_id, to_type, relation_type, relation_type_group, version, created_time";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, entity.getId());
                stmt.setString(2, entity.getEntityType().name());
                stmt.setString(3, relationTypeGroup.name());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<EntityRelation> deletedRelations = new ArrayList<>();
                    while (rs.next()) {
                        deletedRelations.add(mapResultSetToEntityRelation(rs));
                    }
                    return deletedRelations;
                }
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "删除入站关系失败: " + e.getMessage());
        }
    }
    
    @Override
    public long count() throws MiniTbException {
        try {
            String sql = "SELECT COUNT(*) FROM entity_relation";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "统计关系数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 将ResultSet映射为EntityRelation对象
     */
    private EntityRelation mapResultSetToEntityRelation(ResultSet rs) throws SQLException {
        EntityRelation relation = new EntityRelation();
        relation.setFromId(rs.getObject("from_id", java.util.UUID.class));
        relation.setFromType(rs.getString("from_type"));
        relation.setToId(rs.getObject("to_id", java.util.UUID.class));
        relation.setToType(rs.getString("to_type"));
        relation.setRelationType(rs.getString("relation_type"));
        relation.setTypeGroup(RelationTypeGroup.valueOf(rs.getString("relation_type_group")));
        relation.setCreatedTime(rs.getLong("created_time"));
        return relation;
    }
}