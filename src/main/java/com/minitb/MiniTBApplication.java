package com.minitb;

import com.minitb.datasource.prometheus.PrometheusDataPuller;
import com.minitb.ruleengine.RuleChain;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.ruleengine.node.FilterNode;
import com.minitb.ruleengine.node.LogNode;
import com.minitb.ruleengine.node.SaveTelemetryNode;
import com.minitb.storage.TelemetryStorage;
import com.minitb.transport.mqtt.MqttTransportService;
import com.minitb.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

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
            log.info("\n[1/6] 初始化数据存储层...");
            TelemetryStorage storage = new TelemetryStorage(true);
            
            // 2. 初始化规则引擎
            log.info("\n[2/6] 初始化规则引擎...");
            RuleEngineService ruleEngineService = new RuleEngineService();
            
            // 3. 创建根规则链
            log.info("\n[3/6] 配置规则链...");
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
            log.info("\n[4/6] 初始化传输服务...");
            TransportService transportService = new TransportService(ruleEngineService);
            
            // 5. 启动MQTT服务器
            log.info("\n[5/6] 启动MQTT服务器...");
            MqttTransportService mqttService = new MqttTransportService(1883, transportService);
            mqttService.start();
            
            // 6. 启动Prometheus数据拉取器
            log.info("\n[6/6] 启动Prometheus数据拉取器...");
            String prometheusUrl = System.getenv("PROMETHEUS_URL");
            if (prometheusUrl == null || prometheusUrl.isEmpty()) {
                prometheusUrl = "http://localhost:9090";
            }
            
            PrometheusDataPuller promPuller = new PrometheusDataPuller(
                prometheusUrl, 
                transportService
            );
            
            // 注册需要拉取的设备
            // 假设在Prometheus中有一个设备ID为 "prom-device-001" 的设备
            promPuller.registerDevice(
                "prom-device-001",              // Prometheus中的设备ID
                "test-token-prom",              // MiniTB中的设备token
                Arrays.asList("temperature", "humidity")  // 拉取的指标
            );
            
            // 启动定时拉取（每30秒拉取一次）
            int pullInterval = 30;
            String intervalEnv = System.getenv("PROMETHEUS_PULL_INTERVAL");
            if (intervalEnv != null && !intervalEnv.isEmpty()) {
                try {
                    pullInterval = Integer.parseInt(intervalEnv);
                } catch (NumberFormatException e) {
                    log.warn("无效的拉取间隔: {}, 使用默认值30秒", intervalEnv);
                }
            }
            promPuller.start(pullInterval);
            
            log.info("Prometheus数据拉取器已启动:");
            log.info("  - 目标地址: {}", prometheusUrl);
            log.info("  - 拉取间隔: {}秒", pullInterval);
            log.info("  - 注册设备: {} 个", promPuller.getRegisteredDeviceCount());
            
            // 打印使用说明
            printUsageInstructions();
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("\n正在关闭MiniTB...");
                promPuller.shutdown();
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


