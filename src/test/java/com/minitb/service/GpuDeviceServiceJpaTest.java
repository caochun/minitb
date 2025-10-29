package com.minitb.service;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.PrometheusDeviceConfiguration;
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
 * GPU 设备服务测试 (JPA 存储)
 * 
 * 测试场景：
 * 1. DeviceProfile CRUD 操作
 * 2. Device CRUD 操作
 * 3. 访问令牌查询
 * 4. Prometheus 配置持久化
 * 5. GPU 监控场景的完整流程
 * 
 * 存储实现：JPA + H2 内存数据库
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // 每个测试后回滚，保持数据库干净
class GpuDeviceServiceJpaTest {
    
    @Autowired
    private DeviceService deviceService;
    
    private DeviceProfileId testProfileId;
    private DeviceId testDeviceId;
    
    @BeforeEach
    void setUp() {
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║   GPU 设备服务测试 (JPA 存储)                          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
    }
    
    @Test
    void test01_CreateDeviceProfile() {
        System.out.println("━━━ 测试 1: 创建 DeviceProfile (JPA) ━━━\n");
        
        // Given: 创建 GPU 监控配置
        DeviceProfile profile = createGpuMonitorProfile();
        
        // When: 保存配置
        DeviceProfile saved = deviceService.saveProfile(profile);
        
        // Then: 验证保存成功
        assertNotNull(saved.getId(), "配置 ID 不应为空");
        assertTrue(saved.getCreatedTime() > 0, "创建时间应被自动设置");
        assertEquals("NVIDIA GPU Monitor (DCGM)", saved.getName());
        assertEquals(DeviceProfile.DataSourceType.PROMETHEUS, saved.getDataSourceType());
        // prometheusEndpoint 已移到Device.configuration中
        assertEquals("gpu", saved.getPrometheusDeviceLabelKey());
        assertEquals(7, saved.getTelemetryDefinitions().size(), "应包含 7 个遥测指标");
        
        System.out.println("✅ DeviceProfile 创建成功:");
        System.out.println("  - ID: " + saved.getId());
        System.out.println("  - 名称: " + saved.getName());
        System.out.println("  - 数据源: " + saved.getDataSourceType());
        System.out.println("  - 遥测指标数量: " + saved.getTelemetryDefinitions().size());
        System.out.println();
        
        testProfileId = saved.getId();
    }
    
    @Test
    void test02_FindDeviceProfileById() {
        System.out.println("━━━ 测试 2: 根据 ID 查询 DeviceProfile ━━━\n");
        
        // Given: 先创建一个配置
        DeviceProfile created = deviceService.saveProfile(createGpuMonitorProfile());
        DeviceProfileId profileId = created.getId();
        
        // When: 根据 ID 查询
        Optional<DeviceProfile> found = deviceService.findProfileById(profileId);
        
        // Then: 验证查询结果
        assertTrue(found.isPresent(), "应能找到配置");
        assertEquals(profileId, found.get().getId());
        assertEquals("NVIDIA GPU Monitor (DCGM)", found.get().getName());
        
        System.out.println("✅ DeviceProfile 查询成功:");
        System.out.println("  - ID: " + found.get().getId());
        System.out.println("  - 名称: " + found.get().getName());
        System.out.println();
    }
    
    @Test
    void test03_FindAllDeviceProfiles() {
        System.out.println("━━━ 测试 3: 查询所有 DeviceProfile ━━━\n");
        
        // Given: 创建多个配置
        deviceService.saveProfile(createGpuMonitorProfile());
        DeviceProfile profile2 = DeviceProfile.builder()
                .name("CPU Monitor")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                // prometheusEndpoint 已移到Device.configuration中
                .telemetryDefinitions(List.of())
                .strictMode(false)
                .build();
        deviceService.saveProfile(profile2);
        
        // When: 查询所有配置
        List<DeviceProfile> allProfiles = deviceService.findAllProfiles();
        
        // Then: 验证结果
        assertTrue(allProfiles.size() >= 2, "应至少有 2 个配置");
        
        System.out.println("✅ 查询到 " + allProfiles.size() + " 个 DeviceProfile:");
        allProfiles.forEach(p -> 
            System.out.println("  - " + p.getName() + " (ID: " + p.getId() + ")")
        );
        System.out.println();
    }
    
