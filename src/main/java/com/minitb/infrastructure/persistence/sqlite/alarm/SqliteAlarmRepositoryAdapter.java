package com.minitb.infrastructure.persistence.sqlite.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.domain.alarm.*;
import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.DeviceId;
import com.minitb.infrastructure.persistence.sqlite.DatabaseConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite 告警仓储适配器
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "sqlite", matchIfMissing = true)
@Slf4j
public class SqliteAlarmRepositoryAdapter implements AlarmRepository {
    
    private final DatabaseConnectionManager connectionManager;  // ⭐ 改用接口
    private final ObjectMapper objectMapper;
    
    public SqliteAlarmRepositoryAdapter(DatabaseConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        initTable();
    }
    
    /**
     * 初始化告警表
     */
    private void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS alarm (
                id TEXT PRIMARY KEY,
                device_id TEXT NOT NULL,
                device_name TEXT,
                type TEXT NOT NULL,
                severity TEXT NOT NULL,
                start_ts INTEGER NOT NULL,
                end_ts INTEGER,
                ack_ts INTEGER,
                clear_ts INTEGER,
                details TEXT,
                created_time INTEGER NOT NULL
            );
            
            CREATE INDEX IF NOT EXISTS idx_alarm_device_id ON alarm(device_id);
            CREATE INDEX IF NOT EXISTS idx_alarm_type ON alarm(type);
            CREATE INDEX IF NOT EXISTS idx_alarm_severity ON alarm(severity);
            CREATE INDEX IF NOT EXISTS idx_alarm_start_ts ON alarm(start_ts DESC);
            """;
        
        Connection conn = connectionManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            log.debug("告警表初始化完成");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize alarm table", e);
        }
    }
    
    @Override
    public Alarm save(Alarm alarm) {
        String sql = """
            INSERT INTO alarm (id, device_id, device_name, type, severity, 
                               start_ts, end_ts, ack_ts, clear_ts, details, created_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                device_name = excluded.device_name,
                severity = excluded.severity,
                end_ts = excluded.end_ts,
                ack_ts = excluded.ack_ts,
                clear_ts = excluded.clear_ts,
                details = excluded.details
            """;
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, alarm.getId().toString());
            ps.setString(2, alarm.getOriginator().toString());
            ps.setString(3, alarm.getOriginatorName());
            ps.setString(4, alarm.getType());
            ps.setString(5, alarm.getSeverity().name());
            ps.setLong(6, alarm.getStartTs());
            ps.setLong(7, alarm.getEndTs());
            ps.setObject(8, alarm.getAckTs());
            ps.setObject(9, alarm.getClearTs());
            ps.setString(10, alarm.getDetails() != null ? alarm.getDetails().toString() : null);
            ps.setLong(11, alarm.getCreatedTime());
            
            ps.executeUpdate();
            return alarm;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save alarm", e);
        }
    }
    
    @Override
    public Optional<Alarm> findById(AlarmId id) {
        String sql = "SELECT * FROM alarm WHERE id = ?";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, id.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find alarm", e);
        }
    }
    
    @Override
    public Optional<Alarm> findLatestByOriginatorAndType(DeviceId deviceId, String alarmType) {
        String sql = """
            SELECT * FROM alarm 
            WHERE device_id = ? AND type = ? 
            ORDER BY start_ts DESC 
            LIMIT 1
            """;
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, deviceId.toString());
            ps.setString(2, alarmType);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find latest alarm", e);
        }
    }
    
    @Override
    public List<Alarm> findByOriginator(DeviceId deviceId) {
        String sql = "SELECT * FROM alarm WHERE device_id = ? ORDER BY start_ts DESC";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, deviceId.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                List<Alarm> alarms = new ArrayList<>();
                while (rs.next()) {
                    alarms.add(mapRow(rs));
                }
                return alarms;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find alarms by originator", e);
        }
    }
    
    @Override
    public List<Alarm> findByOriginatorAndStatus(DeviceId deviceId, AlarmStatus status) {
        String sql = "SELECT * FROM alarm WHERE device_id = ? ORDER BY start_ts DESC";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, deviceId.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                List<Alarm> alarms = new ArrayList<>();
                while (rs.next()) {
                    Alarm alarm = mapRow(rs);
                    if (alarm.getStatus() == status) {
                        alarms.add(alarm);
                    }
                }
                return alarms;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find alarms by status", e);
        }
    }
    
    @Override
    public List<Alarm> findAllActive() {
        String sql = "SELECT * FROM alarm WHERE clear_ts IS NULL ORDER BY start_ts DESC";
        
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            List<Alarm> alarms = new ArrayList<>();
            while (rs.next()) {
                alarms.add(mapRow(rs));
            }
            return alarms;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active alarms", e);
        }
    }
    
    @Override
    public List<Alarm> findAllUnacknowledged() {
        String sql = "SELECT * FROM alarm WHERE ack_ts IS NULL ORDER BY start_ts DESC";
        
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            List<Alarm> alarms = new ArrayList<>();
            while (rs.next()) {
                alarms.add(mapRow(rs));
            }
            return alarms;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find unacknowledged alarms", e);
        }
    }
    
    @Override
    public List<Alarm> findByTimeRange(long startTs, long endTs) {
        String sql = "SELECT * FROM alarm WHERE start_ts >= ? AND start_ts <= ? ORDER BY start_ts DESC";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, startTs);
            ps.setLong(2, endTs);
            
            try (ResultSet rs = ps.executeQuery()) {
                List<Alarm> alarms = new ArrayList<>();
                while (rs.next()) {
                    alarms.add(mapRow(rs));
                }
                return alarms;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find alarms by time range", e);
        }
    }
    
    @Override
    public void deleteById(AlarmId id) {
        String sql = "DELETE FROM alarm WHERE id = ?";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, id.toString());
            ps.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete alarm", e);
        }
    }
    
    @Override
    public void deleteByOriginator(DeviceId deviceId) {
        String sql = "DELETE FROM alarm WHERE device_id = ?";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, deviceId.toString());
            ps.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete alarms by originator", e);
        }
    }
    
    @Override
    public long countByOriginator(DeviceId deviceId) {
        String sql = "SELECT COUNT(*) FROM alarm WHERE device_id = ?";
        
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, deviceId.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count alarms", e);
        }
    }
    
    @Override
    public long countByStatus(AlarmStatus status) {
        // SQLite 不直接存储 status，需要根据 ack_ts 和 clear_ts 计算
        String sql;
        if (status.isCleared()) {
            sql = status.isAcknowledged() 
                ? "SELECT COUNT(*) FROM alarm WHERE clear_ts IS NOT NULL AND ack_ts IS NOT NULL"
                : "SELECT COUNT(*) FROM alarm WHERE clear_ts IS NOT NULL AND ack_ts IS NULL";
        } else {
            sql = status.isAcknowledged()
                ? "SELECT COUNT(*) FROM alarm WHERE clear_ts IS NULL AND ack_ts IS NOT NULL"
                : "SELECT COUNT(*) FROM alarm WHERE clear_ts IS NULL AND ack_ts IS NULL";
        }
        
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count alarms by status", e);
        }
    }
    
    /**
     * 将 ResultSet 映射为 Alarm 对象
     */
    private Alarm mapRow(ResultSet rs) throws SQLException {
        JsonNode details = null;
        String detailsStr = rs.getString("details");
        if (detailsStr != null) {
            try {
                details = objectMapper.readTree(detailsStr);
            } catch (Exception e) {
                log.warn("Failed to parse alarm details: {}", e.getMessage());
            }
        }
        
        // ⭐ 处理可空字段：ackTs 和 clearTs
        Long ackTs = rs.getLong("ack_ts");
        if (rs.wasNull()) {
            ackTs = null;
        }
        
        Long clearTs = rs.getLong("clear_ts");
        if (rs.wasNull()) {
            clearTs = null;
        }
        
        return Alarm.builder()
            .id(AlarmId.fromString(rs.getString("id")))
            .originator(DeviceId.fromString(rs.getString("device_id")))
            .originatorName(rs.getString("device_name"))
            .type(rs.getString("type"))
            .severity(AlarmSeverity.valueOf(rs.getString("severity")))
            .startTs(rs.getLong("start_ts"))
            .endTs(rs.getLong("end_ts"))
            .ackTs(ackTs)
            .clearTs(clearTs)
            .details(details)
            .createdTime(rs.getLong("created_time"))
            .build();
    }
}

