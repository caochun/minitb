package com.minitb.datasource.ipmi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.IpmiDeviceConfiguration;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.protocol.IpmiConfig;
import com.minitb.infrastructure.transport.service.TransportService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * IPMI 数据拉取器
 * 
 * 职责：
 * 1. 定时执行 ipmitool sensor list 获取传感器数据
 * 2. 根据 TelemetryDefinition 提取需要的指标
 * 3. 通过 TransportService.processTelemetry() 统一入口处理数据
 * 
 * 工作原理：
 * - 从 Device.configuration (IpmiDeviceConfiguration) 获取连接信息
 * - 从 DeviceProfile.telemetryDefinitions (IpmiConfig) 获取传感器名称
 * - 执行本地 ipmitool 命令获取数据
 * - 解析输出并转换为 JSON 遥测数据
 */
@Component
@ConditionalOnProperty(name = "minitb.datasource.ipmi.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class IpmiDataPuller {
    
    private final DeviceService deviceService;
    private final TransportService transportService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 解析 ipmitool sensor list 输出的正则表达式
    // 格式: CPU0_TEMP        | 30.000     | degrees C  | ok    | ...
    private static final Pattern SENSOR_PATTERN = Pattern.compile(
        "^([^|]+)\\s*\\|\\s*([0-9.]+|na)\\s*\\|\\s*([^|]+)\\s*\\|\\s*([^|]+)\\s*\\|.*"
    );
    
    /**
     * 定时拉取所有 IPMI 设备的数据
     */
    @Scheduled(fixedRateString = "${minitb.datasource.ipmi.pull-interval:30000}", 
               initialDelayString = "${minitb.datasource.ipmi.initial-delay:10000}")
    public void pullAllIpmiDevices() {
        log.debug("📊 开始拉取 IPMI 设备数据");
        
        try {
            // 1. 查找所有 IPMI 类型的设备
            List<Device> ipmiDevices = deviceService.findAll().stream()
                    .filter(this::isIpmiDevice)
                    .collect(Collectors.toList());
            
            if (ipmiDevices.isEmpty()) {
                log.debug("没有 IPMI 设备需要拉取数据");
                return;
            }
            
            log.info("📊 开始拉取 {} 个 IPMI 设备的数据", ipmiDevices.size());
            
            // 2. 对每个设备拉取数据
            int successCount = 0;
            int failCount = 0;
            
            for (Device device : ipmiDevices) {
                try {
                    pullDeviceData(device);
                    successCount++;
                } catch (Exception e) {
                    log.error("拉取设备 {} 的数据失败: {}", device.getName(), e.getMessage());
                    failCount++;
                }
            }
            
            log.info("✅ IPMI 数据拉取完成: 成功 {}, 失败 {}", successCount, failCount);
            
        } catch (Exception e) {
            log.error("IPMI 数据拉取整体失败", e);
        }
    }
    
    /**
     * 判断是否为 IPMI 设备
     */
    private boolean isIpmiDevice(Device device) {
        if (device.getDeviceProfileId() == null) {
            return false;
        }
        
        Optional<DeviceProfile> profileOpt = deviceService.findProfileById(device.getDeviceProfileId());
        return profileOpt.isPresent() && 
               profileOpt.get().getDataSourceType() == DeviceProfile.DataSourceType.IPMI;
    }
    
    /**
     * 拉取单个设备的数据
     */
    private void pullDeviceData(Device device) throws Exception {
        log.debug("📡 拉取设备数据: {}", device.getName());
        
        // 1. 获取设备的 DeviceProfile
        DeviceProfile profile = deviceService.findProfileById(device.getDeviceProfileId())
                .orElseThrow(() -> new IllegalStateException(
                    "设备 " + device.getName() + " 的 DeviceProfile 不存在"));
        
        // 2. 检查设备配置
        if (!(device.getConfiguration() instanceof IpmiDeviceConfiguration)) {
            log.warn("设备 {} 的配置不是 IpmiDeviceConfiguration 类型，跳过", device.getName());
            return;
        }
        
        IpmiDeviceConfiguration config = (IpmiDeviceConfiguration) device.getConfiguration();
        
        // 3. 验证配置完整性
        if (config.getHost() == null || config.getHost().isEmpty()) {
            log.warn("设备 {} 未配置 IPMI 主机地址", device.getName());
            return;
        }
        
        if (config.getUsername() == null || config.getPassword() == null) {
            log.warn("设备 {} 未配置 IPMI 认证信息", device.getName());
            return;
        }
        
        // 4. 执行 ipmitool sensor list 获取所有传感器数据
        Map<String, SensorReading> sensorData = executeSensorList(config);
        log.debug("从 {} 获取到 {} 个传感器数据", config.getHost(), sensorData.size());
        
        // 5. 根据 TelemetryDefinition 提取需要的指标
        Map<String, Object> telemetryData = new HashMap<>();
        
        for (TelemetryDefinition telemetryDef : profile.getTelemetryDefinitions()) {
            if (!"IPMI".equals(telemetryDef.getProtocolType())) {
                continue;
            }
            
            IpmiConfig ipmiConfig = (IpmiConfig) telemetryDef.getProtocolConfig();
            String sensorName = ipmiConfig.getSensorName();
            
            SensorReading reading = sensorData.get(sensorName);
            if (reading != null && reading.isValid()) {
                telemetryData.put(telemetryDef.getKey(), reading.getValue());
                log.info("  ✓ {} = {}", telemetryDef.getKey(), reading.getValue());
            } else {
                log.debug("  ✗ {} : 传感器 {} 无数据或状态异常", 
                         telemetryDef.getKey(), sensorName);
            }
        }
        
        if (telemetryData.isEmpty()) {
            log.warn("设备 {} 没有获取到有效数据", device.getName());
            return;
        }
        
        // 6. 转换为 JSON 并发送到 TransportService
        String json = objectMapper.writeValueAsString(telemetryData);
        log.debug("📤 设备 {} 拉取到 {} 个指标，调用 processTelemetry", 
                 device.getName(), telemetryData.size());
        
        transportService.processTelemetry(device.getAccessToken(), json);
    }
    
    /**
     * 执行 ipmitool sensor list 命令
     */
    private Map<String, SensorReading> executeSensorList(IpmiDeviceConfiguration config) throws Exception {
        // 构建命令
        List<String> command = new ArrayList<>();
        command.add("ipmitool");
        command.add("-I");
        command.add("lanplus");
        command.add("-H");
        command.add(config.getHost());
        command.add("-U");
        command.add(config.getUsername());
        command.add("-P");
        command.add(config.getPassword());
        
        if (config.getDriver() != null && !config.getDriver().isEmpty()) {
            // 注意：某些版本的 ipmitool 不支持 -D 参数
            // 如果需要可以添加: command.add("-D"); command.add(config.getDriver());
        }
        
        command.add("sensor");
        command.add("list");
        
        // 日志中隐藏密码
        String commandStr = String.join(" ", 
            command.stream()
                .map(s -> s.equals(config.getPassword()) ? "***" : s)
                .collect(Collectors.toList()));
        log.debug("执行命令: {}", commandStr);
        
        // 执行命令
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // 读取输出
        Map<String, SensorReading> sensorData = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                SensorReading reading = parseSensorLine(line);
                if (reading != null) {
                    sensorData.put(reading.getName(), reading);
                }
            }
        }
        
        // 等待进程结束
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ipmitool 执行失败，退出码: " + exitCode);
        }
        
        log.debug("解析到 {} 个有效传感器数据", sensorData.size());
        return sensorData;
    }
    
    /**
     * 解析单行传感器输出
     * 
     * 格式: CPU0_TEMP        | 30.000     | degrees C  | ok    | na | na | na | 95.000 | 98.000 | na
     */
    private SensorReading parseSensorLine(String line) {
        Matcher matcher = SENSOR_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        
        String name = matcher.group(1).trim();
        String valueStr = matcher.group(2).trim();
        String unit = matcher.group(3).trim();
        String status = matcher.group(4).trim();
        
        // 跳过无效值
        if ("na".equalsIgnoreCase(valueStr) || "disabled".equalsIgnoreCase(status)) {
            return null;
        }
        
        try {
            double value = Double.parseDouble(valueStr);
            return new SensorReading(name, value, unit, status);
        } catch (NumberFormatException e) {
            log.debug("无法解析传感器值: {} = {}", name, valueStr);
            return null;
        }
    }
    
    /**
     * 传感器读数
     */
    @Data
    @AllArgsConstructor
    private static class SensorReading {
        private String name;
        private double value;
        private String unit;
        private String status;
        
        /**
         * 判断读数是否有效
         * 状态为 "ok" 或 "ns" (not specified) 认为有效
         */
        public boolean isValid() {
            return "ok".equalsIgnoreCase(status) || 
                   "ns".equalsIgnoreCase(status);
        }
    }
}


