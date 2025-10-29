package com.minitb.infrastructure.web.dto;

import com.minitb.domain.telemetry.TsKvEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 遥测数据点传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryDataPointDto {
    
    private Long timestamp;      // 时间戳
    private String key;          // 指标名
    private Object value;        // 值（自动转换类型）
    private String dataType;     // 数据类型
    
    /**
     * 从时序键值对转换
     */
    public static TelemetryDataPointDto fromTsKvEntry(TsKvEntry entry) {
        Object value = null;
        
        // 根据数据类型提取值
        if (entry.getLongValue().isPresent()) {
            value = entry.getLongValue().get();
        } else if (entry.getDoubleValue().isPresent()) {
            value = entry.getDoubleValue().get();
        } else if (entry.getStrValue().isPresent()) {
            value = entry.getStrValue().get();
        } else if (entry.getBooleanValue().isPresent()) {
            value = entry.getBooleanValue().get();
        }
        
        return TelemetryDataPointDto.builder()
                .timestamp(entry.getTs())
                .key(entry.getKey())
                .value(value)
                .dataType(entry.getDataType().name())
                .build();
    }
}