    @Test
    void test04_CreateDevice() {
        System.out.println("━━━ 测试 4: 创建 Device ━━━\n");
        
        // Given: 先创建 DeviceProfile
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        
        // When: 创建 Device
        Device device = Device.builder()
                .name("NVIDIA TITAN V - GPU 0")
                .type("NVIDIA_GPU")
                .deviceProfileId(profile.getId())
                .accessToken("test-gpu-0-token")
                .configuration(PrometheusDeviceConfiguration.builder()
                    .endpoint("http://localhost:9090")
                    .label("gpu=0")
                    .build())
                .build();
        
        Device saved = deviceService.save(device);
        PrometheusDeviceConfiguration savedConfig = (PrometheusDeviceConfiguration) saved.getConfiguration();
        
        // Then: 验证保存成功
        assertNotNull(saved.getId(), "设备 ID 不应为空");
        assertTrue(saved.getCreatedTime() > 0, "创建时间应被自动设置");
        assertEquals("NVIDIA TITAN V - GPU 0", saved.getName());
        assertEquals("NVIDIA_GPU", saved.getType());
        assertEquals("test-gpu-0-token", saved.getAccessToken());
        assertEquals("gpu=0", savedConfig.getLabel());
        assertEquals(profile.getId(), saved.getDeviceProfileId());
        
        System.out.println("✅ Device 创建成功:");
        System.out.println("  - ID: " + saved.getId());
        System.out.println("  - 名称: " + saved.getName());
        System.out.println("  - 类型: " + saved.getType());
        System.out.println("  - AccessToken: " + saved.getAccessToken());
        System.out.println("  - Prometheus 标签: " + savedConfig.getLabel());
        System.out.println("  - DeviceProfile ID: " + saved.getDeviceProfileId());
        System.out.println();
        
        testDeviceId = saved.getId();
    }
    
    @Test
    void test05_FindDeviceById() {
        System.out.println("━━━ 测试 5: 根据 ID 查询 Device ━━━\n");
        
        // Given: 先创建设备
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        Device created = deviceService.save(createGpuDevice(profile.getId(), 0));
        DeviceId deviceId = created.getId();
        
        // When: 根据 ID 查询
        Optional<Device> found = deviceService.findById(deviceId);
        
        // Then: 验证查询结果
        assertTrue(found.isPresent(), "应能找到设备");
        assertEquals(deviceId, found.get().getId());
        assertEquals("NVIDIA TITAN V - GPU 0", found.get().getName());
        
        System.out.println("✅ Device 查询成功:");
        System.out.println("  - ID: " + found.get().getId());
        System.out.println("  - 名称: " + found.get().getName());
        System.out.println();
    }
    
    @Test
    void test06_FindDeviceByAccessToken() {
        System.out.println("━━━ 测试 6: 根据 AccessToken 查询 Device ━━━\n");
        
        // Given: 创建设备
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        String accessToken = "test-access-token-unique-" + System.currentTimeMillis();
        Device device = Device.builder()
                .name("Test GPU")
                .type("NVIDIA_GPU")
                .deviceProfileId(profile.getId())
                .accessToken(accessToken)
                .build();
        deviceService.save(device);
        
        // When: 根据 AccessToken 查询
        Optional<Device> found = deviceService.findByAccessToken(accessToken);
        
        // Then: 验证查询结果
        assertTrue(found.isPresent(), "应能通过 AccessToken 找到设备");
        assertEquals(accessToken, found.get().getAccessToken());
        assertEquals("Test GPU", found.get().getName());
        
        System.out.println("✅ 通过 AccessToken 查询成功:");
        System.out.println("  - AccessToken: " + found.get().getAccessToken());
        System.out.println("  - 设备名称: " + found.get().getName());
        System.out.println();
    }
    
    @Test
    void test07_FindAllDevices() {
        System.out.println("━━━ 测试 7: 查询所有 Device ━━━\n");
        
        // Given: 创建多个设备
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        deviceService.save(createGpuDevice(profile.getId(), 0));
        deviceService.save(createGpuDevice(profile.getId(), 1));
        
        // When: 查询所有设备
        List<Device> allDevices = deviceService.findAll();
        
        // Then: 验证结果
        assertTrue(allDevices.size() >= 2, "应至少有 2 个设备");
        
        System.out.println("✅ 查询到 " + allDevices.size() + " 个 Device:");
        allDevices.forEach(d -> {
            if (d.getConfiguration() instanceof PrometheusDeviceConfiguration) {
                PrometheusDeviceConfiguration config = (PrometheusDeviceConfiguration) d.getConfiguration();
                System.out.println("  - " + d.getName() + 
                                 " (Token: " + d.getAccessToken() + 
                                 ", Label: " + config.getLabel() + ")");
            } else {
                System.out.println("  - " + d.getName() + 
                                 " (Token: " + d.getAccessToken() + ")");
            }
        });
        System.out.println();
    }
    
