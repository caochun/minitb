package com.minitb;

import com.minitb.ruleengine.RuleChain;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.ruleengine.node.FilterNode;
import com.minitb.ruleengine.node.LogNode;
import com.minitb.ruleengine.node.SaveTelemetryNode;
import com.minitb.storage.TelemetryStorage;
import com.minitb.transport.mqtt.MqttTransportService;
import com.minitb.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;

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
                    .addNode(new FilterNode("temperature", 20.0)) // 只处理温度>20的数据
                    .addNode(new LogNode("过滤后日志"))
                    .addNode(new SaveTelemetryNode(storage))
                    .addNode(new LogNode("保存完成"));
            
            ruleEngineService.setRootRuleChain(rootRuleChain);
            ruleEngineService.printRuleChains();
            
            // 4. 初始化传输服务
            log.info("\n[4/5] 初始化传输服务...");
            TransportService transportService = new TransportService(ruleEngineService);
            
            // 5. 启动MQTT服务器
            log.info("\n[5/5] 启动MQTT服务器...");
            MqttTransportService mqttService = new MqttTransportService(1883, transportService);
            mqttService.start();
            
            // 打印使用说明
            printUsageInstructions();
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("\n正在关闭MiniTB...");
                mqttService.shutdown();
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

    private static void printUsageInstructions() {
        log.info("\n========================================");
        log.info("             使用说明                   ");
        log.info("========================================");
        log.info("\n1. 使用mosquitto_pub发送数据:");
        log.info("   mosquitto_pub -h localhost -p 1883 -u test-token-001 \\");
        log.info("     -t v1/devices/me/telemetry \\");
        log.info("     -m '{{\"temperature\":25,\"humidity\":60}}'");
        log.info("\n2. 查看数据文件:");
        log.info("   tail -f minitb/data/telemetry_*.log");
        log.info("\n3. 测试不同场景:");
        log.info("   # 温度 > 20 (会被保存)");
        log.info("   mosquitto_pub -h localhost -p 1883 -u test-token-001 \\");
        log.info("     -t v1/devices/me/telemetry -m '{{\"temperature\":25}}'");
        log.info("");
        log.info("   # 温度 <= 20 (会被过滤)");
        log.info("   mosquitto_pub -h localhost -p 1883 -u test-token-001 \\");
        log.info("     -t v1/devices/me/telemetry -m '{{\"temperature\":15}}'");
        log.info("========================================\n");
    }
}

