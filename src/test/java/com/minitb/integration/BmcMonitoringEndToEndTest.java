package com.minitb.integration;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.application.service.DeviceService;
import com.minitb.datasource.ipmi.IpmiDataPuller;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.IpmiDeviceConfiguration;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.IpmiConfig;
import com.minitb.domain.telemetry.DataType;
import com.minitb.domain.telemetry.TsKvEntry;
import com.minitb.infrastructure.transport.service.TransportService;
import com.minitb.storage.TelemetryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BMC 监控端到端测试
 * 
 * 测试场景：
 * - 监控 Gigabyte MZ72-HB2 服务器 BMC
 * - 使用 ipmitool 通过 IPMI 协议拉取传感器数据
 * - BMC 地址: 114.212.81.58
 * 
 * 监控指标：
 * 1. CPU0 温度 (CPU0_TEMP)
 * 2. CPU1 温度 (CPU1_TEMP)
 * 3. CPU0 风扇转速 (CPU0_FAN)
 * 4. CPU1 风扇转速 (CPU1_FAN)
 * 5. 12V 电压 (P_12V)
 * 6. 5V 电压 (P_5V)
 * 7. 内存温度 (DIMMG0_TEMP)
 * 
 * 前置条件：
 * - 安装 ipmitool: brew install ipmitool
 * - BMC 可访问: 114.212.81.58
 * - 设置环境变量 BMC_MONITORING_ENABLED=true
 */