    @Test
    void test08_UpdateDevice() {
        System.out.println("━━━ 测试 8: 更新 Device ━━━\n");
        
        // Given: 创建设备
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        Device original = deviceService.save(createGpuDevice(profile.getId(), 0));
        DeviceId deviceId = original.getId();
        
        // When: 更新设备名称
        Device updated = Device.builder()
                .id(deviceId)
                .name("NVIDIA TITAN V - GPU 0 (Updated)")
                .type(original.getType())
                .deviceProfileId(original.getDeviceProfileId())
                .accessToken(original.getAccessToken())
                .configuration(PrometheusDeviceConfiguration.builder()
                    .endpoint("http://localhost:9090")
                    .label("gpu=0-updated")
                    .build())
                .createdTime(original.getCreatedTime())
                .build();
        
        Device saved = deviceService.save(updated);
        PrometheusDeviceConfiguration savedConfig = (PrometheusDeviceConfiguration) saved.getConfiguration();
        
        // Then: 验证更新成功
        assertEquals(deviceId, saved.getId(), "ID 应保持不变");
        assertEquals("NVIDIA TITAN V - GPU 0 (Updated)", saved.getName());
        assertEquals("gpu=0-updated", savedConfig.getLabel());
        
        PrometheusDeviceConfiguration originalConfig = (PrometheusDeviceConfiguration) original.getConfiguration();
        
        System.out.println("✅ Device 更新成功:");
        System.out.println("  - 原名称: " + original.getName());
        System.out.println("  - 新名称: " + saved.getName());
        System.out.println("  - 原标签: " + originalConfig.getLabel());
        System.out.println("  - 新标签: " + savedConfig.getLabel());
        System.out.println();
    }
    
    @Test
    void test09_DeleteDevice() {
        System.out.println("━━━ 测试 9: 删除 Device ━━━\n");
        
        // Given: 创建设备
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        Device device = deviceService.save(createGpuDevice(profile.getId(), 0));
        DeviceId deviceId = device.getId();
        
        // When: 删除设备
        deviceService.delete(deviceId);
        
        // Then: 验证删除成功
        Optional<Device> found = deviceService.findById(deviceId);
        assertFalse(found.isPresent(), "设备应已被删除");
        
        System.out.println("✅ Device 删除成功:");
        System.out.println("  - 已删除设备 ID: " + deviceId);
        System.out.println("  - 查询结果为空: " + found.isEmpty());
        System.out.println();
    }
    
    @Test
    void test10_DeleteDeviceProfile() {
        System.out.println("━━━ 测试 10: 删除 DeviceProfile ━━━\n");
        
        // Given: 创建配置
        DeviceProfile profile = deviceService.saveProfile(createGpuMonitorProfile());
        DeviceProfileId profileId = profile.getId();
        
        // When: 删除配置
        deviceService.deleteProfile(profileId);
        
        // Then: 验证删除成功
        Optional<DeviceProfile> found = deviceService.findProfileById(profileId);
        assertFalse(found.isPresent(), "配置应已被删除");
        
        System.out.println("✅ DeviceProfile 删除成功:");
        System.out.println("  - 已删除配置 ID: " + profileId);
        System.out.println("  - 查询结果为空: " + found.isEmpty());
        System.out.println();
    }
    
