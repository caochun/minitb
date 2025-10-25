package com.minitb.dao.impl;

import com.minitb.dao.BaseDaoImpl;
import com.minitb.dao.AssetDao;
import com.minitb.domain.entity.Asset;
import com.minitb.domain.entity.AssetId;
import com.minitb.dao.entity.AssetEntity;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MiniTB资产DAO实现
 * 基于SQLite的资产数据访问实现
 * 负责领域模型与实体模型的转换
 */
@Slf4j
public class AssetDaoImpl extends BaseDaoImpl<Asset, AssetId> implements AssetDao {
    
    public AssetDaoImpl(Connection connection) {
        super(connection, "assets", "id");
    }
    
    @Override
    protected Asset insert(Asset asset) throws SQLException {
        // 1. 领域模型 -> 实体模型
        AssetEntity entity = AssetEntity.fromDomain(asset);
        
        String sql = """
            INSERT INTO assets (id, name, type, label, created_time)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entity.getId());
            stmt.setString(2, entity.getName());
            stmt.setString(3, entity.getType());
            stmt.setString(4, entity.getLabel());
            stmt.setLong(5, entity.getCreatedTime());
            
            stmt.executeUpdate();
            return asset;
        }
    }
    
    @Override
    protected Asset update(Asset asset) throws SQLException {
        // 1. 领域模型 -> 实体模型
        AssetEntity entity = AssetEntity.fromDomain(asset);
        
        String sql = """
            UPDATE assets SET name = ?, type = ?, label = ?
            WHERE id = ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entity.getName());
            stmt.setString(2, entity.getType());
            stmt.setString(3, entity.getLabel());
            stmt.setString(4, entity.getId());
            
            stmt.executeUpdate();
            return asset;
        }
    }
    
    @Override
    protected Asset mapRowToEntity(ResultSet rs) throws SQLException {
        // 1. 从ResultSet创建实体模型
        AssetEntity entity = new AssetEntity();
        entity.setId(rs.getString("id"));
        entity.setName(rs.getString("name"));
        entity.setType(rs.getString("type"));
        entity.setLabel(rs.getString("label"));
        entity.setCreatedTime(rs.getLong("created_time"));
        
        // 2. 实体模型 -> 领域模型
        return entity.toDomain();
    }
    
    @Override
    protected AssetId getEntityId(Asset asset) {
        return asset.getId();
    }
    
    // ==================== 业务方法实现 ====================
    
    @Override
    public List<Asset> findByType(String type) {
        try {
            String sql = "SELECT * FROM assets WHERE type = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Asset> assets = new ArrayList<>();
                    while (rs.next()) {
                        assets.add(mapRowToEntity(rs));
                    }
                    return assets;
                }
            }
        } catch (SQLException e) {
            log.error("根据资产类型查找资产失败: type={}", type, e);
            throw new RuntimeException("查找资产失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByType(String type) {
        try {
            String sql = "SELECT COUNT(*) FROM assets WHERE type = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计资产数量失败: type={}", type, e);
            throw new RuntimeException("统计资产数量失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Asset> findByStatus(String status) {
        try {
            String sql = "SELECT * FROM assets WHERE status = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Asset> assets = new ArrayList<>();
                    while (rs.next()) {
                        assets.add(mapRowToEntity(rs));
                    }
                    return assets;
                }
            }
        } catch (SQLException e) {
            log.error("根据资产状态查找资产失败: status={}", status, e);
            throw new RuntimeException("查找资产失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByStatus(String status) {
        try {
            String sql = "SELECT COUNT(*) FROM assets WHERE status = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计资产数量失败: status={}", status, e);
            throw new RuntimeException("统计资产数量失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Asset> findByLabel(String label) {
        try {
            String sql = "SELECT * FROM assets WHERE label = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, label);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Asset> assets = new ArrayList<>();
                    while (rs.next()) {
                        assets.add(mapRowToEntity(rs));
                    }
                    return assets;
                }
            }
        } catch (SQLException e) {
            log.error("根据资产标签查找资产失败: label={}", label, e);
            throw new RuntimeException("查找资产失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Asset> findByNameLike(String namePattern) {
        try {
            String sql = "SELECT * FROM assets WHERE name LIKE ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, "%" + namePattern + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Asset> assets = new ArrayList<>();
                    while (rs.next()) {
                        assets.add(mapRowToEntity(rs));
                    }
                    return assets;
                }
            }
        } catch (SQLException e) {
            log.error("根据资产名称模糊查找失败: namePattern={}", namePattern, e);
            throw new RuntimeException("查找资产失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Asset> findByParentAssetId(String parentAssetId) {
        try {
            String sql = "SELECT * FROM assets WHERE parent_asset_id = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, parentAssetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Asset> assets = new ArrayList<>();
                    while (rs.next()) {
                        assets.add(mapRowToEntity(rs));
                    }
                    return assets;
                }
            }
        } catch (SQLException e) {
            log.error("根据父资产ID查找子资产失败: parentAssetId={}", parentAssetId, e);
            throw new RuntimeException("查找资产失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByParentAssetId(String parentAssetId) {
        try {
            String sql = "SELECT COUNT(*) FROM assets WHERE parent_asset_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, parentAssetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计子资产数量失败: parentAssetId={}", parentAssetId, e);
            throw new RuntimeException("统计资产数量失败: " + e.getMessage());
        }
    }
    
    @Override
    public AssetStatistics getAssetStatistics() {
        try {
            String sql = """
                SELECT 
                    COUNT(*) as total_count,
                    SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_count,
                    SUM(CASE WHEN status = 'INACTIVE' THEN 1 ELSE 0 END) as inactive_count,
                    SUM(CASE WHEN status = 'ONLINE' THEN 1 ELSE 0 END) as online_count,
                    SUM(CASE WHEN status = 'OFFLINE' THEN 1 ELSE 0 END) as offline_count
                FROM assets
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new AssetStatistics(
                            rs.getLong("total_count"),
                            rs.getLong("active_count"),
                            rs.getLong("inactive_count"),
                            rs.getLong("online_count"),
                            rs.getLong("offline_count")
                        );
                    }
                }
            }
            return new AssetStatistics(0, 0, 0, 0, 0);
        } catch (SQLException e) {
            log.error("获取资产统计信息失败", e);
            throw new RuntimeException("获取资产统计信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean existsByName(String name) {
        try {
            String sql = "SELECT 1 FROM assets WHERE name = ? LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("检查资产名称是否存在失败", e);
            throw new RuntimeException("检查资产名称是否存在失败: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<Asset> findByName(String name) {
        try {
            String sql = "SELECT * FROM assets WHERE name = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToAsset(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            log.error("根据名称查找资产失败", e);
            throw new RuntimeException("根据名称查找资产失败: " + e.getMessage());
        }
    }
    
    /**
     * 将ResultSet映射为Asset对象
     */
    private Asset mapResultSetToAsset(ResultSet rs) throws SQLException {
        AssetId id = AssetId.fromString(rs.getString("id"));
        String name = rs.getString("name");
        String type = rs.getString("type");
        String status = rs.getString("status");
        boolean online = rs.getBoolean("online");
        long createdTime = rs.getLong("created_time");
        
        Asset asset = new Asset(name, type);
        // 注意：这里需要根据实际的Asset构造函数调整
        return asset;
    }
}
