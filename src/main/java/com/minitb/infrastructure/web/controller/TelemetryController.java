package com.minitb.infrastructure.web.controller;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.telemetry.TsKvEntry;
import com.minitb.infrastructure.web.dto.LatestTelemetryDto;
import com.minitb.infrastructure.web.dto.TelemetryDataPointDto;
import com.minitb.storage.TelemetryStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 遥测数据 REST API
 */
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {
    
    private final DeviceService deviceService;
    private final TelemetryStorage telemetryStorage;
    
    /**
     * 获取设备最新遥测数据
     * GET /api/telemetry/{deviceId}/latest
     */
    @GetMapping("/{deviceId}/latest")
    public LatestTelemetryDto getLatestTelemetry(@PathVariable String deviceId) {
        log.debug("API: 获取设备最新遥测数据: {}", deviceId);
        
        DeviceId devId = DeviceId.fromString(deviceId);
        Optional<Device> device = deviceService.findById(devId);
        
        if (device.isEmpty()) {
            throw new RuntimeException("设备不存在: " + deviceId);
        }
        
        // 获取所有遥测键
        Map<String, TsKvEntry> latestData = telemetryStorage.getLatestAll(devId);
        
        // 转换为简单的键值对
        Map<String, Object> telemetryMap = new HashMap<>();
        Long latestTimestamp = null;
        
        for (Map.Entry<String, TsKvEntry> entry : latestData.entrySet()) {
            TsKvEntry tsKv = entry.getValue();
            
            // 提取值
            Object value = extractValue(tsKv);
            if (value != null) {
                telemetryMap.put(entry.getKey(), value);
            }
            
            // 记录最新时间戳
            if (latestTimestamp == null || tsKv.getTs() > latestTimestamp) {
                latestTimestamp = tsKv.getTs();
            }
        }
        
        return LatestTelemetryDto.builder()
                .deviceId(deviceId)
                .deviceName(device.get().getName())
                .timestamp(latestTimestamp)
                .telemetry(telemetryMap)
                .build();
    }
    
    /**
     * 获取指定指标的历史数据
     * GET /api/telemetry/{deviceId}/history/{key}?duration=60
     */
    @GetMapping("/{deviceId}/history/{key}")
    public List<TelemetryDataPointDto> getHistory(
            @PathVariable String deviceId,
            @PathVariable String key,
            @RequestParam(defaultValue = "60") int duration) {
        
        log.debug("API: 获取历史数据: device={}, key={}, duration={}s", deviceId, key, duration);
        
        DeviceId devId = DeviceId.fromString(deviceId);
        
        // 计算时间范围（最近 N 秒）
        long endTs = System.currentTimeMillis();
        long startTs = endTs - (duration * 1000L);
        
        // 查询数据
        List<TsKvEntry> history = telemetryStorage.query(devId, key, startTs, endTs);
        
        return history.stream()
                .map(TelemetryDataPointDto::fromTsKvEntry)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取设备所有指标的最新值（简化版，用于首页卡片）
     * GET /api/telemetry/{deviceId}/summary
     */
    @GetMapping("/{deviceId}/summary")
    public Map<String, Object> getTelemetrySummary(@PathVariable String deviceId) {
        log.debug("API: 获取设备遥测摘要: {}", deviceId);
        
        DeviceId devId = DeviceId.fromString(deviceId);
        Map<String, TsKvEntry> latestData = telemetryStorage.getLatestAll(devId);
        
        Map<String, Object> summary = new HashMap<>();
        
        for (Map.Entry<String, TsKvEntry> entry : latestData.entrySet()) {
            Object value = extractValue(entry.getValue());
            if (value != null) {
                summary.put(entry.getKey(), value);
            }
        }
        
        return summary;
    }
    
    /**
     * 提取值（根据数据类型）
     */
    private Object extractValue(TsKvEntry entry) {
        if (entry.getDoubleValue().isPresent()) {
            return entry.getDoubleValue().get();
        } else if (entry.getLongValue().isPresent()) {
            return entry.getLongValue().get();
        } else if (entry.getStrValue().isPresent()) {
            return entry.getStrValue().get();
        } else if (entry.getBooleanValue().isPresent()) {
            return entry.getBooleanValue().get();
        }
        return null;
    }
}

