package com.minitb;

import com.minitb.common.entity.DeviceProfile;
import com.minitb.common.entity.TelemetryDefinition;
import com.minitb.common.entity.protocol.HttpConfig;
import com.minitb.common.entity.protocol.MqttConfig;
import com.minitb.common.entity.protocol.PrometheusConfig;
import com.minitb.common.kv.DataType;
import com.minitb.service.DeviceProfileService;

/**
 * DeviceProfile 设计演示
 * 展示如何使用组合+接口模式定义不同协议的遥测配置
 */
public class DeviceProfileTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  DeviceProfile 设计演示");
        System.out.println("========================================\n");
        
        // 演示1: MQTT 设备配置
        demonstrateMqttProfile();
        
        // 演示2: Prometheus 设备配置
        demonstratePrometheusProfile();
        
        // 演示3: 混合协议设备配置
        demonstrateMixedProfile();
        
        // 演示4: 使用 DeviceProfileService
        demonstrateService();
    }
    
    /**
     * 演示1: MQTT 设备配置
     */
    private static void demonstrateMqttProfile() {
        System.out.println("\n【演示1】MQTT 温湿度传感器配置");
        System.out.println("----------------------------------------");
        
        DeviceProfile mqttProfile = DeviceProfile.builder()
                .id("profile-mqtt-sensor")
                .name("温湿度传感器")
                .dataSourceType(DeviceProfile.DataSourceType.MQTT)
                .strictMode(false)
                .build();
        
        // 定义温度遥测（使用 simple 方法 - 无协议配置）
        mqttProfile.addTelemetryDefinition(
                TelemetryDefinition.simple("temperature", DataType.DOUBLE)
                        .toBuilder()
                        .displayName("温度")
                        .unit("°C")
                        .build()
        );
        
        // 定义湿度遥测（使用 mqtt 方法 - 带 MQTT 配置）
        mqttProfile.addTelemetryDefinition(
                TelemetryDefinition.mqtt("humidity", DataType.LONG)
                        .toBuilder()
                        .displayName("湿度")
                        .unit("%")
                        .build()
        );
        
        printProfile(mqttProfile);
    }
    
    /**
     * 演示2: Prometheus 设备配置
     */
    private static void demonstratePrometheusProfile() {
        System.out.println("\n【演示2】Prometheus 系统监控配置");
        System.out.println("----------------------------------------");
        
        DeviceProfile promProfile = DeviceProfile.builder()
                .id("profile-prometheus-system")
                .name("系统性能监控")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)
                .build();
        
        // 简单指标
        promProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "cpu_seconds",
                        "process_cpu_seconds_total"
                ).toBuilder()
                .displayName("CPU累计时间")
                .unit("秒")
                .build()
        );
        
        // 速率计算
        promProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheusRate(
                        "cpu_rate",
                        "process_cpu_seconds_total",
                        60
                ).toBuilder()
                .displayName("CPU使用率")
                .unit("秒/分钟")
                .build()
        );
        
        // 复杂 PromQL
        promProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "memory_pct",
                        "(process_resident_memory_bytes / node_memory_MemTotal_bytes) * 100"
                ).toBuilder()
                .displayName("内存使用率")
                .unit("%")
                .build()
        );
        
        printProfile(promProfile);
    }
    
    /**
     * 演示3: 混合协议设备配置（重点！）
     */
    private static void demonstrateMixedProfile() {
        System.out.println("\n【演示3】混合协议设备配置（网关）");
        System.out.println("----------------------------------------");
        
        DeviceProfile mixedProfile = DeviceProfile.builder()
                .id("profile-smart-gateway")
                .name("智能网关")
                .description("同时支持 MQTT 推送和 Prometheus 拉取")
                .dataSourceType(DeviceProfile.DataSourceType.MQTT)  // 主要协议
                .strictMode(false)
                .build();
        
        // 1. MQTT 推送的传感器数据
        mixedProfile.addTelemetryDefinition(
                TelemetryDefinition.simple("temperature", DataType.DOUBLE)
                        .toBuilder()
                        .displayName("环境温度")
                        .unit("°C")
                        .description("来自 MQTT 传感器")
                        .build()
        );
        
        // 2. Prometheus 拉取的系统指标
        mixedProfile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "gateway_cpu",
                        "process_cpu_seconds_total{job=\"gateway\"}"
                ).toBuilder()
                .displayName("网关CPU")
                .unit("秒")
                .description("来自 Prometheus 监控")
                .build()
        );
        
        // 3. HTTP 推送的外部数据
        mixedProfile.addTelemetryDefinition(
                TelemetryDefinition.builder()
                        .key("weather")
                        .displayName("天气信息")
                        .dataType(DataType.JSON)
                        .protocolConfig(HttpConfig.builder()
                                .jsonPath("$.data.temperature")
                                .path("/api/weather")
                                .build())
                        .description("来自 HTTP API")
                        .build()
        );
        
        printProfile(mixedProfile);
    }
    
    /**
     * 演示4: 使用 DeviceProfileService
     */
    private static void demonstrateService() {
        System.out.println("\n【演示4】DeviceProfileService");
        System.out.println("----------------------------------------");
        
        DeviceProfileService service = new DeviceProfileService();
        service.printAllProfiles();
    }
    
    /**
     * 打印配置文件详情
     */
    private static void printProfile(DeviceProfile profile) {
        System.out.println("配置文件: " + profile.getName());
        System.out.println("  ID: " + profile.getId());
        System.out.println("  主要数据源: " + profile.getDataSourceType());
        System.out.println("  严格模式: " + profile.isStrictMode());
        System.out.println("  遥测定义:");
        
        for (TelemetryDefinition def : profile.getTelemetryDefinitions()) {
            System.out.println("    - " + def.getKey() + " (" + def.getDisplayName() + ")");
            System.out.println("      协议: " + def.getProtocolType());
            System.out.println("      类型: " + def.getDataType());
            System.out.println("      单位: " + def.getUnit());
            
            if (def.isPrometheus()) {
                PrometheusConfig config = def.getPrometheusConfig();
                System.out.println("      PromQL: " + config.getPromQL());
                if (config.isNeedsRateCalculation()) {
                    System.out.println("      速率窗口: " + config.getRateWindow() + "秒");
                }
            } else if (def.isMqtt()) {
                MqttConfig config = def.getMqttConfig();
                System.out.println("      QoS: " + config.getQos());
            } else if (def.isHttp()) {
                HttpConfig config = def.getHttpConfig();
                System.out.println("      JSONPath: " + config.getJsonPath());
            }
        }
    }
}

