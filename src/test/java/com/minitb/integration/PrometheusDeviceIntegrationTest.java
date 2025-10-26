package com.minitb.integration;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prometheus 设备集成测试
 * 
 * 测试场景：
 * 1. 创建一个 Prometheus 类型的 DeviceProfile
 * 2. 配置 CPU、内存、磁盘三个指标
 * 3. 创建关联该 Profile 的 Device
 * 4. 验证配置正确持久化
 * 
 * 注意：此测试不实际连接 Prometheus，只测试配置的创建和持久化
 */
@SpringBootTest
@ActiveProfiles("test")
class PrometheusDeviceIntegrationTest {
    
    @Autowired
    private DeviceService deviceService;
    
    private DeviceProfileId prometheusProfileId;
    
    @BeforeEach
    void setUp() {
        // 创建 Prometheus 类型的 DeviceProfile
        DeviceProfile prometheusProfile = createPrometheusMonitorProfile();
        DeviceProfile savedProfile = deviceService.saveProfile(prometheusProfile);
        prometheusProfileId = savedProfile.getId();
        
        assertNotNull(prometheusProfileId, "DeviceProfile 应该成功保存");
    }
    
    @Test
    void testCreatePrometheusDevice() {
        // Given - 创建设备
        Device device = Device.builder()
                .id(DeviceId.random())
                .name("Prometheus Monitor Server-01")
                .type("SERVER_MONITOR")
                .deviceProfileId(prometheusProfileId)
                .accessToken("prometheus-server-01-token")
                .createdTime(System.currentTimeMillis())
                .build();
        
        // When - 保存设备
        Device savedDevice = deviceService.save(device);
        
        // Then - 验证设备创建成功
        assertNotNull(savedDevice);
        assertNotNull(savedDevice.getId());
        assertEquals("Prometheus Monitor Server-01", savedDevice.getName());
        assertEquals("SERVER_MONITOR", savedDevice.getType());
        assertEquals(prometheusProfileId, savedDevice.getDeviceProfileId());
        
        // 验证设备可以通过 ID 查询
        Optional<Device> foundDevice = deviceService.findById(savedDevice.getId());
        assertTrue(foundDevice.isPresent());
        assertEquals(savedDevice.getId(), foundDevice.get().getId());
    }
    
    @Test
    void testPrometheusProfileConfiguration() {
        // When - 查询 Prometheus Profile
        Optional<DeviceProfile> profileOpt = deviceService.findProfileById(prometheusProfileId);
        
        // Then - 验证 Profile 配置
        assertTrue(profileOpt.isPresent());
        DeviceProfile profile = profileOpt.get();
        
        assertEquals("Prometheus Server Monitor", profile.getName());
        assertEquals(DeviceProfile.DataSourceType.PROMETHEUS, profile.getDataSourceType());
        
        // 验证遥测定义
        List<TelemetryDefinition> telemetryDefs = profile.getTelemetryDefinitions();
        assertNotNull(telemetryDefs);
        assertEquals(3, telemetryDefs.size(), "应该有 CPU、内存、磁盘三个指标");
        
        // 验证 CPU 指标
        TelemetryDefinition cpuDef = findTelemetryByKey(telemetryDefs, "cpu_usage_percent");
        assertNotNull(cpuDef, "应该有 CPU 使用率指标");
        assertEquals("CPU使用率", cpuDef.getDisplayName());
        assertEquals(DataType.DOUBLE, cpuDef.getDataType());
        assertEquals("%", cpuDef.getUnit());
        assertTrue(cpuDef.isPrometheus(), "应该是 Prometheus 协议");
        
        PrometheusConfig cpuConfig = cpuDef.getPrometheusConfig();
        assertNotNull(cpuConfig);
        assertTrue(cpuConfig.getPromQL().contains("rate"));
        assertTrue(cpuConfig.getPromQL().contains("cpu"));
        
        // 验证内存指标
        TelemetryDefinition memoryDef = findTelemetryByKey(telemetryDefs, "memory_usage_percent");
        assertNotNull(memoryDef, "应该有内存使用率指标");
        assertEquals("内存使用率", memoryDef.getDisplayName());
        assertEquals(DataType.DOUBLE, memoryDef.getDataType());
        assertEquals("%", memoryDef.getUnit());
        assertTrue(memoryDef.isPrometheus());
        
        PrometheusConfig memoryConfig = memoryDef.getPrometheusConfig();
        assertNotNull(memoryConfig);
        assertTrue(memoryConfig.getPromQL().contains("memory") || 
                   memoryConfig.getPromQL().contains("mem"));
        
        // 验证磁盘指标
        TelemetryDefinition diskDef = findTelemetryByKey(telemetryDefs, "disk_usage_percent");
        assertNotNull(diskDef, "应该有磁盘使用率指标");
        assertEquals("磁盘使用率", diskDef.getDisplayName());
        assertEquals(DataType.DOUBLE, diskDef.getDataType());
        assertEquals("%", diskDef.getUnit());
        assertTrue(diskDef.isPrometheus());
        
        PrometheusConfig diskConfig = diskDef.getPrometheusConfig();
        assertNotNull(diskConfig);
        assertTrue(diskConfig.getPromQL().contains("disk") || 
                   diskConfig.getPromQL().contains("filesystem"));
    }
    
