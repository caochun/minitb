package com.minitb.storage;

import com.minitb.common.entity.DeviceId;
import com.minitb.common.kv.DataType;
import com.minitb.common.kv.TsKvEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 遥测数据存储 - 核心数据流的最后一层
 * 职责：持久化时序数据
 * 
 * 重构后：
 * 1. 支持强类型数据存储（TsKvEntry）
 * 2. 按键名查询
 * 3. 按数据类型过滤
 * 4. 兼容旧版字符串存储
 */
@Slf4j
public class TelemetryStorage {
    
    // 内存存储：设备ID -> 键名 -> 时间序列数据列表
    private final Map<DeviceId, Map<String, List<TsKvEntry>>> dataStore = new ConcurrentHashMap<>();
    
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
                log.info("遥测数据存储初始化完成（强类型模式），文件备份目录: {}", backupDir);
            } catch (IOException e) {
                log.error("创建备份目录失败", e);
            }
        } else {
            log.info("遥测数据存储初始化完成（强类型模式，仅内存）");
        }
    }

    /**
     * 保存单个遥测数据点
     */
    public void save(DeviceId deviceId, TsKvEntry tsKvEntry) {
        String key = tsKvEntry.getKey();
        
        // 保存到内存
        dataStore.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(tsKvEntry);
        
        log.trace("保存遥测数据: deviceId={}, key={}, type={}, ts={}, value={}", 
                deviceId, key, tsKvEntry.getDataType(), 
                formatTimestamp(tsKvEntry.getTs()), tsKvEntry.getValueAsString());
        
        // 可选：备份到文件
        if (enableFileBackup) {
            backupToFile(deviceId, tsKvEntry);
        }
    }

    /**
     * 保存多个遥测数据点（批量）
     */
    public void save(DeviceId deviceId, List<TsKvEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        
        for (TsKvEntry entry : entries) {
            save(deviceId, entry);
        }
        
        log.debug("批量保存遥测数据: deviceId={}, 数据点数={}", deviceId, entries.size());
    }

    /**
     * 兼容旧版API：保存JSON字符串数据
     * @deprecated 建议使用 save(DeviceId, TsKvEntry) 或 save(DeviceId, List<TsKvEntry>)
     */
    @Deprecated
    public void save(DeviceId deviceId, long timestamp, String jsonData) {
        log.warn("使用了已废弃的API save(DeviceId, long, String)，建议升级到强类型API");
        
        // 简单处理：作为一个JSON类型的数据点存储
        // 实际应该解析JSON并创建多个TsKvEntry
        if (enableFileBackup) {
            backupToFileLegacy(deviceId, timestamp, jsonData);
        }
    }

    /**
     * 备份到文件（强类型）
     */
    private void backupToFile(DeviceId deviceId, TsKvEntry entry) {
        try {
            String filename = backupDir + "/telemetry_" + deviceId.getId() + ".log";
            try (FileWriter writer = new FileWriter(filename, true)) {
                writer.write(String.format("[%s] %s=%s (%s)%n", 
                        formatTimestamp(entry.getTs()), 
                        entry.getKey(),
                        entry.getValueAsString(),
                        entry.getDataType()));
            }
        } catch (IOException e) {
            log.error("备份到文件失败", e);
        }
    }

    /**
     * 备份到文件（兼容旧版）
     */
    private void backupToFileLegacy(DeviceId deviceId, long timestamp, String jsonData) {
        try {
            String filename = backupDir + "/telemetry_" + deviceId.getId() + "_legacy.log";
            try (FileWriter writer = new FileWriter(filename, true)) {
                writer.write(String.format("[%s] %s%n", formatTimestamp(timestamp), jsonData));
            }
        } catch (IOException e) {
            log.error("备份到文件失败", e);
        }
    }

    /**
     * 查询设备的特定键的遥测数据（时间范围）
     */
    public List<TsKvEntry> query(DeviceId deviceId, String key, long startTs, long endTs) {
        Map<String, List<TsKvEntry>> deviceData = dataStore.get(deviceId);
        if (deviceData == null) {
            return new ArrayList<>();
        }
        
        List<TsKvEntry> keyData = deviceData.get(key);
        if (keyData == null) {
            return new ArrayList<>();
        }
        
        return keyData.stream()
                .filter(entry -> entry.getTs() >= startTs && entry.getTs() <= endTs)
                .collect(Collectors.toList());
    }

    /**
     * 查询设备的所有键的遥测数据（时间范围）
     */
    public Map<String, List<TsKvEntry>> queryAll(DeviceId deviceId, long startTs, long endTs) {
        Map<String, List<TsKvEntry>> deviceData = dataStore.get(deviceId);
        if (deviceData == null) {
            return new HashMap<>();
        }
        
        Map<String, List<TsKvEntry>> result = new HashMap<>();
        deviceData.forEach((key, entries) -> {
            List<TsKvEntry> filtered = entries.stream()
                    .filter(entry -> entry.getTs() >= startTs && entry.getTs() <= endTs)
                    .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                result.put(key, filtered);
            }
        });
        
        return result;
    }

    /**
     * 按数据类型查询
     */
    public List<TsKvEntry> queryByType(DeviceId deviceId, DataType dataType, long startTs, long endTs) {
        Map<String, List<TsKvEntry>> deviceData = dataStore.get(deviceId);
        if (deviceData == null) {
            return new ArrayList<>();
        }
        
        List<TsKvEntry> result = new ArrayList<>();
        deviceData.values().forEach(entries -> {
            entries.stream()
                    .filter(entry -> entry.getDataType() == dataType)
                    .filter(entry -> entry.getTs() >= startTs && entry.getTs() <= endTs)
                    .forEach(result::add);
        });
        
        return result;
    }

    /**
     * 获取设备特定键的最新数据
     */
    public TsKvEntry getLatest(DeviceId deviceId, String key) {
        Map<String, List<TsKvEntry>> deviceData = dataStore.get(deviceId);
        if (deviceData == null) {
            return null;
        }
        
        List<TsKvEntry> keyData = deviceData.get(key);
        if (keyData == null || keyData.isEmpty()) {
            return null;
        }
        
        return keyData.get(keyData.size() - 1);
    }

    /**
     * 获取设备所有键的最新数据
     */
    public Map<String, TsKvEntry> getLatestAll(DeviceId deviceId) {
        Map<String, List<TsKvEntry>> deviceData = dataStore.get(deviceId);
        if (deviceData == null) {
            return new HashMap<>();
        }
        
        Map<String, TsKvEntry> result = new HashMap<>();
        deviceData.forEach((key, entries) -> {
            if (!entries.isEmpty()) {
                result.put(key, entries.get(entries.size() - 1));
            }
        });
        
        return result;
    }

    /**
     * 获取设备的所有键名
     */
    public Set<String> getKeys(DeviceId deviceId) {
        Map<String, List<TsKvEntry>> deviceData = dataStore.get(deviceId);
        if (deviceData == null) {
            return new HashSet<>();
        }
        return new HashSet<>(deviceData.keySet());
    }

    /**
     * 获取数据统计
     */
    public void printStatistics() {
        log.info("=== 遥测数据统计（强类型模式） ===");
        dataStore.forEach((deviceId, keyData) -> {
            int totalDataPoints = keyData.values().stream()
                    .mapToInt(List::size)
                    .sum();
            log.info("设备 {}: {} 个键, {} 条数据点", deviceId, keyData.size(), totalDataPoints);
            
            keyData.forEach((key, entries) -> {
                if (!entries.isEmpty()) {
                    TsKvEntry latest = entries.get(entries.size() - 1);
                    log.info("  键 '{}': {} 条数据, 类型={}, 最新值={}, 最新时间={}", 
                            key, entries.size(), latest.getDataType(), 
                            latest.getValueAsString(), formatTimestamp(latest.getTs()));
                }
            });
        });
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }
    
    /**
     * 获取总消息数
     */
    public int getTotalMessages() {
        return dataStore.values().stream()
                .mapToInt(deviceData -> deviceData.values().stream()
                        .mapToInt(List::size).sum())
                .sum();
    }
    
    /**
     * 清空所有数据
     */
    public void clear() {
        dataStore.clear();
        log.info("遥测数据存储已清空");
    }
}
