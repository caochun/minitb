package com.minitb.application.service;

import com.minitb.domain.alarm.*;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.IpmiDeviceConfiguration;
import com.minitb.domain.device.PrometheusDeviceConfiguration;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.IpmiConfig;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据初始化器
 * 
 * 职责：
 * - 应用启动时初始化默认数据
 * - 创建测试设备和配置文件
 * 
 * 注意: @Order(1) 确保在 TransportService 初始化之前执行
 */
@Component
@Order(1)  // ⭐ 优先级最高，先于其他 CommandLineRunner 执行
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
     * 初始化默认设备
     */
    private void initDefaultDevices() {
        // ========== 1. 确保 GPU 监控 DeviceProfile 存在 ==========
        DeviceProfileId gpuProfileId = ensureGpuMonitorProfileExists();
        
        // ========== 2. 确保 GPU 0 Device 存在 ==========
        ensureGpuDeviceExists(gpuProfileId, 0, "gpu-0-token", "gpu=0");
        
        // ========== 3. 确保 GPU 1 Device 存在 ==========
        ensureGpuDeviceExists(gpuProfileId, 1, "gpu-1-token", "gpu=1");
        
        // ========== 4. 如果启用了 IPMI，初始化 BMC 设备 (用于 Web 展示) ==========
        if (isIpmiEnabled()) {
            DeviceProfileId bmcProfileId = ensureBmcMonitorProfileExists();
            ensureBmcDeviceExists(bmcProfileId);
        }

        // ========== 5. 初始化 Website 可用性监控（基于 Prometheus/blackbox） ==========
        DeviceProfileId websiteProfileId = ensureWebsiteMonitorProfileExists();
        ensureWebsiteDeviceExists(websiteProfileId);
    }
    
    /**
     * 检查 IPMI 是否启用
     */
    private boolean isIpmiEnabled() {
        // 简单检查，可以从配置中读取
        return true; // 总是创建 BMC 设备用于演示
    }
    
    /**
     * 确保 GPU 监控 DeviceProfile 存在
     * @return DeviceProfile ID
     */
    private DeviceProfileId ensureGpuMonitorProfileExists() {
        // 检查是否已存在
        Optional<DeviceProfile> existing = deviceService.findProfileByName("NVIDIA GPU Monitor (DCGM)");
        if (existing.isPresent()) {
            log.info("GPU 监控配置文件已存在，使用现有配置 (ID: {})", existing.get().getId());
            return existing.get().getId();
        }
        
        // 创建新的 DeviceProfile
        log.info("创建 GPU 监控配置文件...");
        DeviceProfile gpuProfile = DeviceProfile.builder()
                .name("NVIDIA GPU Monitor (DCGM)")
                .description("NVIDIA TITAN V GPU 监控配置 - 从 Prometheus 拉取 DCGM 指标")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                // 注意: prometheusEndpoint 已移到 Device.configuration 中
                .prometheusDeviceLabelKey("gpu")
                .strictMode(true)
                .telemetryDefinitions(createGpuTelemetryDefinitions())
                .alarmRules(createGpuAlarmRules())  // 添加告警规则
                .createdTime(System.currentTimeMillis())
                .build();
        
        DeviceProfile saved = deviceService.saveProfile(gpuProfile);
        log.info("✓ DeviceProfile 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - 遥测指标数量: {}", saved.getTelemetryDefinitions().size());
        
        return saved.getId();
    }
    
    /**
     * 确保指定的 GPU Device 存在
     */
    private void ensureGpuDeviceExists(DeviceProfileId profileId, int gpuIndex, 
                                       String accessToken, String prometheusLabel) {
        // 检查是否已存在
        if (deviceService.existsByAccessToken(accessToken)) {
            log.info("GPU {} 设备已存在，跳过创建 (token: {})", gpuIndex, accessToken);
            return;
        }
        
        // 创建新的 Device
        Device gpu = Device.builder()
                .name("NVIDIA TITAN V - GPU " + gpuIndex)
                .type("NVIDIA_GPU")
                .deviceProfileId(profileId)
                .accessToken(accessToken)
                // 使用 PrometheusDeviceConfiguration
                .configuration(PrometheusDeviceConfiguration.builder()
                        .endpoint("http://192.168.30.134:9090")
                        .label(prometheusLabel)  // "gpu=0" 或 "gpu=1"
                        .build())
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device saved = deviceService.save(gpu);
        log.info("✓ Device 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - AccessToken: {}", saved.getAccessToken());
        log.info("  - Prometheus Label: {}", prometheusLabel);
    }
    
    /**
     * 创建 GPU 遥测指标定义
     */
    private List<TelemetryDefinition> createGpuTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        // 1. GPU 利用率
        defs.add(TelemetryDefinition.builder()
                .key("gpu_utilization")
                .displayName("GPU利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_GPU_UTIL")
                        .build())
                .build());
        
        // 2. 内存拷贝带宽利用率
        defs.add(TelemetryDefinition.builder()
                .key("memory_copy_utilization")
                .displayName("内存拷贝带宽利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEM_COPY_UTIL")
                        .build())
                .build());
        
        // 3. GPU 温度
        defs.add(TelemetryDefinition.builder()
                .key("gpu_temperature")
                .displayName("GPU温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_GPU_TEMP")
                        .build())
                .build());
        
        // 4. 显存温度
        defs.add(TelemetryDefinition.builder()
                .key("memory_temperature")
                .displayName("显存温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEMORY_TEMP")
                        .build())
                .build());
        
        // 5. 功耗
        defs.add(TelemetryDefinition.builder()
                .key("power_usage")
                .displayName("功耗")
                .dataType(DataType.DOUBLE)
                .unit("W")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_POWER_USAGE")
                        .build())
                .build());
        
        // 6. 已用显存
        defs.add(TelemetryDefinition.builder()
                .key("memory_used")
                .displayName("已用显存")
                .dataType(DataType.DOUBLE)
                .unit("MiB")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_FB_USED")
                        .build())
                .build());
        
        // 7. 空闲显存
        defs.add(TelemetryDefinition.builder()
                .key("memory_free")
                .displayName("空闲显存")
                .dataType(DataType.DOUBLE)
                .unit("MiB")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_FB_FREE")
                        .build())
                .build());
        
        // 8. SM 时钟频率
        defs.add(TelemetryDefinition.builder()
                .key("sm_clock")
                .displayName("SM时钟频率")
                .dataType(DataType.DOUBLE)
                .unit("MHz")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_SM_CLOCK")
                        .build())
                .build());
        
        // 9. Memory 时钟频率
        defs.add(TelemetryDefinition.builder()
                .key("memory_clock")
                .displayName("显存时钟频率")
                .dataType(DataType.DOUBLE)
                .unit("MHz")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_MEM_CLOCK")
                        .build())
                .build());
        
        // 10. SM 利用率
        defs.add(TelemetryDefinition.builder()
                .key("sm_utilization")
                .displayName("SM利用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_PROF_SM_ACTIVE")
                        .build())
                .build());
        
        // 11. PCIe TX 吞吐量
        defs.add(TelemetryDefinition.builder()
                .key("pcie_tx_throughput")
                .displayName("PCIe发送吞吐量")
                .dataType(DataType.DOUBLE)
                .unit("KB/s")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_PROF_PCIE_TX_BYTES")
                        .build())
                .build());
        
        // 12. PCIe RX 吞吐量
        defs.add(TelemetryDefinition.builder()
                .key("pcie_rx_throughput")
                .displayName("PCIe接收吞吐量")
                .dataType(DataType.DOUBLE)
                .unit("KB/s")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_PROF_PCIE_RX_BYTES")
                        .build())
                .build());
        
        // 13. ECC 单比特错误
        defs.add(TelemetryDefinition.builder()
                .key("ecc_sbe_aggregate")
                .displayName("ECC单比特错误总数")
                .dataType(DataType.DOUBLE)
                .unit("次")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_ECC_SBE_AGG_TOTAL")
                        .build())
                .build());
        
        // 14. ECC 双比特错误
        defs.add(TelemetryDefinition.builder()
                .key("ecc_dbe_aggregate")
                .displayName("ECC双比特错误总数")
                .dataType(DataType.DOUBLE)
                .unit("次")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_ECC_DBE_AGG_TOTAL")
                        .build())
                .build());
        
        // 15. 功耗上限
        defs.add(TelemetryDefinition.builder()
                .key("power_limit")
                .displayName("功耗上限")
                .dataType(DataType.DOUBLE)
                .unit("W")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_POWER_MGMT_LIMIT")
                        .build())
                .build());
        
        // 16. 风扇转速
        defs.add(TelemetryDefinition.builder()
                .key("fan_speed")
                .displayName("风扇转速")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_DEV_FAN_SPEED")
                        .build())
                .build());
        
        // 17. NVLink 吞吐量（如果有）
        defs.add(TelemetryDefinition.builder()
                .key("nvlink_bandwidth")
                .displayName("NVLink总带宽")
                .dataType(DataType.DOUBLE)
                .unit("MB/s")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("DCGM_FI_PROF_NVLINK_TX_BYTES + DCGM_FI_PROF_NVLINK_RX_BYTES")
                        .build())
                .build());
        
        return defs;
    }
    
    /**
     * 确保 BMC 监控 DeviceProfile 存在
     */
    private DeviceProfileId ensureBmcMonitorProfileExists() {
        Optional<DeviceProfile> existing = deviceService.findProfileByName("Gigabyte 服务器 BMC 监控");
        if (existing.isPresent()) {
            log.info("BMC 监控配置文件已存在，使用现有配置 (ID: {})", existing.get().getId());
            return existing.get().getId();
        }
        
        log.info("创建 BMC 监控配置文件...");
        DeviceProfile bmcProfile = DeviceProfile.builder()
                .name("Gigabyte 服务器 BMC 监控")
                .description("Gigabyte MZ72-HB2 服务器 BMC 监控配置")
                .dataSourceType(DeviceProfile.DataSourceType.IPMI)
                .strictMode(true)
                .telemetryDefinitions(createBmcTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();
        
        DeviceProfile saved = deviceService.saveProfile(bmcProfile);
        log.info("✓ BMC DeviceProfile 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - 遥测指标数量: {}", saved.getTelemetryDefinitions().size());
        
        return saved.getId();
    }
    
    /**
     * 确保 BMC Device 存在
     */
    private void ensureBmcDeviceExists(DeviceProfileId profileId) {
        String accessToken = "gigabyte-bmc-token";
        
        if (deviceService.existsByAccessToken(accessToken)) {
            log.info("BMC 设备已存在，跳过创建 (token: {})", accessToken);
            return;
        }
        
        Device bmcDevice = Device.builder()
                .name("Gigabyte MZ72-HB2 服务器")
                .type("SERVER")
                .deviceProfileId(profileId)
                .accessToken(accessToken)
                .configuration(IpmiDeviceConfiguration.builder()
                        .host("114.212.81.58")
                        .username("admin")
                        .password("OGC61700147")
                        .driver("LAN_2_0")
                        .build())
                .createdTime(System.currentTimeMillis())
                .build();
        
        Device saved = deviceService.save(bmcDevice);
        log.info("✓ BMC Device 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - AccessToken: {}", saved.getAccessToken());
        log.info("  - BMC Host: 114.212.81.58");
    }
    
    /**
     * 创建 BMC 遥测指标定义
     */
    private List<TelemetryDefinition> createBmcTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        // CPU 温度
        defs.add(TelemetryDefinition.builder()
                .key("cpu0_temperature")
                .displayName("CPU0温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder().sensorName("CPU0_TEMP").build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("cpu1_temperature")
                .displayName("CPU1温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder().sensorName("CPU1_TEMP").build())
                .build());
        
        // 风扇转速
        defs.add(TelemetryDefinition.builder()
                .key("cpu0_fan_speed")
                .displayName("CPU0风扇转速")
                .dataType(DataType.LONG)
                .unit("RPM")
                .protocolConfig(IpmiConfig.builder().sensorName("CPU0_FAN").build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("cpu1_fan_speed")
                .displayName("CPU1风扇转速")
                .dataType(DataType.LONG)
                .unit("RPM")
                .protocolConfig(IpmiConfig.builder().sensorName("CPU1_FAN").build())
                .build());
        
        // 电压
        defs.add(TelemetryDefinition.builder()
                .key("voltage_12v")
                .displayName("12V电压")
                .dataType(DataType.DOUBLE)
                .unit("V")
                .protocolConfig(IpmiConfig.builder().sensorName("P_12V").build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("voltage_5v")
                .displayName("5V电压")
                .dataType(DataType.DOUBLE)
                .unit("V")
                .protocolConfig(IpmiConfig.builder().sensorName("P_5V").build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("voltage_3_3v")
                .displayName("3.3V电压")
                .dataType(DataType.DOUBLE)
                .unit("V")
                .protocolConfig(IpmiConfig.builder().sensorName("P_3V3").build())
                .build());
        
        // 内存温度
        defs.add(TelemetryDefinition.builder()
                .key("memory_temperature")
                .displayName("内存温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder().sensorName("DIMMG0_TEMP").build())
                .build());
        
        // 主板温度（使用 M.2 温度传感器）
        defs.add(TelemetryDefinition.builder()
                .key("motherboard_temperature")
                .displayName("主板温度")
                .dataType(DataType.DOUBLE)
                .unit("°C")
                .protocolConfig(IpmiConfig.builder().sensorName("M2_AMB_TEMP").build())
                .build());
        
        return defs;
    }

    /**
     * 确保 Website 监控 DeviceProfile 存在（使用 Prometheus/blackbox 指标）
     */
    private DeviceProfileId ensureWebsiteMonitorProfileExists() {
        Optional<DeviceProfile> existing = deviceService.findProfileByName("Website Uptime Monitor");
        if (existing.isPresent()) {
            log.info("Website 监控配置文件已存在，使用现有配置 (ID: {})", existing.get().getId());
            return existing.get().getId();
        }

        log.info("创建 Website 监控配置文件...");
        DeviceProfile profile = DeviceProfile.builder()
                .name("Website Uptime Monitor")
                .description("基于 Prometheus/blackbox_exporter 的网站可用性与证书监控")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)
                .telemetryDefinitions(createWebsiteTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();

        DeviceProfile saved = deviceService.saveProfile(profile);
        log.info("✓ DeviceProfile 创建: {} (ID: {})", saved.getName(), saved.getId());
        return saved.getId();
    }

    /**
     * 创建 Website 监控遥测定义
     * 依赖 Prometheus 中 blackbox_exporter 采集的指标：probe_success / probe_http_status_code / probe_ssl_earliest_cert_expiry
     */
    private List<TelemetryDefinition> createWebsiteTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();

        // 1) 存活/可达性 (1/0)
        defs.add(TelemetryDefinition.builder()
                .key("website_alive")
                .displayName("网站可达")
                .dataType(DataType.DOUBLE)
                .unit("")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("probe_success{job=\"blackbox-http\", instance=\"http://www.js.sgcc.com.cn\"}")
                        .build())
                .build());

        // 2) HTTP 状态码
        defs.add(TelemetryDefinition.builder()
                .key("http_status_code")
                .displayName("HTTP状态码")
                .dataType(DataType.DOUBLE)
                .unit("")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("probe_http_status_code{job=\"blackbox-http\", instance=\"http://www.js.sgcc.com.cn\"}")
                        .build())
                .build());

        // 3) 证书剩余天数（如为 HTTP 则可能无意义）
        defs.add(TelemetryDefinition.builder()
                .key("ssl_days_to_expiry")
                .displayName("证书剩余天数")
                .dataType(DataType.DOUBLE)
                .unit("days")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(probe_ssl_earliest_cert_expiry{job=\"blackbox-http\", instance=\"http://www.js.sgcc.com.cn\"} - time()) / 86400")
                        .build())
                .build());

        return defs;
    }

    /**
     * 确保 Website 设备存在
     */
    private void ensureWebsiteDeviceExists(DeviceProfileId profileId) {
        String accessToken = "website-js-sgcc-token";

        if (deviceService.existsByAccessToken(accessToken)) {
            log.info("Website 设备已存在，跳过创建 (token: {})", accessToken);
            return;
        }

        Device device = Device.builder()
                .name("JS SGCC Website")
                .type("WEBSITE")
                .deviceProfileId(profileId)
                .accessToken(accessToken)
                .configuration(PrometheusDeviceConfiguration.builder()
                        .endpoint("http://localhost:9090")
                        // 使用 blackbox 的 instance 标签进行绑定
                        .label("instance=http://www.js.sgcc.com.cn")
                        .build())
                .createdTime(System.currentTimeMillis())
                .build();

        Device saved = deviceService.save(device);
        log.info("✓ Website Device 创建: {} (ID: {})", saved.getName(), saved.getId());
        log.info("  - AccessToken: {}", saved.getAccessToken());
        log.info("  - Prometheus Endpoint: http://localhost:9090");
    }
    
    /**
     * 创建 GPU 告警规则
     */
    private List<AlarmRule> createGpuAlarmRules() {
        List<AlarmRule> rules = new ArrayList<>();
        
        // 1. GPU 温度告警（多级）
        AlarmRule gpuTempRule = AlarmRule.builder()
                .id("gpu_temperature_alarm")
                .alarmType("GPU High Temperature")
                .createConditions(Map.of(
                    // CRITICAL: > 85°C 持续 30 秒
                    AlarmSeverity.CRITICAL, AlarmCondition.duration(30,
                        AlarmConditionFilter.greaterThan("gpu_temperature", 85.0)
                    ),
                    // MAJOR: > 80°C 持续 30 秒
                    AlarmSeverity.MAJOR, AlarmCondition.duration(30,
                        AlarmConditionFilter.greaterThan("gpu_temperature", 80.0)
                    ),
                    // WARNING: > 75°C 持续 30 秒
                    AlarmSeverity.WARNING, AlarmCondition.duration(30,
                        AlarmConditionFilter.greaterThan("gpu_temperature", 75.0)
                    )
                ))
                .clearCondition(AlarmCondition.simple(
                    AlarmConditionFilter.lessThan("gpu_temperature", 70.0)
                ))
                .build();
        rules.add(gpuTempRule);
        
        // 2. 显存温度告警
        AlarmRule memoryTempRule = AlarmRule.builder()
                .id("memory_temperature_alarm")
                .alarmType("Memory High Temperature")
                .createConditions(Map.of(
                    AlarmSeverity.CRITICAL, AlarmCondition.duration(30,
                        AlarmConditionFilter.greaterThan("memory_temperature", 90.0)
                    ),
                    AlarmSeverity.WARNING, AlarmCondition.duration(30,
                        AlarmConditionFilter.greaterThan("memory_temperature", 85.0)
                    )
                ))
                .clearCondition(AlarmCondition.simple(
                    AlarmConditionFilter.lessThan("memory_temperature", 80.0)
                ))
                .build();
        rules.add(memoryTempRule);
        
        // 3. 功耗告警
        AlarmRule powerRule = AlarmRule.builder()
                .id("power_usage_alarm")
                .alarmType("High Power Usage")
                .createConditions(Map.of(
                    AlarmSeverity.WARNING, AlarmCondition.simple(
                        AlarmConditionFilter.greaterThan("power_usage", 200.0)
                    )
                ))
                .clearCondition(AlarmCondition.simple(
                    AlarmConditionFilter.lessThan("power_usage", 180.0)
                ))
                .build();
        rules.add(powerRule);
        
        log.info("  - 告警规则: GPU 温度、显存温度、功耗 (3 个规则)");
        return rules;
    }
}