    @Test
    void testMultiplePrometheusDevices() {
        // Given - 创建多个设备
        Device server1 = createPrometheusDevice("Server-01", "prometheus-server-01");
        Device server2 = createPrometheusDevice("Server-02", "prometheus-server-02");
        Device server3 = createPrometheusDevice("Server-03", "prometheus-server-03");
        
        // When - 保存所有设备
        Device saved1 = deviceService.save(server1);
        Device saved2 = deviceService.save(server2);
        Device saved3 = deviceService.save(server3);
        
        // Then - 验证所有设备都创建成功
        assertNotNull(saved1.getId());
        assertNotNull(saved2.getId());
        assertNotNull(saved3.getId());
        
        // 验证它们都使用同一个 Profile
        assertEquals(prometheusProfileId, saved1.getDeviceProfileId());
        assertEquals(prometheusProfileId, saved2.getDeviceProfileId());
        assertEquals(prometheusProfileId, saved3.getDeviceProfileId());
        
        // 验证所有设备都能查询到
        List<Device> allDevices = deviceService.findAll();
        assertTrue(allDevices.size() >= 3, "应该至少有 3 个设备");
    }
    
    @Test
    void testPrometheusDeviceAccessToken() {
        // Given
        String accessToken = "unique-prometheus-token-12345";
        Device device = createPrometheusDevice("Token Test Device", accessToken);
        
        // When
        Device savedDevice = deviceService.save(device);
        
        // Then - 通过 AccessToken 查找设备
        Optional<Device> foundByToken = deviceService.findByAccessToken(accessToken);
        assertTrue(foundByToken.isPresent());
        assertEquals(savedDevice.getId(), foundByToken.get().getId());
        assertEquals(accessToken, foundByToken.get().getAccessToken());
    }
    
    @Test
    void testPrometheusProfilePersistence() {
        // Given - 重新加载 Profile
        Optional<DeviceProfile> reloadedProfile = deviceService.findProfileById(prometheusProfileId);
        assertTrue(reloadedProfile.isPresent());
        
        DeviceProfile profile = reloadedProfile.get();
        
        // Then - 验证所有 Prometheus 配置都正确持久化
        List<TelemetryDefinition> telemetryDefs = profile.getTelemetryDefinitions();
        
        for (TelemetryDefinition def : telemetryDefs) {
            // 验证每个遥测定义都有 Prometheus 配置
            assertNotNull(def.getProtocolConfig(), 
                "遥测定义 " + def.getKey() + " 应该有 ProtocolConfig");
            assertTrue(def.isPrometheus(), 
                "遥测定义 " + def.getKey() + " 应该是 Prometheus 类型");
            
            PrometheusConfig config = def.getPrometheusConfig();
            assertNotNull(config, 
                "遥测定义 " + def.getKey() + " 应该有 PrometheusConfig");
            assertNotNull(config.getPromQL(), 
                "遥测定义 " + def.getKey() + " 应该有 PromQL 查询");
            assertFalse(config.getPromQL().isEmpty(), 
                "PromQL 查询不应为空");
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * 创建 Prometheus 监控 DeviceProfile
     * 包含 CPU、内存、磁盘三个指标
     */
    private DeviceProfile createPrometheusMonitorProfile() {
        List<TelemetryDefinition> telemetryDefs = new ArrayList<>();
        
        // CPU 使用率指标
        telemetryDefs.add(TelemetryDefinition.builder()
                .key("cpu_usage_percent")
                .displayName("CPU使用率")
                .dataType(DataType.DOUBLE)
                .description("服务器 CPU 使用率百分比")
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)")
                        .needsRateCalculation(false)
                        .build())
                .build());
        
        // 内存使用率指标
        telemetryDefs.add(TelemetryDefinition.builder()
                .key("memory_usage_percent")
                .displayName("内存使用率")
                .dataType(DataType.DOUBLE)
                .description("服务器内存使用率百分比")
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100")
                        .needsRateCalculation(false)
                        .build())
                .build());
        
        // 磁盘使用率指标
        telemetryDefs.add(TelemetryDefinition.builder()
                .key("disk_usage_percent")
                .displayName("磁盘使用率")
                .dataType(DataType.DOUBLE)
                .description("根分区磁盘使用率百分比")
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})) * 100")
                        .needsRateCalculation(false)
                        .build())
                .build());
        
        return DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("Prometheus Server Monitor")
                .description("用于监控服务器的 Prometheus 配置文件，包含 CPU、内存、磁盘指标")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .strictMode(true)
                .telemetryDefinitions(telemetryDefs)
                .createdTime(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建 Prometheus 监控设备
     */
    private Device createPrometheusDevice(String name, String accessToken) {
        return Device.builder()
                .id(DeviceId.random())
                .name("Prometheus Monitor " + name)
                .type("SERVER_MONITOR")
                .deviceProfileId(prometheusProfileId)
                .accessToken(accessToken)
                .createdTime(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 根据 key 查找遥测定义
     */
    private TelemetryDefinition findTelemetryByKey(List<TelemetryDefinition> telemetryDefs, String key) {
        return telemetryDefs.stream()
                .filter(def -> key.equals(def.getKey()))
                .findFirst()
                .orElse(null);
    }
}

