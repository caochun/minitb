package com.minitb.service;

import com.minitb.common.entity.DeviceProfile;
import com.minitb.common.entity.DeviceProfileId;
import com.minitb.common.entity.TelemetryDefinition;
import com.minitb.common.kv.DataType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备配置文件服务
 * 管理设备配置文件的创建、查询、更新
 */
@Slf4j
public class DeviceProfileService {
    
    // 内存存储：profileId -> DeviceProfile（使用强类型Key）
    private final Map<DeviceProfileId, DeviceProfile> profiles = new ConcurrentHashMap<>();
    
    public DeviceProfileService() {
        log.info("设备配置文件服务初始化完成");
        initDefaultProfiles();
    }
    
    /**
     * 初始化默认的配置文件
     */
    private void initDefaultProfiles() {
        // 1. 通用 MQTT 传感器配置
        DeviceProfile mqttSensorProfile = DeviceProfile.builder()
                .id(DeviceProfileId.fromString("profile-mqtt-sensor"))
                .name("MQTT传感器标准配置")
                .description("适用于温湿度、压力等常见传感器")
                .dataSourceType(DeviceProfile.DataSourceType.MQTT)
                .strictMode(false)
                .createdTime(System.currentTimeMillis())
                .build();
        
        mqttSensorProfile.addTelemetryDefinition(
                TelemetryDefinition.simple("temperature", DataType.DOUBLE)
                        .toBuilder()
                        .displayName("温度")
                        .unit("°C")
                        .description("环境温度")
                        .build()
        );
        mqttSensorProfile.addTelemetryDefinition(
                TelemetryDefinition.simple("humidity", DataType.LONG)
                        .toBuilder()
                        .displayName("湿度")
                        .unit("%")
                        .description("相对湿度")
                        .build()
        );
        
        saveProfile(mqttSensorProfile);
        
        // 2. Prometheus 系统监控配置
        DeviceProfile prometheusSystemProfile = DeviceProfile.builder()
                .id(DeviceProfileId.fromString("profile-prometheus-system"))
                .name("Prometheus系统监控")
                .description("监控 Prometheus 自身的系统指标")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)  // 严格模式：只接受配置的指标
                .createdTime(System.currentTimeMillis())
                .build();
        
        // CPU 使用时间（累计值）
        prometheusSystemProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "cpu_seconds_total",
                        "process_cpu_seconds_total"
                ).toBuilder()
                .displayName("CPU累计时间")
                .unit("秒")
                .description("进程累计CPU使用时间")
                .build()
        );
        
        // CPU 使用率（速率计算）
        prometheusSystemProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheusRate(
                        "cpu_usage_rate",
                        "process_cpu_seconds_total",
                        60
                ).toBuilder()
                .displayName("CPU使用率")
                .unit("秒/分钟")
                .description("每分钟CPU使用时间")
                .build()
        );
        
        // 内存使用
        prometheusSystemProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "memory_bytes",
                        "process_resident_memory_bytes"
                ).toBuilder()
                .displayName("内存使用")
                .unit("字节")
                .description("进程常驻内存大小")
                .build()
        );
        
        // Goroutines 数量
        prometheusSystemProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "goroutines",
                        "go_goroutines"
                ).toBuilder()
                .displayName("协程数")
                .unit("个")
                .description("当前 Goroutine 数量")
                .build()
        );
        
        saveProfile(prometheusSystemProfile);
        
        // 3. Prometheus Web 服务器监控配置
        DeviceProfile prometheusWebProfile = DeviceProfile.builder()
                .id(DeviceProfileId.fromString("profile-prometheus-web"))
                .name("Prometheus Web服务监控")
                .description("监控 Web 服务的 HTTP 请求指标")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)
                .createdTime(System.currentTimeMillis())
                .build();
        
        // HTTP 请求总数
        prometheusWebProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "http_requests_total",
                        "prometheus_http_requests_total"
                ).toBuilder()
                .displayName("HTTP请求总数")
                .unit("次")
                .build()
        );
        
        // HTTP 请求速率（每分钟）
        prometheusWebProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "http_requests_rate",
                        "rate(prometheus_http_requests_total[1m])"
                ).toBuilder()
                .displayName("HTTP请求速率")
                .unit("请求/秒")
                .description("每秒HTTP请求数")
                .build()
        );
        
        saveProfile(prometheusWebProfile);
        
        log.info("初始化了 {} 个默认设备配置文件", profiles.size());
    }
    
    /**
     * 保存配置文件
     */
    public DeviceProfile saveProfile(DeviceProfile profile) {
        profiles.put(profile.getId(), profile);
        log.info("保存设备配置文件: id={}, name={}, 遥测数={}", 
                profile.getId(), profile.getName(), profile.getTelemetryDefinitions().size());
        return profile;
    }
    
    /**
     * 根据ID查询配置文件
     */
    public Optional<DeviceProfile> findById(DeviceProfileId profileId) {
        return Optional.ofNullable(profiles.get(profileId));
    }
    
    /**
     * 获取所有配置文件
     */
    public Map<DeviceProfileId, DeviceProfile> getAllProfiles() {
        return new ConcurrentHashMap<>(profiles);
    }
    
    /**
     * 打印所有配置文件
     */
    public void printAllProfiles() {
        log.info("=== 设备配置文件列表 ===");
        profiles.values().forEach(profile -> {
            log.info("配置文件: id={}, name={}, 数据源={}, 严格模式={}", 
                    profile.getId(), 
                    profile.getName(), 
                    profile.getDataSourceType(),
                    profile.isStrictMode());
            
            log.info("  遥测定义 ({} 个):", profile.getTelemetryDefinitions().size());
            profile.getTelemetryDefinitions().forEach(def -> {
                if (def.isPrometheus()) {
                    log.info("    - {} ({}): 协议={}, PromQL={}", 
                            def.getKey(), 
                            def.getDisplayName(),
                            def.getProtocolType(),
                            def.getPrometheusConfig().getPromQL());
                } else {
                    log.info("    - {} ({}): 协议={}, 类型={}", 
                            def.getKey(), 
                            def.getDisplayName(),
                            def.getProtocolType(),
                            def.getDataType());
                }
            });
        });
    }
}

