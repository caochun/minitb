package com.minitb.dao.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.device.TelemetryDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备配置实体类 - 数据库映射
 * 
 * 关键点：处理复杂对象的序列化
 * - telemetryDefinitions (List<TelemetryDefinition>) → JSON 字符串
 * - dataSourceType (Enum) → String
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProfileEntity {
    
    private String id;
    private String name;
    private String description;
    private String dataSourceType;
    private Boolean strictMode;
    
    /**
     * 遥测定义列表（序列化为 JSON 存储）
     */
    private String telemetryDefinitionsJson;
    
    private Long createdTime;
    
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    /**
     * Domain → Entity
     * 序列化复杂对象
     */
    public static DeviceProfileEntity fromDomain(DeviceProfile profile) {
        if (profile == null) {
            return null;
        }
        
        // 序列化遥测定义列表
        String telemetryJson = null;
        if (profile.getTelemetryDefinitions() != null) {
            telemetryJson = gson.toJson(profile.getTelemetryDefinitions());
        }
        
        // Enum → String
        String dataSourceType = null;
        if (profile.getDataSourceType() != null) {
            dataSourceType = profile.getDataSourceType().name();
        }
        
        return DeviceProfileEntity.builder()
                .id(profile.getId() != null ? profile.getId().toString() : null)
                .name(profile.getName())
                .description(profile.getDescription())
                .dataSourceType(dataSourceType)
                .strictMode(profile.isStrictMode())
                .telemetryDefinitionsJson(telemetryJson)
                .createdTime(profile.getCreatedTime())
                .build();
    }
    
    /**
     * Entity → Domain
     * 反序列化复杂对象
     */
    public DeviceProfile toDomain() {
        // 反序列化遥测定义列表
        List<TelemetryDefinition> definitions = null;
        if (telemetryDefinitionsJson != null && !telemetryDefinitionsJson.isEmpty()) {
            definitions = gson.fromJson(
                telemetryDefinitionsJson,
                new TypeToken<List<TelemetryDefinition>>(){}.getType()
            );
        }
        
        // String → Enum
        DeviceProfile.DataSourceType dataSourceTypeEnum = null;
        if (dataSourceType != null) {
            dataSourceTypeEnum = DeviceProfile.DataSourceType.valueOf(dataSourceType);
        }
        
        return DeviceProfile.builder()
                .id(id != null ? DeviceProfileId.fromString(id) : null)
                .name(name)
                .description(description)
                .dataSourceType(dataSourceTypeEnum)
                .strictMode(strictMode != null ? strictMode : false)
                .telemetryDefinitions(definitions)
                .createdTime(createdTime != null ? createdTime : 0L)
                .build();
    }
}

