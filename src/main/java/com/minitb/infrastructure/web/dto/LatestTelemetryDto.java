package com.minitb.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 设备最新遥测数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestTelemetryDto {
    
    private String deviceId;
    private String deviceName;
    private Long timestamp;                      // 最新数据时间戳
    private Map<String, Object> telemetry;       // 键值对：指标名 → 值
}


