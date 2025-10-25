package com.minitb.dao;

import com.minitb.domain.entity.DeviceProfile;
import com.minitb.domain.entity.DeviceProfileId;
import com.minitb.dao.entity.DeviceProfileEntity;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 设备配置 DAO - SQLite 实现
 * 
 * 关键功能：
 * - 处理复杂对象序列化（TelemetryDefinition List → JSON）
 * - 使用 Entity 层封装序列化逻辑
 */
@Slf4j
public class DeviceProfileDao {
    
    private final Connection conn;
    
    public DeviceProfileDao() throws SQLException {
        this.conn = DatabaseManager.getConnection();
    }
    
    /**
     * 保存设备配置
     */
    public DeviceProfile save(DeviceProfile profile) {
        // Domain → Entity（自动序列化复杂对象）
        DeviceProfileEntity entity = DeviceProfileEntity.fromDomain(profile);
        
        String sql = """
            INSERT INTO device_profile 
            (id, name, description, data_source_type, strict_mode, telemetry_definitions_json, created_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                data_source_type = excluded.data_source_type,
                strict_mode = excluded.strict_mode,
                telemetry_definitions_json = excluded.telemetry_definitions_json
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getId());
            ps.setString(2, entity.getName());
            ps.setString(3, entity.getDescription());
            ps.setString(4, entity.getDataSourceType());
            ps.setInt(5, entity.getStrictMode() ? 1 : 0);
            ps.setString(6, entity.getTelemetryDefinitionsJson());
            ps.setLong(7, entity.getCreatedTime());
            
            ps.executeUpdate();
            log.debug("保存设备配置: {}", profile.getName());
            return profile;
            
        } catch (SQLException e) {
            log.error("保存设备配置失败: {}", profile.getName(), e);
            throw new RuntimeException("保存设备配置失败", e);
        }
    }
    
    /**
     * 根据 ID 查找配置
     */
    public Optional<DeviceProfile> findById(DeviceProfileId id) {
        String sql = "SELECT * FROM device_profile WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                DeviceProfileEntity entity = mapResultSetToEntity(rs);
                return Optional.of(entity.toDomain());
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("查询设备配置失败: {}", id, e);
            return Optional.empty();
        }
    }
    
    /**
     * 查找所有配置
     */
    public List<DeviceProfile> findAll() {
        List<DeviceProfile> profiles = new ArrayList<>();
        String sql = "SELECT * FROM device_profile ORDER BY created_time DESC";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                DeviceProfileEntity entity = mapResultSetToEntity(rs);
                profiles.add(entity.toDomain());
            }
            
            log.debug("查询到 {} 个设备配置", profiles.size());
            return profiles;
            
        } catch (SQLException e) {
            log.error("查询所有设备配置失败", e);
            return profiles;
        }
    }
    
    /**
     * 根据数据源类型查找配置
     */
    public List<DeviceProfile> findByDataSourceType(DeviceProfile.DataSourceType dataSourceType) {
        List<DeviceProfile> profiles = new ArrayList<>();
        String sql = "SELECT * FROM device_profile WHERE data_source_type = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dataSourceType.name());
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                DeviceProfileEntity entity = mapResultSetToEntity(rs);
                profiles.add(entity.toDomain());
            }
            
            log.debug("查询到 {} 个 {} 类型配置", profiles.size(), dataSourceType);
            return profiles;
            
        } catch (SQLException e) {
            log.error("根据数据源类型查询配置失败: {}", dataSourceType, e);
            return profiles;
        }
    }
    
    /**
     * 删除配置
     */
    public boolean delete(DeviceProfileId id) {
        String sql = "DELETE FROM device_profile WHERE id = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            int rows = ps.executeUpdate();
            
            log.debug("删除设备配置: {}, 影响行数: {}", id, rows);
            return rows > 0;
            
        } catch (SQLException e) {
            log.error("删除设备配置失败: {}", id, e);
            return false;
        }
    }
    
    /**
     * 统计配置数量
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM device_profile";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (SQLException e) {
            log.error("统计设备配置数量失败", e);
        }
        
        return 0;
    }
    
    /**
     * ResultSet → DeviceProfileEntity 映射
     */
    private DeviceProfileEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return DeviceProfileEntity.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .dataSourceType(rs.getString("data_source_type"))
                .strictMode(rs.getInt("strict_mode") == 1)
                .telemetryDefinitionsJson(rs.getString("telemetry_definitions_json"))
                .createdTime(rs.getLong("created_time"))
                .build();
    }
}

