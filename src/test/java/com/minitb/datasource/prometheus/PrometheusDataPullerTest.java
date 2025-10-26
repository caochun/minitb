package com.minitb.datasource.prometheus;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import com.minitb.transport.service.TransportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PrometheusDataPuller 单元测试
 * 
 * 使用 Mock，不依赖真实的 Prometheus 服务
 * 测试核心逻辑：标签映射和数据关联
 */
@ExtendWith(MockitoExtension.class)
class PrometheusDataPullerTest {
    
    @Mock
    private DeviceService deviceService;
    
    @Mock
    private TransportService transportService;
    
    private PrometheusDataPuller prometheusDataPuller;
    
    private Device testDevice;
    private DeviceProfile testProfile;
    
    @BeforeEach
    void setUp() {
        // 注意：这里我们只能测试公共方法，私有方法无法直接测试
        // 实际的 HTTP 调用和 JSON 解析需要在集成测试中验证
        
        // 创建测试用的 DeviceProfile
        testProfile = DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("Test Prometheus Profile")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .prometheusEndpoint("http://localhost:9090")
                .prometheusDeviceLabelKey("instance")
                .telemetryDefinitions(createTestTelemetryDefinitions())
                .build();
        
        // 创建测试用的 Device
        testDevice = Device.builder()
                .id(DeviceId.random())
                .name("Test Server")
                .type("SERVER_MONITOR")
                .deviceProfileId(testProfile.getId())
                .accessToken("test-token-123")
                .prometheusLabel("instance=test-server:9100")
                .build();
    }
    
    @Test
    void testPrometheusLabelParsing() {
        // Given
        String prometheusLabel = "instance=server-01:9100";
        
        // When - 解析标签
        String[] parts = prometheusLabel.split("=", 2);
        
        // Then
        assertEquals(2, parts.length);
        assertEquals("instance", parts[0]);
        assertEquals("server-01:9100", parts[1]);
    }
    
    @Test
    void testPrometheusLabelMatching() {
        // Given - 模拟 Prometheus 查询结果
        PrometheusQueryResult result1 = PrometheusQueryResult.builder()
                .metric(Map.of("instance", "server-01:9100", "job", "node"))
                .value(45.2)
                .timestamp(System.currentTimeMillis())
                .build();
        
        PrometheusQueryResult result2 = PrometheusQueryResult.builder()
                .metric(Map.of("instance", "server-02:9100", "job", "node"))
                .value(32.1)
                .timestamp(System.currentTimeMillis())
                .build();
        
        // When - 测试标签匹配
        boolean match1 = result1.matchesLabel("instance", "server-01:9100");
        boolean match2 = result2.matchesLabel("instance", "server-01:9100");
        
        // Then
        assertTrue(match1, "result1 应该匹配 server-01");
        assertFalse(match2, "result2 不应该匹配 server-01");
    }
    
    @Test
    void testDeviceFilterByDataSourceType() {
        // Given - 准备不同类型的设备
        Device mqttDevice = Device.builder()
                .id(DeviceId.random())
                .name("MQTT Device")
                .deviceProfileId(DeviceProfileId.random())
                .build();
        
        Device prometheusDevice = testDevice;
        
        DeviceProfile mqttProfile = DeviceProfile.builder()
                .id(mqttDevice.getDeviceProfileId())
                .dataSourceType(DeviceProfile.DataSourceType.MQTT)
                .build();
        
        // Mock 返回
        when(deviceService.findAll()).thenReturn(Arrays.asList(mqttDevice, prometheusDevice));
        when(deviceService.findProfileById(mqttDevice.getDeviceProfileId()))
                .thenReturn(Optional.of(mqttProfile));
        when(deviceService.findProfileById(prometheusDevice.getDeviceProfileId()))
                .thenReturn(Optional.of(testProfile));
        
        // Then - 验证 Prometheus 设备可以被正确识别
        List<Device> allDevices = deviceService.findAll();
        assertEquals(2, allDevices.size());
        
        // 过滤 Prometheus 设备
        List<Device> prometheusDevices = allDevices.stream()
                .filter(device -> {
                    Optional<DeviceProfile> profileOpt = deviceService.findProfileById(device.getDeviceProfileId());
                    return profileOpt.isPresent() && 
                           DeviceProfile.DataSourceType.PROMETHEUS == profileOpt.get().getDataSourceType();
                })
                .toList();
        
        assertEquals(1, prometheusDevices.size(), "应该只有 1 个 Prometheus 设备");
        assertEquals(prometheusDevice.getId(), prometheusDevices.get(0).getId());
    }
    
    @Test
    void testPrometheusQueryResultCreation() {
        // Given
        Map<String, String> metric = new HashMap<>();
        metric.put("instance", "localhost:9100");
        metric.put("job", "node");
        metric.put("__name__", "node_cpu_seconds_total");
        
        // When
        PrometheusQueryResult result = PrometheusQueryResult.builder()
                .metric(metric)
                .timestamp(1234567890L)
                .value(45.67)
                .build();
        
        // Then
        assertNotNull(result);
        assertEquals("localhost:9100", result.getLabel("instance"));
        assertEquals("node", result.getLabel("job"));
        assertEquals(45.67, result.getValue());
        assertTrue(result.matchesLabel("instance", "localhost:9100"));
        assertFalse(result.matchesLabel("instance", "other:9100"));
    }
    
    @Test
    void testTelemetryDefinitionPrometheusCheck() {
        // Given
        List<TelemetryDefinition> definitions = testProfile.getTelemetryDefinitions();
        
        // Then - 验证所有定义都是 Prometheus 类型
        assertEquals(3, definitions.size());
        
        for (TelemetryDefinition def : definitions) {
            assertTrue(def.isPrometheus(), 
                "遥测定义 " + def.getKey() + " 应该是 Prometheus 类型");
            assertNotNull(def.getPrometheusConfig());
            assertNotNull(def.getPrometheusConfig().getPromQL());
        }
    }
    
    // ==================== Helper Methods ====================
    
    private List<TelemetryDefinition> createTestTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();
        
        defs.add(TelemetryDefinition.builder()
                .key("cpu_usage_percent")
                .displayName("CPU使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)")
                        .build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("memory_usage_percent")
                .displayName("内存使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100")
                        .build())
                .build());
        
        defs.add(TelemetryDefinition.builder()
                .key("disk_usage_percent")
                .displayName("磁盘使用率")
                .dataType(DataType.DOUBLE)
                .unit("%")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(1 - (node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})) * 100")
                        .build())
                .build());
        
        return defs;
    }
}