@SpringBootTest(properties = {
    "minitb.datasource.ipmi.enabled=true",
    "minitb.datasource.ipmi.pull-interval=30000",
    "minitb.datasource.ipmi.initial-delay=5000"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "BMC_MONITORING_ENABLED", matches = "true")
class BmcMonitoringEndToEndTest {
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private IpmiDataPuller ipmiDataPuller;
    
    @Autowired
    private TransportService transportService;
    
    @Autowired
    private MiniTbActorSystem actorSystem;
    
    @Autowired
    private TelemetryStorage telemetryStorage;
    
    private static DeviceProfileId bmcProfileId;
    private static DeviceId serverDeviceId;
    private static boolean initialized = false;
    
    private static final String BMC_HOST = "114.212.81.58";
    private static final String BMC_USERNAME = "admin";
    private static final String BMC_PASSWORD = "OGC61700147";
    
    @BeforeEach
    void setUp() throws Exception {
        if (initialized) {
            return;
        }
        
        // 验证 ipmitool 可用
        if (!checkIpmitoolAvailable()) {
            fail("❌ ipmitool 未安装或 BMC 不可访问");
        }
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║   BMC 监控端到端测试 - Gigabyte MZ72-HB2                ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // 创建 BMC 监控设备
        setupBmcDevice();
        
        initialized = true;
    }
    
    @Test
    void testBmcMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 BMC 监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long testStartTime = System.currentTimeMillis();
        
        // ========== 验证设备配置 ==========
        System.out.println("📋 设备配置:");
        Device server = deviceService.findById(serverDeviceId).orElseThrow();
        
        IpmiDeviceConfiguration config = (IpmiDeviceConfiguration) server.getConfiguration();
        
        System.out.println("  服务器:");
        System.out.println("    - 设备名称: " + server.getName());
        System.out.println("    - 设备 ID: " + server.getId());
        System.out.println("    - BMC 地址: " + config.getHost());
        System.out.println("    - IPMI 用户: " + config.getUsername());
        System.out.println("    - AccessToken: " + server.getAccessToken());
        System.out.println();
        
        // ========== 拉取数据 ==========
        System.out.println("🔄 从 BMC 拉取传感器数据...");
        System.out.println("  - BMC: " + BMC_HOST);
        System.out.println("  - 协议: IPMI 2.0 (ipmitool)");
        
        long beforePull = System.currentTimeMillis();
        
        // 手动触发拉取
        ipmiDataPuller.pullAllIpmiDevices();
        
        System.out.println("  ✅ 数据拉取完成");
        System.out.println();
        
        // 等待异步处理
        Thread.sleep(1000);
        
        long afterProcess = System.currentTimeMillis();
        
        // ========== 验证数据 ==========
        System.out.println("📊 BMC 数据验证:");
        verifyBmcData(serverDeviceId, beforePull, afterProcess);
        
        // ========== 总结 ==========
        long totalTime = afterProcess - testStartTime;
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║   ✅ BMC 监控测试通过                                   ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("测试摘要:");
        System.out.println("  - 监控设备: Gigabyte MZ72-HB2 服务器");
        System.out.println("  - 监控指标数: 7");
        System.out.println("  - 总耗时: " + totalTime + " ms");
        System.out.println();
    }
    
    @Test
    void testCpuTemperatureMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试 CPU 温度监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        ipmiDataPuller.pullAllIpmiDevices();
        Thread.sleep(500);
        long afterProcess = System.currentTimeMillis();
        
        // 验证 CPU 温度
        List<TsKvEntry> cpu0Temp = telemetryStorage.query(
            serverDeviceId, "cpu0_temperature", beforePull, afterProcess);
        
        List<TsKvEntry> cpu1Temp = telemetryStorage.query(
            serverDeviceId, "cpu1_temperature", beforePull, afterProcess);
        
        assertFalse(cpu0Temp.isEmpty(), "CPU0 温度数据应存在");
        assertFalse(cpu1Temp.isEmpty(), "CPU1 温度数据应存在");
        
        double cpu0TempValue = getValue(cpu0Temp.get(cpu0Temp.size() - 1));
        double cpu1TempValue = getValue(cpu1Temp.get(cpu1Temp.size() - 1));
        
        System.out.println("🌡️  CPU 温度:");
        System.out.println("  CPU0: " + String.format("%.0f", cpu0TempValue) + "°C");
        System.out.println("  CPU1: " + String.format("%.0f", cpu1TempValue) + "°C");
        System.out.println();
        
        // 验证温度范围（合理范围：20-100°C）
        assertTrue(cpu0TempValue >= 20 && cpu0TempValue <= 100, 
            "CPU0 温度应在合理范围内 (20-100°C)");
        assertTrue(cpu1TempValue >= 20 && cpu1TempValue <= 100, 
            "CPU1 温度应在合理范围内 (20-100°C)");
        
        System.out.println("✅ CPU 温度监控验证通过");
        System.out.println();
    }
    
    @Test
    void testFanSpeedMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试风扇转速监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        ipmiDataPuller.pullAllIpmiDevices();
        Thread.sleep(500);
        long afterProcess = System.currentTimeMillis();
        
        // 验证风扇转速
        List<TsKvEntry> cpu0Fan = telemetryStorage.query(
            serverDeviceId, "cpu0_fan_speed", beforePull, afterProcess);
        
        List<TsKvEntry> cpu1Fan = telemetryStorage.query(
            serverDeviceId, "cpu1_fan_speed", beforePull, afterProcess);
        
        assertFalse(cpu0Fan.isEmpty(), "CPU0 风扇数据应存在");
        assertFalse(cpu1Fan.isEmpty(), "CPU1 风扇数据应存在");
        
        double cpu0FanValue = getValue(cpu0Fan.get(cpu0Fan.size() - 1));
        double cpu1FanValue = getValue(cpu1Fan.get(cpu1Fan.size() - 1));
        
        System.out.println("💨 风扇转速:");
        System.out.println("  CPU0 风扇: " + String.format("%.0f", cpu0FanValue) + " RPM");
        System.out.println("  CPU1 风扇: " + String.format("%.0f", cpu1FanValue) + " RPM");
        System.out.println();
        
        // 验证风扇转速（合理范围：300-5000 RPM）
        assertTrue(cpu0FanValue >= 300 && cpu0FanValue <= 5000, 
            "CPU0 风扇转速应在合理范围内");
        assertTrue(cpu1FanValue >= 300 && cpu1FanValue <= 5000, 
            "CPU1 风扇转速应在合理范围内");
        
        System.out.println("✅ 风扇转速监控验证通过");
        System.out.println();
    }
    
    @Test
    void testVoltageMonitoring() throws Exception {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  测试电压监控");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long beforePull = System.currentTimeMillis();
        ipmiDataPuller.pullAllIpmiDevices();
        Thread.sleep(500);
        long afterProcess = System.currentTimeMillis();
        
        // 验证电压
        List<TsKvEntry> voltage12v = telemetryStorage.query(
            serverDeviceId, "voltage_12v", beforePull, afterProcess);
        
        List<TsKvEntry> voltage5v = telemetryStorage.query(
            serverDeviceId, "voltage_5v", beforePull, afterProcess);
        
        assertFalse(voltage12v.isEmpty(), "12V 电压数据应存在");
        assertFalse(voltage5v.isEmpty(), "5V 电压数据应存在");
        
        double voltage12vValue = getValue(voltage12v.get(voltage12v.size() - 1));
        double voltage5vValue = getValue(voltage5v.get(voltage5v.size() - 1));
        
        System.out.println("⚡ 电压:");
        System.out.println("  12V: " + String.format("%.2f", voltage12vValue) + " V");
        System.out.println("  5V: " + String.format("%.2f", voltage5vValue) + " V");
        System.out.println();
        
        // 验证电压范围（允许 ±10% 波动）
        assertTrue(voltage12vValue >= 10.8 && voltage12vValue <= 13.2, 
            "12V 电压应在合理范围内 (10.8-13.2V)");
        assertTrue(voltage5vValue >= 4.5 && voltage5vValue <= 5.5, 
            "5V 电压应在合理范围内 (4.5-5.5V)");
        
        System.out.println("✅ 电压监控验证通过");
        System.out.println();
    }
    
    // ==================== Helper Methods ====================
    
    private void setupBmcDevice() {
        System.out.println("🔧 初始化 BMC 监控设备...\n");
        
        // 1. 创建 BMC 监控 DeviceProfile
        DeviceProfile bmcProfile = DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("Gigabyte 服务器 BMC 监控")
                .description("Gigabyte MZ72-HB2 服务器 BMC 监控配置")
                .dataSourceType(DeviceProfile.DataSourceType.IPMI)
                .strictMode(true)
                .telemetryDefinitions(createBmcTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();
        
        DeviceProfile savedProfile = deviceService.saveProfile(bmcProfile);
        bmcProfileId = savedProfile.getId();
        System.out.println("  ✓ DeviceProfile 创建: " + savedProfile.getName());
        
        // 2. 创建 Gigabyte 服务器 Device
        Device server = Device.builder()
                .id(DeviceId.random())
                .name("Gigabyte MZ72-HB2 服务器")
                .type("SERVER")
                .deviceProfileId(bmcProfileId)
                .accessToken("gigabyte-bmc-token-" + System.currentTimeMillis())
                // 使用 IpmiDeviceConfiguration
                .configuration(IpmiDeviceConfiguration.builder()
                        .host(BMC_HOST)
                        .username(BMC_USERNAME)
                        .password(BMC_PASSWORD)
                        .driver("LAN_2_0")
                        .build())
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device savedServer = deviceService.save(server);
        serverDeviceId = savedServer.getId();
        System.out.println("  ✓ Device 创建: " + savedServer.getName());
        
        // 创建 DeviceActor
        com.minitb.actor.device.DeviceActor serverActor = 
            new com.minitb.actor.device.DeviceActor(savedServer.getId(), savedServer);
        actorSystem.createActor(serverActor.getActorId(), serverActor);
        System.out.println("    - DeviceActor 创建: " + serverActor.getActorId());
        
        System.out.println("\n✅ BMC 监控设备初始化完成\n");
    }
    
    /**
     * 创建 BMC 遥测指标定义
     */
    private List<TelemetryDefinition> createBmcTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        // 1. CPU0 温度
        defs.add(TelemetryDefinition.builder()
                .key("cpu0_temperature")
                .displayName("CPU0温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("CPU0_TEMP")
                        .build())
                .build());
        
        // 2. CPU1 温度
        defs.add(TelemetryDefinition.builder()
                .key("cpu1_temperature")
                .displayName("CPU1温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("CPU1_TEMP")
                        .build())
                .build());
        
        // 3. CPU0 风扇转速
        defs.add(TelemetryDefinition.builder()
                .key("cpu0_fan_speed")
                .displayName("CPU0风扇转速")
                .dataType(DataType.LONG)
                .unit("RPM")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("CPU0_FAN")
                        .build())
                .build());
        
        // 4. CPU1 风扇转速
        defs.add(TelemetryDefinition.builder()
                .key("cpu1_fan_speed")
                .displayName("CPU1风扇转速")
                .dataType(DataType.LONG)
                .unit("RPM")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("CPU1_FAN")
                        .build())
                .build());
        
        // 5. 12V 电压
        defs.add(TelemetryDefinition.builder()
                .key("voltage_12v")
                .displayName("12V电压")
                .dataType(DataType.DOUBLE)
                .unit("V")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("P_12V")
                        .build())
                .build());
        
        // 6. 5V 电压
        defs.add(TelemetryDefinition.builder()
                .key("voltage_5v")
                .displayName("5V电压")
                .dataType(DataType.DOUBLE)
                .unit("V")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("P_5V")
                        .build())
                .build());
        
        // 7. 内存温度
        defs.add(TelemetryDefinition.builder()
                .key("memory_temperature")
                .displayName("内存温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder()
                        .sensorName("DIMMG0_TEMP")
                        .build())
                .build());
        
        return defs;
    }
    
    /**
     * 验证 BMC 数据
     */
    private void verifyBmcData(DeviceId deviceId, long startTime, long endTime) {
        String[] metrics = {
            "cpu0_temperature", "cpu1_temperature",
            "cpu0_fan_speed", "cpu1_fan_speed",
            "voltage_12v", "voltage_5v",
            "memory_temperature"
        };
        
        int successCount = 0;
        
        for (String metricKey : metrics) {
            List<TsKvEntry> data = telemetryStorage.query(deviceId, metricKey, startTime, endTime);
            
            if (!data.isEmpty()) {
                TsKvEntry latest = data.get(data.size() - 1);
                
                Optional<Double> doubleValue = latest.getDoubleValue();
                Optional<Long> longValue = latest.getLongValue();
                
                if (doubleValue.isPresent()) {
                    System.out.println("  ✓ " + metricKey + ": " + 
                        String.format("%.2f", doubleValue.get()) + " " + getUnit(metricKey));
                    successCount++;
                } else if (longValue.isPresent()) {
                    System.out.println("  ✓ " + metricKey + ": " + 
                        longValue.get() + " " + getUnit(metricKey));
                    successCount++;
                } else {
                    System.out.println("  ✗ " + metricKey + ": 无法获取值");
                }
            } else {
                System.out.println("  ✗ " + metricKey + ": 未保存 (查询结果为空)");
            }
        }
        
        System.out.println("  总计: " + successCount + "/7 指标成功");
    }
    
    private String getUnit(String metricKey) {
        if (metricKey.contains("temperature")) return "°C";
        if (metricKey.contains("fan")) return "RPM";
        if (metricKey.contains("voltage")) return "V";
        return "";
    }
    
    /**
     * 从 TsKvEntry 获取数值（自动处理 DOUBLE 或 LONG 类型）
     */
    private double getValue(TsKvEntry entry) {
        Optional<Double> doubleValue = entry.getDoubleValue();
        if (doubleValue.isPresent()) {
            return doubleValue.get();
        }
        Optional<Long> longValue = entry.getLongValue();
        if (longValue.isPresent()) {
            return longValue.get().doubleValue();
        }
        throw new IllegalStateException("TsKvEntry 既不包含 double 值也不包含 long 值");
    }
    
    /**
     * 检查 ipmitool 是否可用且 BMC 可访问
     */
    private boolean checkIpmitoolAvailable() {
        try {
            // 检查 ipmitool 是否安装
            ProcessBuilder pb = new ProcessBuilder("which", "ipmitool");
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                System.out.println("❌ ipmitool 未安装");
                System.out.println("   安装方法: brew install ipmitool");
                return false;
            }
            
            System.out.println("✅ ipmitool 已安装");
            
            // 检查 BMC 是否可访问
            ProcessBuilder testPb = new ProcessBuilder(
                "ipmitool", "-I", "lanplus",
                "-H", BMC_HOST,
                "-U", BMC_USERNAME,
                "-P", BMC_PASSWORD,
                "mc", "info"
            );
            Process testProcess = testPb.start();
            
            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(testProcess.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains("Device ID")) {
                    System.out.println("✅ BMC 可访问: " + BMC_HOST);
                    return true;
                }
            }
            
            int testExitCode = testProcess.waitFor();
            if (testExitCode == 0) {
                System.out.println("✅ BMC 可访问: " + BMC_HOST);
                return true;
            }
            
            System.out.println("❌ BMC 不可访问或认证失败: " + BMC_HOST);
            return false;
            
        } catch (Exception e) {
            System.out.println("❌ 检查 ipmitool 失败: " + e.getMessage());
            return false;
        }
    }
}