    @Test
    void test11_CompleteGpuMonitoringScenario() {
        System.out.println("━━━ 测试 11: 完整 GPU 监控场景 ━━━\n");
        
        // Step 1: 创建 DeviceProfile（配置双 GPU 监控）
        System.out.println("📋 步骤 1: 创建 GPU 监控配置...");
        DeviceProfile gpuProfile = createGpuMonitorProfile();
        DeviceProfile savedProfile = deviceService.saveProfile(gpuProfile);
        System.out.println("  ✓ DeviceProfile 创建: " + savedProfile.getName());
        System.out.println("    - 遥测指标: " + savedProfile.getTelemetryDefinitions().size() + " 个");
        System.out.println();
        
        // Step 2: 创建 GPU 0
        System.out.println("📋 步骤 2: 创建 GPU 0 设备...");
        Device gpu0 = createGpuDevice(savedProfile.getId(), 0);
        Device savedGpu0 = deviceService.save(gpu0);
        PrometheusDeviceConfiguration gpu0Config = (PrometheusDeviceConfiguration) savedGpu0.getConfiguration();
        System.out.println("  ✓ Device 创建: " + savedGpu0.getName());
        System.out.println("    - AccessToken: " + savedGpu0.getAccessToken());
        System.out.println("    - Prometheus Label: " + gpu0Config.getLabel());
        System.out.println();
        
        // Step 3: 创建 GPU 1
        System.out.println("📋 步骤 3: 创建 GPU 1 设备...");
        Device gpu1 = createGpuDevice(savedProfile.getId(), 1);
        Device savedGpu1 = deviceService.save(gpu1);
        PrometheusDeviceConfiguration gpu1Config = (PrometheusDeviceConfiguration) savedGpu1.getConfiguration();
        System.out.println("  ✓ Device 创建: " + savedGpu1.getName());
        System.out.println("    - AccessToken: " + savedGpu1.getAccessToken());
        System.out.println("    - Prometheus Label: " + gpu1Config.getLabel());
        System.out.println();
        
        // Step 4: 验证设备可通过 AccessToken 查询
        System.out.println("📋 步骤 4: 验证设备认证...");
        Optional<Device> foundGpu0 = deviceService.findByAccessToken(savedGpu0.getAccessToken());
        Optional<Device> foundGpu1 = deviceService.findByAccessToken(savedGpu1.getAccessToken());
        assertTrue(foundGpu0.isPresent(), "GPU 0 应可通过 AccessToken 找到");
        assertTrue(foundGpu1.isPresent(), "GPU 1 应可通过 AccessToken 找到");
        System.out.println("  ✓ GPU 0 认证成功");
        System.out.println("  ✓ GPU 1 认证成功");
        System.out.println();
        
        // Step 5: 验证 Prometheus 配置
        System.out.println("📋 步骤 5: 验证 Prometheus 配置...");
        Optional<DeviceProfile> profileCheck = deviceService.findProfileById(savedProfile.getId());
        assertTrue(profileCheck.isPresent());
        // prometheusEndpoint 已移到Device.configuration中
        assertEquals("gpu", profileCheck.get().getPrometheusDeviceLabelKey());
        System.out.println("  ✓ 设备标签键: " + profileCheck.get().getPrometheusDeviceLabelKey());
        System.out.println();
        
        // Step 6: 验证遥测指标配置
        System.out.println("📋 步骤 6: 验证遥测指标配置...");
        List<TelemetryDefinition> telemetryDefs = profileCheck.get().getTelemetryDefinitions();
        assertEquals(7, telemetryDefs.size(), "应有 7 个遥测指标");
        
        String[] expectedKeys = {
            "gpu_utilization", "memory_copy_utilization", "gpu_temperature",
            "memory_temperature", "power_usage", "memory_used", "memory_free"
        };
        
        for (String expectedKey : expectedKeys) {
            boolean found = telemetryDefs.stream()
                    .anyMatch(def -> def.getKey().equals(expectedKey));
            assertTrue(found, "应包含指标: " + expectedKey);
            System.out.println("  ✓ 指标配置: " + expectedKey);
        }
        System.out.println();
        
        // Summary
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   ✅ 完整 GPU 监控场景测试通过                          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("测试摘要:");
        System.out.println("  ✓ DeviceProfile 创建: 1 个");
        System.out.println("  ✓ Device 创建: 2 个 (GPU 0, GPU 1)");
        System.out.println("  ✓ AccessToken 认证: 通过");
        System.out.println("  ✓ Prometheus 配置: 完整");
        System.out.println("  ✓ 遥测指标配置: 7 个");
        System.out.println();
    }
    
    // ==================== Helper Methods ====================
    
    private DeviceProfile createGpuMonitorProfile() {
        return DeviceProfile.builder()
                .name("NVIDIA GPU Monitor (DCGM)")
                .description("NVIDIA TITAN V GPU 监控配置")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                // prometheusEndpoint 已移到Device.configuration中
                .prometheusDeviceLabelKey("gpu")
                .strictMode(true)
                .telemetryDefinitions(createGpuTelemetryDefinitions())
                .build();
    }
    
    private Device createGpuDevice(DeviceProfileId profileId, int gpuIndex) {
        return Device.builder()
                .name("NVIDIA TITAN V - GPU " + gpuIndex)
                .type("NVIDIA_GPU")
                .deviceProfileId(profileId)
                .accessToken("test-gpu-" + gpuIndex + "-token-" + System.currentTimeMillis())
                .configuration(PrometheusDeviceConfiguration.builder()
                    .endpoint("http://192.168.30.134:9090")
                    .label("gpu=" + gpuIndex)
                    .build())
                .build();
    }
    
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
        
        return defs;
    }
}

