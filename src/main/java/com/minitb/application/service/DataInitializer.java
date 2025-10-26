package com.minitb.application.service;

import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器
 * 
 * 职责：
 * - 应用启动时初始化默认数据
 * - 创建测试设备和配置文件
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final DeviceService deviceService;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("开始初始化默认数据...");
        log.info("========================================");
        
        initDefaultDevices();
        
        log.info("========================================");
        log.info("默认数据初始化完成");
        log.info("========================================");
    }
    
    /**
     * 初始化默认测试设备
     */
    private void initDefaultDevices() {
        // 检查是否已存在设备
        if (deviceService.existsByAccessToken("test-token-001")) {
            log.info("默认设备已存在，跳过初始化");
            return;
        }
        
        // MQTT设备1: 温度传感器
        Device device1 = Device.builder()
                .id(DeviceId.random())
                .name("温度传感器-01")
                .type("TemperatureSensor")
                .accessToken("test-token-001")
                .createdTime(System.currentTimeMillis())
                .build();
        deviceService.save(device1);
        log.info("创建设备: {} (token: {})", device1.getName(), device1.getAccessToken());
        
        // MQTT设备2: 湿度传感器
        Device device2 = Device.builder()
                .id(DeviceId.random())
                .name("湿度传感器-01")
                .type("HumiditySensor")
                .accessToken("test-token-002")
                .createdTime(System.currentTimeMillis())
                .build();
        deviceService.save(device2);
        log.info("创建设备: {} (token: {})", device2.getName(), device2.getAccessToken());
        
        // Prometheus 数据源设备1: Prometheus 自身进程
        Device promDevice = Device.builder()
                .id(DeviceId.random())
                .name("Prometheus进程监控")
                .type("PrometheusMonitor")
                .accessToken("test-token-prom")
                .createdTime(System.currentTimeMillis())
                .build();
        deviceService.save(promDevice);
        log.info("创建设备: {} (token: {})", promDevice.getName(), promDevice.getAccessToken());
        
        // Prometheus 数据源设备2: node_exporter 系统监控
        Device nodeDevice = Device.builder()
                .id(DeviceId.random())
                .name("系统资源监控")
                .type("NodeExporter")
                .accessToken("test-token-node")
                .createdTime(System.currentTimeMillis())
                .build();
        deviceService.save(nodeDevice);
        log.info("创建设备: {} (token: {})", nodeDevice.getName(), nodeDevice.getAccessToken());
    }
}

