package com.minitb;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.domain.rule.RuleChain;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.infrastructure.rule.FilterNode;
import com.minitb.infrastructure.rule.LogNode;
import com.minitb.infrastructure.rule.SaveTelemetryNode;
import com.minitb.storage.TelemetryStorage;
import com.minitb.infrastructure.transport.mqtt.MqttTransportService;
import com.minitb.infrastructure.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;

/**
 * MiniTB主程序
 * 
 * 这是一个简化版的ThingsBoard，用于演示核心数据流：
 * 设备 → MQTT传输层 → TransportService → Message → RuleEngine → 数据存储
 */
@Slf4j
public class MiniTBApplication {
    
    public static void main(String[] args) {
        log.info("========================================");
        log.info("   MiniTB - ThingsBoard核心数据流演示   ");
        log.info("========================================");
        
        try {
            // 1. 初始化存储层
            log.info("\n[1/5] 初始化数据存储层...");
            TelemetryStorage storage = new TelemetryStorage(true);
            
            // 2. 初始化规则引擎
            log.info("\n[2/5] 初始化规则引擎...");
            RuleEngineService ruleEngineService = new RuleEngineService();
            
            // 3. 创建根规则链
            log.info("\n[3/5] 配置规则链...");
            RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
            rootRuleChain
                    .addNode(new LogNode("入口日志"))
                    .addNode(new FilterNode("temperature", 20.0))
                    .addNode(new LogNode("过滤后日志"))
                    .addNode(new SaveTelemetryNode(storage))
                    .addNode(new LogNode("保存完成"));
            
            ruleEngineService.setRootRuleChain(rootRuleChain);
            ruleEngineService.printRuleChains();
            
            // 4. 初始化 Actor 系统
            log.info("\n[4/5] 初始化 Actor 系统...");
            MiniTbActorSystem actorSystem = new MiniTbActorSystem(5);
            log.info("Actor 系统已创建，线程池大小: 5");
            
            // 5. 初始化传输服务并启动 MQTT
            log.info("\n[5/5] 启动MQTT服务器...");
            // TODO: TransportService 需要 DeviceService，待 Spring Boot 完全集成后启用
            // TransportService transportService = new TransportService(deviceService, ruleEngineService);
            // transportService.setActorSystem(actorSystem);
            // log.info("传输服务已设置 Actor 系统");
            
            // MqttTransportService mqttService = new MqttTransportService(1883, transportService);
            // mqttService.start();
            
            log.info("\n========================================");
            log.info("✅ MiniTB 核心组件初始化完成！");
            log.info("========================================");
            log.info("📊 已初始化: Storage → RuleEngine → Actor System");
            log.info("⚠️  MQTT 传输层待 Spring Boot 完全集成后启用");
            log.info("========================================\n");
            
        } catch (Exception e) {
            log.error("MiniTB启动失败", e);
            System.exit(1);
        }
    }
}
