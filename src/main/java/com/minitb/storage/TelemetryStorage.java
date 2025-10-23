package com.minitb.storage;

import com.minitb.common.entity.DeviceId;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遥测数据存储 - 核心数据流的最后一层
 * 职责：持久化时序数据
 * 
 * 简化实现：
 * 1. 内存存储（Map）
 * 2. 文件备份（可选）
 * 
 * 实际ThingsBoard使用：
 * - Cassandra（大规模时序数据）
 * - PostgreSQL（中小规模）
 * - TimescaleDB（PostgreSQL扩展）
 */
@Slf4j
public class TelemetryStorage {
    
    // 内存存储：设备ID -> 数据列表
    private final Map<DeviceId, List<TelemetryData>> dataStore = new ConcurrentHashMap<>();
    
    // 是否启用文件备份
    private final boolean enableFileBackup;
    private final String backupDir;
    
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
    
    public TelemetryStorage(boolean enableFileBackup) {
        this.enableFileBackup = enableFileBackup;
        this.backupDir = "minitb/data";
        
        if (enableFileBackup) {
            try {
                Files.createDirectories(Paths.get(backupDir));
                log.info("遥测数据存储初始化完成，文件备份目录: {}", backupDir);
            } catch (IOException e) {
                log.error("创建备份目录失败", e);
            }
        } else {
            log.info("遥测数据存储初始化完成（仅内存模式）");
        }
    }

    /**
     * 保存遥测数据
     */
    public void save(DeviceId deviceId, long timestamp, String jsonData) {
        TelemetryData data = new TelemetryData(timestamp, jsonData);
        
        // 保存到内存
        dataStore.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(data);
        
        log.info("保存遥测数据: deviceId={}, ts={}, data={}", 
                deviceId, formatTimestamp(timestamp), jsonData);
        
        // 可选：备份到文件
        if (enableFileBackup) {
            backupToFile(deviceId, data);
        }
    }

    /**
     * 备份到文件
     */
    private void backupToFile(DeviceId deviceId, TelemetryData data) {
        try {
            String filename = backupDir + "/telemetry_" + deviceId.getId() + ".log";
            try (FileWriter writer = new FileWriter(filename, true)) {
                writer.write(String.format("[%s] %s%n", 
                        formatTimestamp(data.timestamp), data.jsonData));
            }
        } catch (IOException e) {
            log.error("备份到文件失败", e);
        }
    }

    /**
     * 查询设备的遥测数据
     */
    public List<TelemetryData> query(DeviceId deviceId, long startTs, long endTs) {
        List<TelemetryData> allData = dataStore.get(deviceId);
        if (allData == null) {
            return new ArrayList<>();
        }
        
        return allData.stream()
                .filter(data -> data.timestamp >= startTs && data.timestamp <= endTs)
                .toList();
    }

    /**
     * 获取设备的最新数据
     */
    public TelemetryData getLatest(DeviceId deviceId) {
        List<TelemetryData> allData = dataStore.get(deviceId);
        if (allData == null || allData.isEmpty()) {
            return null;
        }
        return allData.get(allData.size() - 1);
    }

    /**
     * 获取数据统计
     */
    public void printStatistics() {
        log.info("=== 遥测数据统计 ===");
        dataStore.forEach((deviceId, dataList) -> {
            log.info("设备 {}: {} 条数据", deviceId, dataList.size());
            if (!dataList.isEmpty()) {
                TelemetryData latest = dataList.get(dataList.size() - 1);
                log.info("  最新数据时间: {}", formatTimestamp(latest.timestamp));
                log.info("  最新数据内容: {}", latest.jsonData);
            }
        });
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * 遥测数据记录
     */
    public static class TelemetryData {
        public final long timestamp;
        public final String jsonData;
        
        public TelemetryData(long timestamp, String jsonData) {
            this.timestamp = timestamp;
            this.jsonData = jsonData;
        }
    }
}


