package com.minitb.dao;

import com.minitb.common.entity.Device;
import com.minitb.common.entity.DeviceId;
import com.minitb.dao.entity.DeviceEntity;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 设备 DAO - SQLite 实现
 * 
 * 职责：
 * 1. 设备的 CRUD 操作
 * 2. 使用 Entity 层进行类型转换
 * 3. 处理 SQL 操作和异常
 * 
 * 设计模式：
 * - DAO 模式：封装数据访问逻辑
 * - 使用 Entity 层解耦业务对象和数据库
 */
@Slf4j
public class DeviceDao {
    
    private final Connection conn;
    
    public DeviceDao() throws SQLException {
        this.conn = DatabaseManager.getConnection();
    }
    
    /**
     * 保存设备（INSERT or UPDATE）
     */
    public Device save(Device device) {
        // 1. Domain → Entity
        DeviceEntity entity = DeviceEntity.fromDomain(device);
        
        // 2. 保存到数据库
        String sql = """
            INSERT INTO device (id, name, type, access_token, device_profile_id, created_time)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                type = excluded.type,
                access_token = excluded.access_token,
                device_profile_id = excluded.device_profile_id
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getId());
            ps.setString(2, entity.getName());
            ps.setString(3, entity.getType());
            ps.setString(4, entity.getAccessToken());
            ps.setString(5, entity.getDeviceProfileId());
            ps.setLong(6, entity.getCreatedTime());
            
            ps.executeUpdate();
            log.debug("保存设备: {}", device.getName());
            
            // 3. 返回 Domain 对象
            return device;
            
        } catch (SQLException e) {
            log.error("保存设备失败: {}", device.getName(), e);
            throw new RuntimeException("保存设备失败", e);
        }
    }
    
    /**
     * 根据 ID 查找设备
     */
    public Optional<Device> findById(DeviceId id) {
        String sql = "SELECT * FROM device WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                DeviceEntity entity = mapResultSetToEntity(rs);
                return Optional.of(entity.toDomain());
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("查询设备失败: {}", id, e);
            return Optional.empty();
        }
    }
    
    /**
     * 根据 AccessToken 查找设备
     */
    public Optional<Device> findByAccessToken(String accessToken) {
        String sql = "SELECT * FROM device WHERE access_token = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accessToken);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                DeviceEntity entity = mapResultSetToEntity(rs);
                return Optional.of(entity.toDomain());
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("根据 token 查询设备失败: {}", accessToken, e);
            return Optional.empty();
        }
    }
    
    /**
     * 查找所有设备
     */
    public List<Device> findAll() {
        List<Device> devices = new ArrayList<>();
        String sql = "SELECT * FROM device ORDER BY created_time DESC";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                DeviceEntity entity = mapResultSetToEntity(rs);
                devices.add(entity.toDomain());
            }
            
            log.debug("查询到 {} 个设备", devices.size());
            return devices;
            
        } catch (SQLException e) {
            log.error("查询所有设备失败", e);
            return devices;
        }
    }
    
    /**
     * 根据类型查找设备
     */
    public List<Device> findByType(String type) {
        List<Device> devices = new ArrayList<>();
        String sql = "SELECT * FROM device WHERE type = ? ORDER BY created_time DESC";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                DeviceEntity entity = mapResultSetToEntity(rs);
                devices.add(entity.toDomain());
            }
            
            log.debug("查询到 {} 个 {} 类型设备", devices.size(), type);
            return devices;
            
        } catch (SQLException e) {
            log.error("根据类型查询设备失败: {}", type, e);
            return devices;
        }
    }
    
    /**
     * 根据设备名称查找
     */
    public Optional<Device> findByName(String name) {
        String sql = "SELECT * FROM device WHERE name = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                DeviceEntity entity = mapResultSetToEntity(rs);
                return Optional.of(entity.toDomain());
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("根据名称查询设备失败: {}", name, e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除设备
     */
    public boolean delete(DeviceId id) {
        String sql = "DELETE FROM device WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            int rows = ps.executeUpdate();
            
            log.debug("删除设备: {}, 影响行数: {}", id, rows);
            return rows > 0;
            
        } catch (SQLException e) {
            log.error("删除设备失败: {}", id, e);
            return false;
        }
    }
    
    /**
     * 统计设备数量
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM device";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (SQLException e) {
            log.error("统计设备数量失败", e);
        }
        
        return 0;
    }
    
    /**
     * 按类型统计设备数量
     */
    public long countByType(String type) {
        String sql = "SELECT COUNT(*) FROM device WHERE type = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (SQLException e) {
            log.error("按类型统计设备失败: {}", type, e);
        }
        
        return 0;
    }
    
    /**
     * ResultSet → DeviceEntity 映射
     * 这里处理数据库类型到 Entity 的转换
     */
    private DeviceEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return DeviceEntity.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .type(rs.getString("type"))
                .accessToken(rs.getString("access_token"))
                .deviceProfileId(rs.getString("device_profile_id"))
                .createdTime(rs.getLong("created_time"))
                .build();
    }
}

