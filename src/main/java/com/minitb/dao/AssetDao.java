package com.minitb.dao;

import com.minitb.domain.entity.Asset;
import com.minitb.domain.entity.AssetId;
import com.minitb.dao.entity.AssetEntity;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 资产 DAO - SQLite 实现
 */
@Slf4j
public class AssetDao {
    
    private final Connection conn;
    
    public AssetDao() throws SQLException {
        this.conn = DatabaseManager.getConnection();
    }
    
    /**
     * 保存资产
     */
    public Asset save(Asset asset) {
        AssetEntity entity = AssetEntity.fromDomain(asset);
        
        String sql = """
            INSERT INTO asset (id, name, type, label, created_time)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                type = excluded.type,
                label = excluded.label
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getId());
            ps.setString(2, entity.getName());
            ps.setString(3, entity.getType());
            ps.setString(4, entity.getLabel());
            ps.setLong(5, entity.getCreatedTime());
            
            ps.executeUpdate();
            log.debug("保存资产: {}", asset.getName());
            return asset;
            
        } catch (SQLException e) {
            log.error("保存资产失败: {}", asset.getName(), e);
            throw new RuntimeException("保存资产失败", e);
        }
    }
    
    /**
     * 根据 ID 查找资产
     */
    public Optional<Asset> findById(AssetId id) {
        String sql = "SELECT * FROM asset WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                AssetEntity entity = mapResultSetToEntity(rs);
                return Optional.of(entity.toDomain());
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("查询资产失败: {}", id, e);
            return Optional.empty();
        }
    }
    
    /**
     * 查找所有资产
     */
    public List<Asset> findAll() {
        List<Asset> assets = new ArrayList<>();
        String sql = "SELECT * FROM asset ORDER BY created_time DESC";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                AssetEntity entity = mapResultSetToEntity(rs);
                assets.add(entity.toDomain());
            }
            
            log.debug("查询到 {} 个资产", assets.size());
            return assets;
            
        } catch (SQLException e) {
            log.error("查询所有资产失败", e);
            return assets;
        }
    }
    
    /**
     * 根据类型查找资产
     */
    public List<Asset> findByType(String type) {
        List<Asset> assets = new ArrayList<>();
        String sql = "SELECT * FROM asset WHERE type = ? ORDER BY created_time DESC";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                AssetEntity entity = mapResultSetToEntity(rs);
                assets.add(entity.toDomain());
            }
            
            log.debug("查询到 {} 个 {} 类型资产", assets.size(), type);
            return assets;
            
        } catch (SQLException e) {
            log.error("根据类型查询资产失败: {}", type, e);
            return assets;
        }
    }
    
    /**
     * 删除资产
     */
    public boolean delete(AssetId id) {
        String sql = "DELETE FROM asset WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            int rows = ps.executeUpdate();
            
            log.debug("删除资产: {}, 影响行数: {}", id, rows);
            return rows > 0;
            
        } catch (SQLException e) {
            log.error("删除资产失败: {}", id, e);
            return false;
        }
    }
    
    /**
     * 统计资产数量
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM asset";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (SQLException e) {
            log.error("统计资产数量失败", e);
        }
        
        return 0;
    }
    
    /**
     * ResultSet → AssetEntity 映射
     */
    private AssetEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return AssetEntity.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .type(rs.getString("type"))
                .label(rs.getString("label"))
                .createdTime(rs.getLong("created_time"))
                .build();
    }
}

