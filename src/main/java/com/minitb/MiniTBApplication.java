package com.minitb;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.common.entity.*;
import com.minitb.common.kv.DataType;
import com.minitb.datasource.prometheus.PrometheusDataPuller;
import com.minitb.relation.EntityRelation;
import com.minitb.relation.EntityRelationService;
import com.minitb.relation.EntitySearchDirection;
import com.minitb.relation.RelationTypeGroup;
import com.minitb.ruleengine.RuleChain;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.ruleengine.node.FilterNode;
import com.minitb.ruleengine.node.LogNode;
import com.minitb.ruleengine.node.SaveTelemetryNode;
import com.minitb.service.DeviceProfileService;
import com.minitb.storage.TelemetryStorage;
import com.minitb.transport.mqtt.MqttTransportService;
import com.minitb.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MiniTB主程序
 * 
 * 这是一个简化版的ThingsBoard，用于演示核心数据流：
 * 设备 → MQTT传输层 → TransportService → TbMsg → RuleEngine → 数据存储
 */
@Slf4j
public class MiniTBApplication {
    
    public static void main(String[] args) {
        log.info("========================================");
        log.info("   MiniTB - ThingsBoard核心数据流演示   ");
        log.info("========================================");
        
        try {
            // 1. 初始化存储层
            log.info("\n[1/8] 初始化数据存储层...");
            TelemetryStorage storage = new TelemetryStorage(true);
            
            // 2. 初始化设备配置文件服务
            log.info("\n[2/8] 初始化设备配置文件服务...");
            DeviceProfileService profileService = new DeviceProfileService();
            
            // 创建 Prometheus 监控配置文件（CPU + 内存）
            DeviceProfile promMonitorProfile = createPrometheusMonitorProfile();
            profileService.saveProfile(promMonitorProfile);
            log.info("创建 Prometheus 监控配置: {}", promMonitorProfile.getName());
            log.info("  包含 {} 个遥测定义:", promMonitorProfile.getTelemetryDefinitions().size());
            promMonitorProfile.getTelemetryDefinitions().forEach(def -> {
                if (def.isPrometheus()) {
                    log.info("    - {} ({}): {}", 
                            def.getKey(), 
                            def.getDisplayName(),
                            def.getPrometheusConfig().getPromQL());
                }
            });
            
            // 3. 初始化实体关系服务
            log.info("\n[3/8] 初始化实体关系服务...");
            EntityRelationService relationService = new EntityRelationService();
            
            // 演示实体关系的创建和查询
            demoEntityRelations(relationService);
            
            // 4. 初始化规则引擎
            log.info("\n[4/8] 初始化规则引擎...");
            RuleEngineService ruleEngineService = new RuleEngineService();
            
            // 5. 创建根规则链
            log.info("\n[5/8] 配置规则链...");
            RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
            rootRuleChain
                    .addNode(new LogNode("入口日志"))
                    // 注意: FilterNode会过滤temperature>20的数据
                    // CPU指标(process_cpu_seconds_total)不会被过滤，直接通过
                    .addNode(new FilterNode("temperature", 20.0))
                    .addNode(new LogNode("过滤后日志"))
                    .addNode(new SaveTelemetryNode(storage))
                    .addNode(new LogNode("保存完成"));
            
            ruleEngineService.setRootRuleChain(rootRuleChain);
            ruleEngineService.printRuleChains();
            
            // 6. 初始化 Actor 系统
            log.info("\n[6/9] 初始化 Actor 系统...");
            MiniTbActorSystem actorSystem = new MiniTbActorSystem(5);  // 5个线程
            log.info("Actor 系统已创建，线程池大小: 5");
            
            // 7. 初始化传输服务并集成 Actor
            log.info("\n[7/9] 初始化传输服务...");
            TransportService transportService = new TransportService(ruleEngineService);
            transportService.enableActorSystem(actorSystem);  // 启用 Actor 模式
            log.info("传输服务已启用 Actor 模式");
            
            // 8. 启动MQTT服务器
            log.info("\n[8/9] 启动MQTT服务器...");
            MqttTransportService mqttService = new MqttTransportService(1883, transportService);
            mqttService.start();
            
            // 9. 启动Prometheus数据拉取器（使用 DeviceProfile 配置）
            log.info("\n[9/9] 启动Prometheus数据拉取器（基于 DeviceProfile）...");
            String prometheusUrl = System.getenv("PROMETHEUS_URL");
            if (prometheusUrl == null || prometheusUrl.isEmpty()) {
                prometheusUrl = "http://localhost:9090";
            }
            
            PrometheusDataPuller promPuller = new PrometheusDataPuller(
                prometheusUrl, 
                transportService,
                profileService
            );
            
            // 注册监控设备1: Prometheus 自身
            promPuller.registerDeviceWithProfile(
                "localhost:9090",
                "test-token-prom",
                promMonitorProfile.getId()
            );
            
            // 创建并注册监控设备2: node_exporter 系统监控
            DeviceProfile nodeExporterProfile = createNodeExporterProfile();
            profileService.saveProfile(nodeExporterProfile);
            
            promPuller.registerDeviceWithProfile(
                "localhost:9100",
                "test-token-node",
                nodeExporterProfile.getId()
            );
            
            // 启动定时拉取
            int pullInterval = 10;
            promPuller.start(pullInterval);
            
            log.info("Prometheus数据拉取器已启动:");
            log.info("  - 目标地址: {}", prometheusUrl);
            log.info("  - 拉取间隔: {}秒", pullInterval);
            log.info("");
            log.info("  监控设备1: Prometheus 进程监控");
            log.info("    配置: {}", promMonitorProfile.getName());
            promMonitorProfile.getTelemetryDefinitions().forEach(def -> {
                if (def.isPrometheus()) {
                    log.info("      * {} ({}) - PromQL: {}", 
                            def.getKey(), def.getDisplayName(), 
                            def.getPrometheusConfig().getPromQL());
                }
            });
            log.info("");
            log.info("  监控设备2: 系统资源监控 (node_exporter)");
            log.info("    配置: {}", nodeExporterProfile.getName());
            nodeExporterProfile.getTelemetryDefinitions().forEach(def -> {
                if (def.isPrometheus()) {
                    log.info("      * {} ({}) - PromQL: {}", 
                            def.getKey(), def.getDisplayName(), 
                            def.getPrometheusConfig().getPromQL());
                }
            });
            
            // 打印使用说明
            printUsageInstructions();
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("\n正在关闭MiniTB...");
                promPuller.shutdown();
                mqttService.shutdown();
                actorSystem.shutdown();  // 关闭 Actor 系统
                ruleEngineService.shutdown();
                storage.printStatistics();
                log.info("MiniTB已关闭");
            }));
            
            // 保持运行
            log.info("\nMiniTB运行中，按Ctrl+C停止...\n");
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("MiniTB启动失败", e);
            System.exit(1);
        }
    }

    /**
     * 演示实体关系功能
     */
    private static void demoEntityRelations(EntityRelationService relationService) {
        log.info("\n>>> 演示实体关系功能 <<<");
        
        TenantId tenantId = TenantId.random();
        
        // 创建资产层次结构: 建筑 → 楼层 → 房间
        Asset building = new Asset(tenantId, "智能大厦A座", "Building");
        Asset floor1 = new Asset(tenantId, "1楼", "Floor");
        Asset floor2 = new Asset(tenantId, "2楼", "Floor");
        Asset room101 = new Asset(tenantId, "101会议室", "Room");
        Asset room201 = new Asset(tenantId, "201办公室", "Room");
        
        // 创建设备
        Device tempSensor1 = new Device("温度传感器-101", "TemperatureSensor", "token-101");
        Device tempSensor2 = new Device("温度传感器-201", "TemperatureSensor", "token-201");
        Device humiditySensor = new Device("湿度传感器-201", "HumiditySensor", "token-202");
        
        log.info("创建资产: {} x 5个", building.getName());
        log.info("创建设备: {} x 3个", tempSensor1.getName());
        
        // 建立关系
        log.info("\n>>> 建立实体关系 <<<");
        
        // 建筑包含楼层
        relationService.saveRelation(tenantId, new EntityRelation(
            building.getId().getId(), "Asset",
            floor1.getId().getId(), "Asset",
            EntityRelation.CONTAINS_TYPE
        ));
        relationService.saveRelation(tenantId, new EntityRelation(
            building.getId().getId(), "Asset",
            floor2.getId().getId(), "Asset",
            EntityRelation.CONTAINS_TYPE
        ));
        
        // 楼层包含房间
        relationService.saveRelation(tenantId, new EntityRelation(
            floor1.getId().getId(), "Asset",
            room101.getId().getId(), "Asset",
            EntityRelation.CONTAINS_TYPE
        ));
        relationService.saveRelation(tenantId, new EntityRelation(
            floor2.getId().getId(), "Asset",
            room201.getId().getId(), "Asset",
            EntityRelation.CONTAINS_TYPE
        ));
        
        // 房间包含设备
        relationService.saveRelation(tenantId, new EntityRelation(
            room101.getId().getId(), "Asset",
            tempSensor1.getId().getId(), "Device",
            EntityRelation.CONTAINS_TYPE
        ));
        relationService.saveRelation(tenantId, new EntityRelation(
            room201.getId().getId(), "Asset",
            tempSensor2.getId().getId(), "Device",
            EntityRelation.CONTAINS_TYPE
        ));
        relationService.saveRelation(tenantId, new EntityRelation(
            room201.getId().getId(), "Asset",
            humiditySensor.getId().getId(), "Device",
            EntityRelation.CONTAINS_TYPE
        ));
        
        // 打印所有关系
        relationService.printAllRelations();
        
        // 查询演示
        log.info("\n>>> 查询实体关系 <<<");
        
        // 1. 查询建筑的所有子资产（1层深度）
        List<EntityRelation> buildingChildren = relationService.findByFrom(
            tenantId, building.getId().getId(), RelationTypeGroup.COMMON
        );
        log.info("建筑 {} 包含 {} 个直接子资产", building.getName(), buildingChildren.size());
        
        // 2. 递归查询建筑下的所有实体（多层深度）
        Set<UUID> allRelated = relationService.findRelatedEntities(
            tenantId, building.getId().getId(), EntitySearchDirection.FROM, 10
        );
        log.info("建筑 {} 递归包含 {} 个实体（所有层级）", building.getName(), allRelated.size());
        
        // 3. 查询设备所属的房间（反向查询）
        List<EntityRelation> deviceParents = relationService.findByTo(
            tenantId, tempSensor1.getId().getId(), RelationTypeGroup.COMMON
        );
        log.info("设备 {} 属于 {} 个房间", tempSensor1.getName(), deviceParents.size());
        
        // 4. 检查关系是否存在
        boolean exists = relationService.checkRelation(
            tenantId,
            building.getId().getId(), "Asset",
            floor1.getId().getId(), "Asset",
            EntityRelation.CONTAINS_TYPE,
            RelationTypeGroup.COMMON
        );
        log.info("建筑 {} 包含 楼层 {}? {}", building.getName(), floor1.getName(), exists);
        
        log.info("\n实体关系演示完成！\n");
    }
    
    /**
     * 创建 Prometheus 监控配置文件
     * 监控 CPU 和内存
     */
    private static DeviceProfile createPrometheusMonitorProfile() {
        DeviceProfile profile = DeviceProfile.builder()
                .id("profile-prometheus-cpu-memory")
                .name("Prometheus CPU和内存监控")
                .description("监控 Prometheus 进程的 CPU 和内存使用情况")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)
                .createdTime(System.currentTimeMillis())
                .build();
        
        // CPU 使用时间（累计值）
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "cpu_seconds_total",
                        "process_cpu_seconds_total"
                ).toBuilder()
                .displayName("CPU累计时间")
                .dataType(DataType.DOUBLE)
                .unit("秒")
                .description("进程累计CPU使用时间")
                .build()
        );
        
        // 已分配内存
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "memory_alloc_bytes",
                        "go_memstats_alloc_bytes"
                ).toBuilder()
                .displayName("已分配内存")
                .dataType(DataType.LONG)
                .unit("字节")
                .description("Go运行时已分配的内存")
                .build()
        );
        
        // 协程数量
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "goroutines",
                        "go_goroutines"
                ).toBuilder()
                .displayName("协程数量")
                .dataType(DataType.LONG)
                .unit("个")
                .description("当前Goroutine数量")
                .build()
        );
        
        return profile;
    }
    
    /**
     * 创建 node_exporter 系统监控配置
     * 监控整个系统的 CPU、内存、磁盘等
     */
    private static DeviceProfile createNodeExporterProfile() {
        DeviceProfile profile = DeviceProfile.builder()
                .id("profile-node-exporter")
                .name("系统资源监控 (node_exporter)")
                .description("监控系统整体的 CPU、内存、磁盘使用情况")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)
                .createdTime(System.currentTimeMillis())
                .build();
        
        // 系统CPU使用率（所有核心的平均值，1分钟速率）
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "system_cpu_usage",
                        "avg(rate(node_cpu_seconds_total{mode!=\"idle\"}[1m]))"
                ).toBuilder()
                .displayName("系统CPU使用率")
                .dataType(DataType.DOUBLE)
                .unit("占比")
                .description("所有CPU核心的平均使用率（1分钟速率）")
                .build()
        );
        
        // 系统总内存
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "memory_total_bytes",
                        "node_memory_total_bytes"
                ).toBuilder()
                .displayName("系统总内存")
                .dataType(DataType.LONG)
                .unit("字节")
                .description("系统总内存大小")
                .build()
        );
        
        // 系统空闲内存
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "memory_free_bytes",
                        "node_memory_free_bytes"
                ).toBuilder()
                .displayName("空闲内存")
                .dataType(DataType.LONG)
                .unit("字节")
                .description("系统空闲内存")
                .build()
        );
        
        // 内存使用率（百分比）
        profile.addTelemetryDefinition(
                TelemetryDefinition.prometheus(
                        "memory_usage_percent",
                        "(1 - node_memory_free_bytes / node_memory_total_bytes) * 100"
                ).toBuilder()
                .displayName("内存使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .description("系统内存使用百分比")
                .build()
        );
        
        return profile;
    }
    
    private static void printUsageInstructions() {
        log.info("\n========================================");
        log.info("             使用说明                   ");
        log.info("========================================");
        log.info("\n数据来源1: MQTT推送（实时上报）");
        log.info("   mosquitto_pub -h localhost -p 1883 -u test-token-001 \\");
        log.info("     -t v1/devices/me/telemetry \\");
        log.info("     -m '{{\"temperature\":25,\"humidity\":60}}'");
        log.info("\n数据来源2: Prometheus拉取（定时采集）");
        log.info("   - 自动从Prometheus拉取设备数据");
        log.info("   - 设备ID: prom-device-001");
        log.info("   - 指标: temperature, humidity");
        log.info("   - 在Prometheus中应有如下格式的数据:");
        log.info("     temperature{{device_id=\"prom-device-001\"}} 25.0");
        log.info("     humidity{{device_id=\"prom-device-001\"}} 60.0");
        log.info("\n查看数据文件:");
        log.info("   tail -f minitb/data/telemetry_*.log");
        log.info("\n测试过滤规则:");
        log.info("   # 温度 > 20 (会被保存)");
        log.info("   mosquitto_pub -h localhost -p 1883 -u test-token-001 \\");
        log.info("     -t v1/devices/me/telemetry -m '{{\"temperature\":25}}'");
        log.info("\n   # 温度 <= 20 (会被过滤)");
        log.info("   mosquitto_pub -h localhost -p 1883 -u test-token-001 \\");
        log.info("     -t v1/devices/me/telemetry -m '{{\"temperature\":15}}'");
        log.info("\n环境变量:");
        log.info("   PROMETHEUS_URL - Prometheus服务地址 (默认: http://localhost:9090)");
        log.info("   PROMETHEUS_PULL_INTERVAL - 拉取间隔秒数 (默认: 30)");
        log.info("========================================\n");
    }
}


