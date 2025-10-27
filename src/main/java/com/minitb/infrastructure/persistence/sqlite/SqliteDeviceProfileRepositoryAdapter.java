package com.minitb.infrastructure.persistence.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.DeviceProfileRepository;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.infrastructure.persistence.sqlite.mapper.DeviceProfileRowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite DeviceProfile Repository Adapter
 * 
 * 实现 Domain 层的 DeviceProfileRepository 接口，使用 SQLite JDBC
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "sqlite")
@RequiredArgsConstructor
@Slf4j
public class SqliteDeviceProfileRepositoryAdapter implements DeviceProfileRepository {
    
    private final SqliteConnectionManager connectionManager;
    private final DeviceProfileRowMapper profileMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public DeviceProfile save(DeviceProfile profile) {
        log.debug("Saving device profile to SQLite: {}", profile.getId());
        
        String sql = """
            INSERT OR REPLACE INTO device_profile 
            (id, name, description, telemetry_definitions_json, strict_mode,
             data_source_type, prometheus_endpoint, prometheus_device_label_key,
             created_time, updated_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, profile.getId().toString());
            stmt.setString(2, profile.getName());
            stmt.setString(3, profile.getDescription());
            
            // 序列化遥测定义为 JSON
            String telemetryJson = objectMapper.writeValueAsString(profile.getTelemetryDefinitions());
            stmt.setString(4, telemetryJson);
            
            stmt.setInt(5, profile.isStrictMode() ? 1 : 0);
            stmt.setString(6, profile.getDataSourceType() != null ? 
                profile.getDataSourceType().name() : null);
            stmt.setString(7, profile.getPrometheusEndpoint());
            stmt.setString(8, profile.getPrometheusDeviceLabelKey());
            stmt.setLong(9, profile.getCreatedTime());
            stmt.setLong(10, System.currentTimeMillis());
            
            int affected = stmt.executeUpdate();
            log.debug("DeviceProfile saved, affected rows: {}", affected);
            
            return profile;
            
        } catch (Exception e) {
            log.error("Failed to save device profile: {}", profile.getId(), e);
            throw new RuntimeException("Failed to save device profile", e);
        }
    }
    
    @Override
    public Optional<DeviceProfile> findById(DeviceProfileId profileId) {
        log.debug("Finding device profile by id: {}", profileId);
        
        String sql = "SELECT * FROM device_profile WHERE id = ?";
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, profileId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DeviceProfile profile = profileMapper.mapRow(rs);
                    log.debug("DeviceProfile found: {}", profile.getName());
                    return Optional.of(profile);
                }
            }
            
            log.debug("DeviceProfile not found: {}", profileId);
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Failed to find device profile by id: {}", profileId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<DeviceProfile> findByName(String name) {
        log.debug("Finding device profile by name: {}", name);
        
        String sql = "SELECT * FROM device_profile WHERE name = ?";
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DeviceProfile profile = profileMapper.mapRow(rs);
                    log.debug("DeviceProfile found by name: {}", profile.getName());
                    return Optional.of(profile);
                }
            }
            
            log.debug("DeviceProfile not found by name: {}", name);
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Failed to find device profile by name: {}", name, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<DeviceProfile> findAll() {
        log.debug("Finding all device profiles");
        
        String sql = "SELECT * FROM device_profile ORDER BY created_time DESC";
        List<DeviceProfile> profiles = new ArrayList<>();
        
        try (Statement stmt = connectionManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                profiles.add(profileMapper.mapRow(rs));
            }
            
            log.debug("Found {} device profiles", profiles.size());
            
        } catch (SQLException e) {
            log.error("Failed to find all device profiles", e);
        }
        
        return profiles;
    }
    
    @Override
    public void deleteById(DeviceProfileId profileId) {
        log.debug("Deleting device profile: {}", profileId);
        
        String sql = "DELETE FROM device_profile WHERE id = ?";
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, profileId.toString());
            
            int affected = stmt.executeUpdate();
            log.debug("DeviceProfile deleted, affected rows: {}", affected);
            
        } catch (SQLException e) {
            log.error("Failed to delete device profile: {}", profileId, e);
            throw new RuntimeException("Failed to delete device profile", e);
        }
    }
    
}

