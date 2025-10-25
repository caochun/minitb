package com.minitb.dao;

import com.minitb.domain.relation.EntityRelation;
import com.minitb.domain.relation.RelationTypeGroup;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 实体关系 DAO - SQLite 实现
 * 
 * 注意：EntityRelation 本身已经是简单对象，无需 Entity 层
 */
@Slf4j
public class EntityRelationDao {
    
    private final Connection conn;
    
    public EntityRelationDao() throws SQLException {
        this.conn = DatabaseManager.getConnection();
    }
    
    /**
     * 保存关系
     */
    public EntityRelation save(EntityRelation relation) {
        String sql = """
            INSERT INTO entity_relation 
            (from_id, from_type, to_id, to_type, relation_type, type_group)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, relation.getFromId().toString());
            ps.setString(2, relation.getFromType());
            ps.setString(3, relation.getToId().toString());
            ps.setString(4, relation.getToType());
            ps.setString(5, relation.getRelationType());
            ps.setString(6, relation.getTypeGroup().name());
            
            ps.executeUpdate();
            log.debug("保存关系: {} -> {}", relation.getFromId(), relation.getToId());
            return relation;
            
        } catch (SQLException e) {
            log.error("保存关系失败", e);
            throw new RuntimeException("保存关系失败", e);
        }
    }
    
    /**
     * 删除关系
     */
    public boolean delete(EntityRelation relation) {
        String sql = """
            DELETE FROM entity_relation 
            WHERE from_id = ? AND from_type = ? 
              AND to_id = ? AND to_type = ? 
              AND relation_type = ? AND type_group = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, relation.getFromId().toString());
            ps.setString(2, relation.getFromType());
            ps.setString(3, relation.getToId().toString());
            ps.setString(4, relation.getToType());
            ps.setString(5, relation.getRelationType());
            ps.setString(6, relation.getTypeGroup().name());
            
            int rows = ps.executeUpdate();
            log.debug("删除关系，影响行数: {}", rows);
            return rows > 0;
            
        } catch (SQLException e) {
            log.error("删除关系失败", e);
            return false;
        }
    }
    
    /**
     * 查询从某实体出发的所有关系
     */
    public List<EntityRelation> findByFrom(UUID fromId, RelationTypeGroup typeGroup) {
        List<EntityRelation> relations = new ArrayList<>();
        
        String sql = typeGroup != null ?
            "SELECT * FROM entity_relation WHERE from_id = ? AND type_group = ?" :
            "SELECT * FROM entity_relation WHERE from_id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromId.toString());
            if (typeGroup != null) {
                ps.setString(2, typeGroup.name());
            }
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                relations.add(mapResultSetToRelation(rs));
            }
            
            log.debug("查询出边关系: fromId={}, 结果数={}", fromId, relations.size());
            return relations;
            
        } catch (SQLException e) {
            log.error("查询出边关系失败: {}", fromId, e);
            return relations;
        }
    }
    
    /**
     * 查询指向某实体的所有关系
     */
    public List<EntityRelation> findByTo(UUID toId, RelationTypeGroup typeGroup) {
        List<EntityRelation> relations = new ArrayList<>();
        
        String sql = typeGroup != null ?
            "SELECT * FROM entity_relation WHERE to_id = ? AND type_group = ?" :
            "SELECT * FROM entity_relation WHERE to_id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toId.toString());
            if (typeGroup != null) {
                ps.setString(2, typeGroup.name());
            }
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                relations.add(mapResultSetToRelation(rs));
            }
            
            log.debug("查询入边关系: toId={}, 结果数={}", toId, relations.size());
            return relations;
            
        } catch (SQLException e) {
            log.error("查询入边关系失败: {}", toId, e);
            return relations;
        }
    }
    
    /**
     * 删除实体的所有关系
     */
    public int deleteByEntityId(UUID entityId) {
        String sql = """
            DELETE FROM entity_relation 
            WHERE from_id = ? OR to_id = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityId.toString());
            ps.setString(2, entityId.toString());
            
            int rows = ps.executeUpdate();
            log.debug("删除实体所有关系: entityId={}, 影响行数={}", entityId, rows);
            return rows;
            
        } catch (SQLException e) {
            log.error("删除实体关系失败: {}", entityId, e);
            return 0;
        }
    }
    
    /**
     * 统计关系数量
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM entity_relation";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (SQLException e) {
            log.error("统计关系数量失败", e);
        }
        
        return 0;
    }
    
    /**
     * ResultSet → EntityRelation 映射
     */
    private EntityRelation mapResultSetToRelation(ResultSet rs) throws SQLException {
        return new EntityRelation(
            UUID.fromString(rs.getString("from_id")),
            rs.getString("from_type"),
            UUID.fromString(rs.getString("to_id")),
            rs.getString("to_type"),
            rs.getString("relation_type"),
            RelationTypeGroup.valueOf(rs.getString("type_group"))
        );
    }
}

