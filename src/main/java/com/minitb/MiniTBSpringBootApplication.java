package com.minitb;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.infrastructure.transport.mqtt.MqttTransportService;
import com.minitb.infrastructure.transport.service.TransportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MiniTB Spring Boot 主启动类
 * 
 * 职责：
 * - 启动 Spring Boot 应用
 * - 启动 MQTT 传输服务
 * - 集成 Actor 系统
 * - 启用定时任务（Prometheus 数据拉取）
 */
@SpringBootApplication
@EnableScheduling  // ← 启用定时任务
@Slf4j
public class MiniTBSpringBootApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MiniTBSpringBootApplication.class, args);
    }
    
    /**
     * 启动 MQTT 传输服务
     * 
     * @Order(10) 确保在 DataInitializer (@Order(1)) 之后执行
     */
    @Bean
    @Order(10)  // ⭐ 在数据初始化之后执行
    public CommandLineRunner startMqttTransport(
            TransportService transportService,
            MiniTbActorSystem actorSystem) {
        
        return args -> {
            log.info("\n========================================");
            log.info("   MiniTB - ThingsBoard核心数据流演示   ");
            log.info("========================================");
            
            // 设置 Actor 系统
            log.info("\n初始化 Actor 系统与设备 Actor...");
            transportService.setActorSystem(actorSystem);
            
            // 启动 MQTT 服务器
            log.info("\n启动 MQTT 服务器...");
            MqttTransportService mqttService = new MqttTransportService(1883, transportService);
            mqttService.start();
            
            log.info("\n========================================");
            log.info("✅ MiniTB 启动完成！");
            log.info("========================================");
            log.info("📡 MQTT服务器监听: mqtt://localhost:1883");
            log.info("📊 数据流程: MQTT → Actor → RuleEngine → Storage");
            log.info("💾 持久化: Spring Data JPA + H2 Database");
            log.info("🌐 H2 控制台: http://localhost:8080/h2-console");
            log.info("   JDBC URL: jdbc:h2:mem:minitb");
            log.info("\n💡 测试方法：");
            log.info("   mosquitto_pub -h localhost -p 1883 \\");
            log.info("     -t 'v1/devices/me/telemetry' \\");
            log.info("     -m '{\"temperature\":25,\"humidity\":60}'");
            log.info("========================================\n");
        };
    }
}


