package com.minitb.dao.impl;

import com.minitb.dao.BaseDaoImpl;
import com.minitb.dao.DeviceProfileDao;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.DeviceProfileId;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备配置DAO实现
 * 基于SQLite的设备配置数据访问实现
 * 负责领域模型与实体模型的转换
 */
@Slf4j
public class DeviceProfileDaoImpl extends BaseDaoImpl<DeviceProfile, DeviceProfileId> implements DeviceProfileDao {

    public DeviceProfileDaoImpl(Connection connection) {
        super(connection, "device_profiles", "id");
    }

    @Override
    protected DeviceProfile insert(DeviceProfile deviceProfile) throws SQLException {
        String sql = """
            INSERT INTO device_profiles (id, name, description, data_source_type, strict_mode, created_time)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, deviceProfile.getId().toString());
            stmt.setString(2, deviceProfile.getName());
            stmt.setString(3, deviceProfile.getDescription());
            stmt.setString(4, deviceProfile.getDataSourceType().name());
            stmt.setBoolean(5, deviceProfile.isStrictMode());
            stmt.setLong(6, deviceProfile.getCreatedTime());

            stmt.executeUpdate();
            return deviceProfile;
        }
    }

    @Override
    protected DeviceProfile update(DeviceProfile deviceProfile) throws SQLException {
        String sql = """
            UPDATE device_profiles SET name = ?, description = ?, data_source_type = ?, strict_mode = ?
            WHERE id = ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, deviceProfile.getName());
            stmt.setString(2, deviceProfile.getDescription());
            stmt.setString(3, deviceProfile.getDataSourceType().name());
            stmt.setBoolean(4, deviceProfile.isStrictMode());
            stmt.setString(5, deviceProfile.getId().toString());

            stmt.executeUpdate();
            return deviceProfile;
        }
    }

    @Override
    protected DeviceProfile mapRowToEntity(ResultSet rs) throws SQLException {
        DeviceProfile deviceProfile = new DeviceProfile();
        deviceProfile.setId(DeviceProfileId.fromString(rs.getString("id")));
        deviceProfile.setName(rs.getString("name"));
        deviceProfile.setDescription(rs.getString("description"));
        deviceProfile.setDataSourceType(DeviceProfile.DataSourceType.valueOf(rs.getString("data_source_type")));
        deviceProfile.setStrictMode(rs.getBoolean("strict_mode"));
        deviceProfile.setCreatedTime(rs.getLong("created_time"));
        return deviceProfile;
    }

    @Override
    protected DeviceProfileId getEntityId(DeviceProfile deviceProfile) {
        return deviceProfile.getId();
    }

    // ==================== 业务方法实现 ====================

    @Override
    public List<DeviceProfile> findByType(String type) {
        try {
            String sql = "SELECT * FROM device_profiles WHERE data_source_type = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<DeviceProfile> profiles = new ArrayList<>();
                    while (rs.next()) {
                        profiles.add(mapRowToEntity(rs));
                    }
                    return profiles;
                }
            }
        } catch (SQLException e) {
            log.error("根据类型查找设备配置失败: type={}", type, e);
            throw new RuntimeException("查找设备配置失败: " + e.getMessage());
        }
    }

    @Override
    public long countByType(String type) {
        try {
            String sql = "SELECT COUNT(*) FROM device_profiles WHERE data_source_type = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计设备配置数量失败: type={}", type, e);
            throw new RuntimeException("统计设备配置数量失败: " + e.getMessage());
        }
    }

    @Override
    public List<DeviceProfile> findByTransportType(String transportType) {
        // 简化实现，返回空列表
        return new ArrayList<>();
    }

    @Override
    public long countByTransportType(String transportType) {
        return 0;
    }

    @Override
    public List<DeviceProfile> findByNameLike(String namePattern) {
        try {
            String sql = "SELECT * FROM device_profiles WHERE name LIKE ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, "%" + namePattern + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    List<DeviceProfile> profiles = new ArrayList<>();
                    while (rs.next()) {
                        profiles.add(mapRowToEntity(rs));
                    }
                    return profiles;
                }
            }
        } catch (SQLException e) {
            log.error("根据名称模糊查找设备配置失败: namePattern={}", namePattern, e);
            throw new RuntimeException("查找设备配置失败: " + e.getMessage());
        }
    }

    @Override
    public List<DeviceProfile> findByIsDefault(boolean isDefault) {
        // 简化实现，返回空列表
        return new ArrayList<>();
    }

    @Override
    public long countByIsDefault(boolean isDefault) {
        return 0;
    }

    @Override
    public Optional<DeviceProfile> findDefault() {
        return Optional.empty();
    }

    @Override
    public void setDefault(DeviceProfileId deviceProfileId) {
        // 简化实现，不做任何操作
    }

    @Override
    public void unsetDefault(DeviceProfileId deviceProfileId) {
        // 简化实现，不做任何操作
    }

    @Override
    public DeviceProfileStatistics getDeviceProfileStatistics() {
        try {
            String sql = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(CASE WHEN data_source_type = 'PROMETHEUS' THEN 1 ELSE 0 END) as prometheus_count,
                    SUM(CASE WHEN data_source_type = 'MQTT' THEN 1 ELSE 0 END) as mqtt_count,
                    SUM(CASE WHEN data_source_type = 'HTTP' THEN 1 ELSE 0 END) as http_count
                FROM device_profiles
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new DeviceProfileStatistics(
                            rs.getLong("total_count"),
                            0, // defaultCount
                            0, // customCount
                            rs.getLong("mqtt_count"),
                            0, // coapCount
                            rs.getLong("http_count")
                        );
                    }
                }
            }
            return new DeviceProfileStatistics(0, 0, 0, 0, 0, 0);
        } catch (SQLException e) {
            log.error("获取设备配置统计信息失败", e);
            throw new RuntimeException("获取设备配置统计信息失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isUsedByDevices(DeviceProfileId deviceProfileId) {
        try {
            // 简化实现：检查devices表中是否有使用此配置的设备
            String sql = "SELECT COUNT(*) FROM devices WHERE device_profile_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, deviceProfileId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getLong(1) > 0;
                }
            }
        } catch (SQLException e) {
            log.error("检查设备配置使用情况失败: deviceProfileId={}", deviceProfileId, e);
            // 如果查询失败，为了安全起见，返回true（不允许删除）
            return true;
        }
    }

    @Override
    public List<DeviceProfile> findByDataSourceType(DeviceProfile.DataSourceType dataSourceType) {
        try {
            String sql = "SELECT * FROM device_profiles WHERE data_source_type = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, dataSourceType.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    List<DeviceProfile> profiles = new ArrayList<>();
                    while (rs.next()) {
                        profiles.add(mapRowToEntity(rs));
                    }
                    return profiles;
                }
            }
        } catch (SQLException e) {
            log.error("根据数据源类型查找设备配置失败: dataSourceType={}", dataSourceType, e);
            throw new RuntimeException("查找设备配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean existsByName(String name) {
        try {
            String sql = "SELECT 1 FROM device_profiles WHERE name = ? LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("检查设备配置文件名称是否存在失败", e);
            throw new RuntimeException("检查设备配置文件名称是否存在失败: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<DeviceProfile> findByName(String name) {
        try {
            String sql = "SELECT * FROM device_profiles WHERE name = ? LIMIT 1";
            
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
            log.error("根据名称查找设备配置文件失败", e);
            throw new RuntimeException("根据名称查找设备配置文件失败: " + e.getMessage());
        }
    }
}