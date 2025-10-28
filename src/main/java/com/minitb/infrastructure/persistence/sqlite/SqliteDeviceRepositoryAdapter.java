package com.minitb.infrastructure.persistence.sqlite;

import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceRepository;
import com.minitb.domain.id.DeviceId;
import com.minitb.infrastructure.persistence.sqlite.mapper.DeviceRowMapper;
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
 * SQLite Device Repository Adapter
 * 
 * 实现 Domain 层的 DeviceRepository 接口，使用 SQLite JDBC
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "sqlite")
@RequiredArgsConstructor
@Slf4j
public class SqliteDeviceRepositoryAdapter implements DeviceRepository {
    
    private final SqliteConnectionManager connectionManager;
    private final DeviceRowMapper deviceMapper;
    
    @Override
    public Device save(Device device) {
        log.debug("Saving device to SQLite: {}", device.getId());
        
        String sql = """
            INSERT OR REPLACE INTO device 
            (id, name, type, access_token, device_profile_id, 
             configuration, created_time, updated_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, device.getId().toString());
            stmt.setString(2, device.getName());
            stmt.setString(3, device.getType());
            stmt.setString(4, device.getAccessToken());
            stmt.setString(5, device.getDeviceProfileId() != null ? 
                device.getDeviceProfileId().toString() : null);
            
            // 序列化 configuration 为 JSON
            String configJson = deviceMapper.serializeConfiguration(device.getConfiguration());
            stmt.setString(6, configJson);
            
            stmt.setLong(7, device.getCreatedTime());
            stmt.setLong(8, System.currentTimeMillis());
            
            int affected = stmt.executeUpdate();
            log.debug("Device saved, affected rows: {}", affected);
            
            return device;
            
        } catch (SQLException e) {
            log.error("Failed to save device: {}", device.getId(), e);
            throw new RuntimeException("Failed to save device", e);
        }
    }
    
    @Override
    public Optional<Device> findById(DeviceId deviceId) {
        log.debug("Finding device by id: {}", deviceId);
        
        String sql = "SELECT * FROM device WHERE id = ?";
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, deviceId.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Device device = deviceMapper.mapRow(rs);
                    log.debug("Device found: {}", device.getName());
                    return Optional.of(device);
                }
            }
            
            log.debug("Device not found: {}", deviceId);
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Failed to find device by id: {}", deviceId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<Device> findByAccessToken(String accessToken) {
        log.debug("Finding device by access token: {}", accessToken);
        
        String sql = "SELECT * FROM device WHERE access_token = ?";
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, accessToken);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Device device = deviceMapper.mapRow(rs);
                    log.debug("Device found by token: {}", device.getName());
                    return Optional.of(device);
                }
            }
            
            log.debug("Device not found by token: {}", accessToken);
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Failed to find device by access token", e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<Device> findAll() {
        log.debug("Finding all devices");
        
        String sql = "SELECT * FROM device ORDER BY created_time DESC";
        List<Device> devices = new ArrayList<>();
        
        try (Statement stmt = connectionManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                devices.add(deviceMapper.mapRow(rs));
            }
            
            log.debug("Found {} devices", devices.size());
            
        } catch (SQLException e) {
            log.error("Failed to find all devices", e);
        }
        
        return devices;
    }
    
    @Override
    public void deleteById(DeviceId deviceId) {
        log.debug("Deleting device: {}", deviceId);
        
        String sql = "DELETE FROM device WHERE id = ?";
        
        try (PreparedStatement stmt = connectionManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, deviceId.toString());
            
            int affected = stmt.executeUpdate();
            log.debug("Device deleted, affected rows: {}", affected);
            
        } catch (SQLException e) {
            log.error("Failed to delete device: {}", deviceId, e);
            throw new RuntimeException("Failed to delete device", e);
        }
    }
}

