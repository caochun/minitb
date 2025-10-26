package com.minitb.dao.impl;

import com.minitb.dao.BaseDaoImpl;
import com.minitb.dao.DeviceDao;
import com.minitb.domain.entity.Device;
import com.minitb.domain.entity.DeviceId;
import com.minitb.dao.entity.DeviceEntity;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备DAO实现
 * 基于SQLite的设备数据访问实现
 * 负责领域模型与实体模型的转换
 */
@Slf4j
public class DeviceDaoImpl extends BaseDaoImpl<Device, DeviceId> implements DeviceDao {
    
    public DeviceDaoImpl(Connection connection) {
        super(connection, "devices", "id");
    }
    
    @Override
    protected Device insert(Device device) throws SQLException {
        // 1. 领域模型 -> 实体模型
        DeviceEntity entity = DeviceEntity.fromDomain(device);
        
        String sql = """
            INSERT INTO devices (id, name, type, access_token, device_profile_id, created_time)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entity.getId());
            stmt.setString(2, entity.getName());
            stmt.setString(3, entity.getType());
            stmt.setString(4, entity.getAccessToken());
            stmt.setString(5, entity.getDeviceProfileId());
            stmt.setLong(6, entity.getCreatedTime());
            
            stmt.executeUpdate();
            return device;
        }
    }
    
    @Override
    protected Device update(Device device) throws SQLException {
        // 1. 领域模型 -> 实体模型
        DeviceEntity entity = DeviceEntity.fromDomain(device);
        
        String sql = """
            UPDATE devices SET name = ?, type = ?, device_profile_id = ?, access_token = ?
            WHERE id = ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entity.getName());
            stmt.setString(2, entity.getType());
            stmt.setString(3, entity.getDeviceProfileId());
            stmt.setString(4, entity.getAccessToken());
            stmt.setString(5, entity.getId());
            
            stmt.executeUpdate();
            return device;
        }
    }
    
    @Override
    protected Device mapRowToEntity(ResultSet rs) throws SQLException {
        // 1. 从ResultSet创建实体模型
        DeviceEntity entity = new DeviceEntity();
        entity.setId(rs.getString("id"));
        entity.setName(rs.getString("name"));
        entity.setType(rs.getString("type"));
        entity.setDeviceProfileId(rs.getString("device_profile_id"));
        entity.setAccessToken(rs.getString("access_token"));
        entity.setCreatedTime(rs.getLong("created_time"));
        
        // 2. 实体模型 -> 领域模型
        return entity.toDomain();
    }
    
    @Override
    protected DeviceId getEntityId(Device device) {
        return device.getId();
    }
    
    // ==================== 业务方法实现 ====================
    
    @Override
    public Optional<Device> findByAccessToken(String accessToken) {
        try {
            String sql = "SELECT * FROM devices WHERE access_token = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, accessToken);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToEntity(rs));
                    }
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            log.error("根据访问令牌查找设备失败: {}", accessToken, e);
            throw new RuntimeException("查找设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Device> findByDeviceProfileId(String deviceProfileId) {
        try {
            String sql = "SELECT * FROM devices WHERE device_profile_id = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, deviceProfileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Device> devices = new ArrayList<>();
                    while (rs.next()) {
                        devices.add(mapRowToEntity(rs));
                    }
                    return devices;
                }
            }
        } catch (SQLException e) {
            log.error("根据设备配置ID查找设备失败: deviceProfileId={}", deviceProfileId, e);
            throw new RuntimeException("查找设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByDeviceProfileId(String deviceProfileId) {
        try {
            String sql = "SELECT COUNT(*) FROM devices WHERE device_profile_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, deviceProfileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计设备数量失败: deviceProfileId={}", deviceProfileId, e);
            throw new RuntimeException("统计设备数量失败: " + e.getMessage());
        }
    }
    
    @Override
    public void deleteByDeviceProfileId(String deviceProfileId) {
        try {
            String sql = "DELETE FROM devices WHERE device_profile_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, deviceProfileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("根据设备配置ID删除设备失败: deviceProfileId={}", deviceProfileId, e);
            throw new RuntimeException("删除设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Device> findByType(String type) {
        try {
            String sql = "SELECT * FROM devices WHERE type = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Device> devices = new ArrayList<>();
                    while (rs.next()) {
                        devices.add(mapRowToEntity(rs));
                    }
                    return devices;
                }
            }
        } catch (SQLException e) {
            log.error("根据设备类型查找设备失败: type={}", type, e);
            throw new RuntimeException("查找设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByType(String type) {
        try {
            String sql = "SELECT COUNT(*) FROM devices WHERE type = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计设备数量失败: type={}", type, e);
            throw new RuntimeException("统计设备数量失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Device> findByStatus(String status) {
        try {
            String sql = "SELECT * FROM devices WHERE status = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Device> devices = new ArrayList<>();
                    while (rs.next()) {
                        devices.add(mapRowToEntity(rs));
                    }
                    return devices;
                }
            }
        } catch (SQLException e) {
            log.error("根据设备状态查找设备失败: status={}", status, e);
            throw new RuntimeException("查找设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByStatus(String status) {
        try {
            String sql = "SELECT COUNT(*) FROM devices WHERE status = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计设备数量失败: status={}", status, e);
            throw new RuntimeException("统计设备数量失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Device> findByNameLike(String namePattern) {
        try {
            String sql = "SELECT * FROM devices WHERE name LIKE ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, "%" + namePattern + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Device> devices = new ArrayList<>();
                    while (rs.next()) {
                        devices.add(mapRowToEntity(rs));
                    }
                    return devices;
                }
            }
        } catch (SQLException e) {
            log.error("根据设备名称模糊查找失败: namePattern={}", namePattern, e);
            throw new RuntimeException("查找设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Device> findByLabel(String label) {
        try {
            String sql = "SELECT * FROM devices WHERE label = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, label);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Device> devices = new ArrayList<>();
                    while (rs.next()) {
                        devices.add(mapRowToEntity(rs));
                    }
                    return devices;
                }
            }
        } catch (SQLException e) {
            log.error("根据设备标签查找设备失败: label={}", label, e);
            throw new RuntimeException("查找设备失败: " + e.getMessage());
        }
    }
    
    @Override
    public DeviceStatistics getDeviceStatistics() {
        try {
            String sql = """
                SELECT 
                    COUNT(*) as total_count,
                    SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_count,
                    SUM(CASE WHEN status = 'INACTIVE' THEN 1 ELSE 0 END) as inactive_count,
                    SUM(CASE WHEN status = 'ONLINE' THEN 1 ELSE 0 END) as online_count,
                    SUM(CASE WHEN status = 'OFFLINE' THEN 1 ELSE 0 END) as offline_count
                FROM devices
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new DeviceStatistics(
                            rs.getLong("total_count"),
                            rs.getLong("active_count"),
                            rs.getLong("inactive_count"),
                            rs.getLong("online_count"),
                            rs.getLong("offline_count")
                        );
                    }
                }
            }
            return new DeviceStatistics(0, 0, 0, 0, 0);
        } catch (SQLException e) {
            log.error("获取设备统计信息失败", e);
            throw new RuntimeException("获取设备统计信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean existsByAccessToken(String accessToken) {
        try {
            String sql = "SELECT COUNT(*) FROM devices WHERE access_token = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, accessToken);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getLong(1) > 0;
                }
            }
        } catch (SQLException e) {
            log.error("检查访问令牌是否存在失败: accessToken={}", accessToken, e);
            throw new RuntimeException("检查访问令牌失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean existsByName(String name) {
        try {
            String sql = "SELECT 1 FROM devices WHERE name = ? LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("检查设备名称是否存在失败", e);
            throw new RuntimeException("检查设备名称是否存在失败: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<Device> findByName(String name) {
        try {
            String sql = "SELECT * FROM devices WHERE name = ? LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToEntity(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            log.error("根据名称查找设备失败", e);
            throw new RuntimeException("根据名称查找设备失败: " + e.getMessage());
        }
    }
}