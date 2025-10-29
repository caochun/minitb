package com.minitb.integration;

import com.minitb.application.service.DeviceService;
import com.minitb.application.service.alarm.AlarmEvaluator;
import com.minitb.application.service.alarm.AlarmService;
import com.minitb.domain.alarm.*;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceConfiguration;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.PrometheusDeviceConfiguration;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.telemetry.BasicTsKvEntry;
import com.minitb.domain.telemetry.DataType;
import com.minitb.domain.telemetry.LongDataEntry;
import com.minitb.domain.telemetry.TsKvEntry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 告警系统端到端测试
 * 
 * 测试场景：
 * 1. 简单阈值告警（SIMPLE）
 * 2. 持续时间告警（DURATION）
 * 3. 重复次数告警（REPEATING）
 * 4. 告警严重程度变化
 * 5. 告警清除
 * 6. 告警确认
 */
@SpringBootTest
@Slf4j
public class AlarmEndToEndTest {
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private AlarmService alarmService;
    
    @Autowired
    private AlarmEvaluator alarmEvaluator;
    
    private Device testDevice;
    private DeviceProfile testProfile;
    
    @BeforeEach
    void setup() {
        log.info("=".repeat(60));
        log.info("  告警系统端到端测试");
        log.info("=".repeat(60));
    }
    
    @Test
    void testSimpleThresholdAlarm() throws Exception {
        log.info("\n[测试 1] 简单阈值告警");
        
        // 1. 创建测试设备和配置
        createTestDeviceWithSimpleAlarmRule();
        
        // 2. 模拟正常数据（温度 70°C）
        Map<String, TsKvEntry> normalData = createTelemetryData("temperature", 70L);
        alarmEvaluator.evaluate(testDevice, testProfile, normalData);
        
        // 验证：没有告警
        List<Alarm> alarms = alarmService.findByDevice(testDevice.getId());
        assertEquals(0, alarms.size(), "正常数据不应触发告警");
        
        // 3. 模拟高温数据（温度 90°C）
        Map<String, TsKvEntry> highTempData = createTelemetryData("temperature", 90L);
        alarmEvaluator.evaluate(testDevice, testProfile, highTempData);
        
        // 验证：创建了告警
        alarms = alarmService.findByDevice(testDevice.getId());
        assertEquals(1, alarms.size(), "高温应触发告警");
        
        Alarm alarm = alarms.get(0);
        assertEquals("High Temperature", alarm.getType());
        assertEquals(AlarmSeverity.CRITICAL, alarm.getSeverity());
        assertEquals(AlarmStatus.ACTIVE_UNACK, alarm.getStatus());
        assertFalse(alarm.isAcknowledged());
        assertFalse(alarm.isCleared());
        
        log.info("✅ 简单阈值告警测试通过");
        log.info("   告警: {} [{}]", alarm.getType(), alarm.getSeverity());
    }
    
    @Test
    void testAlarmSeverityEscalation() throws Exception {
        log.info("\n[测试 2] 告警严重程度升级");
        
        // 1. 创建测试设备
        createTestDeviceWithMultiLevelAlarmRule();
        
        // 2. 触发 WARNING 级别（温度 76°C）
        Map<String, TsKvEntry> warningData = createTelemetryData("temperature", 76L);
        alarmEvaluator.evaluate(testDevice, testProfile, warningData);
        
        List<Alarm> alarms = alarmService.findByDevice(testDevice.getId());
        assertEquals(1, alarms.size());
        assertEquals(AlarmSeverity.WARNING, alarms.get(0).getSeverity());
        log.info("   ⚠️ WARNING 告警已触发: 76°C");
        
        // 3. 升级到 MAJOR 级别（温度 82°C）
        Map<String, TsKvEntry> majorData = createTelemetryData("temperature", 82L);
        alarmEvaluator.evaluate(testDevice, testProfile, majorData);
        
        alarms = alarmService.findByDevice(testDevice.getId());
        assertEquals(1, alarms.size());
        assertEquals(AlarmSeverity.MAJOR, alarms.get(0).getSeverity());
        log.info("   📈 升级到 MAJOR: 82°C");
        
        // 4. 升级到 CRITICAL 级别（温度 88°C）
        Map<String, TsKvEntry> criticalData = createTelemetryData("temperature", 88L);
        alarmEvaluator.evaluate(testDevice, testProfile, criticalData);
        
        alarms = alarmService.findByDevice(testDevice.getId());
        assertEquals(1, alarms.size());
        assertEquals(AlarmSeverity.CRITICAL, alarms.get(0).getSeverity());
        log.info("   🚨 升级到 CRITICAL: 88°C");
        
        log.info("✅ 告警严重程度升级测试通过");
    }
    
    @Test
    void testAlarmClearance() throws Exception {
        log.info("\n[测试 3] 告警清除");
        
        // 1. 创建测试设备并触发告警
        createTestDeviceWithSimpleAlarmRule();
        
        Map<String, TsKvEntry> highTempData = createTelemetryData("temperature", 90L);
        alarmEvaluator.evaluate(testDevice, testProfile, highTempData);
        
        List<Alarm> alarms = alarmService.findByDevice(testDevice.getId());
        assertEquals(1, alarms.size());
        Alarm alarm = alarms.get(0);
        assertFalse(alarm.isCleared());
        log.info("   🚨 告警已创建: {} [{}]", alarm.getType(), alarm.getSeverity());
        
        // 2. 温度恢复正常（70°C）
        Map<String, TsKvEntry> normalData = createTelemetryData("temperature", 70L);
        alarmEvaluator.evaluate(testDevice, testProfile, normalData);
        
        // 3. 验证告警已清除
        Optional<Alarm> clearedAlarmOpt = alarmService.findById(alarm.getId());
        assertTrue(clearedAlarmOpt.isPresent());
        
        Alarm clearedAlarm = clearedAlarmOpt.get();
        assertTrue(clearedAlarm.isCleared());
        assertEquals(AlarmStatus.CLEARED_UNACK, clearedAlarm.getStatus());
        log.info("   ✅ 告警已清除: {} → {}", alarm.getStatus(), clearedAlarm.getStatus());
        
        log.info("✅ 告警清除测试通过");
    }
    
    @Test
    void testAlarmAcknowledgement() throws Exception {
        log.info("\n[测试 4] 告警确认");
        
        // 1. 创建告警
        createTestDeviceWithSimpleAlarmRule();
        Map<String, TsKvEntry> highTempData = createTelemetryData("temperature", 90L);
        alarmEvaluator.evaluate(testDevice, testProfile, highTempData);
        
        List<Alarm> alarms = alarmService.findByDevice(testDevice.getId());
        Alarm alarm = alarms.get(0);
        assertEquals(AlarmStatus.ACTIVE_UNACK, alarm.getStatus());
        log.info("   告警状态: {}", alarm.getStatus());
        
        // 2. 确认告警
        Alarm acknowledgedAlarm = alarmService.acknowledgeAlarm(alarm.getId());
        
        // 3. 验证
        assertTrue(acknowledgedAlarm.isAcknowledged());
        assertEquals(AlarmStatus.ACTIVE_ACK, acknowledgedAlarm.getStatus());
        assertNotNull(acknowledgedAlarm.getAckTs());
        log.info("   ✅ 告警已确认: {} → {}", alarm.getStatus(), acknowledgedAlarm.getStatus());
        
        // 4. 清除已确认的告警
        Map<String, TsKvEntry> normalData = createTelemetryData("temperature", 70L);
        alarmEvaluator.evaluate(testDevice, testProfile, normalData);
        
        Optional<Alarm> finalAlarmOpt = alarmService.findById(alarm.getId());
        assertTrue(finalAlarmOpt.isPresent());
        
        Alarm finalAlarm = finalAlarmOpt.get();
        assertTrue(finalAlarm.isCleared());
        assertTrue(finalAlarm.isAcknowledged());
        assertEquals(AlarmStatus.CLEARED_ACK, finalAlarm.getStatus());
        log.info("   ✅ 已确认的告警被清除: {}", finalAlarm.getStatus());
        
        log.info("✅ 告警确认测试通过");
    }
    
    @Test
    void testAlarmLifecycle() throws Exception {
        log.info("\n[测试 5] 完整告警生命周期");
        log.info("   ACTIVE_UNACK → ACTIVE_ACK → CLEARED_ACK");
        
        // 1. 创建告警
        createTestDeviceWithSimpleAlarmRule();
        Map<String, TsKvEntry> highTempData = createTelemetryData("temperature", 90L);
        alarmEvaluator.evaluate(testDevice, testProfile, highTempData);
        
        List<Alarm> alarms = alarmService.findByDevice(testDevice.getId());
        Alarm alarm = alarms.get(0);
        
        // 阶段1: ACTIVE_UNACK
        assertEquals(AlarmStatus.ACTIVE_UNACK, alarm.getStatus());
        log.info("   ✓ 阶段1: {}", alarm.getStatus());
        
        // 阶段2: ACTIVE_ACK
        alarm = alarmService.acknowledgeAlarm(alarm.getId());
        assertEquals(AlarmStatus.ACTIVE_ACK, alarm.getStatus());
        log.info("   ✓ 阶段2: {}", alarm.getStatus());
        
        // 添加短暂延迟确保持续时间 > 0
        Thread.sleep(100);
        
        // 阶段3: CLEARED_ACK
        Map<String, TsKvEntry> normalData = createTelemetryData("temperature", 70L);
        alarmEvaluator.evaluate(testDevice, testProfile, normalData);
        
        alarm = alarmService.findById(alarm.getId()).get();
        assertEquals(AlarmStatus.CLEARED_ACK, alarm.getStatus());
        log.info("   ✓ 阶段3: {}", alarm.getStatus());
        
        // 验证时间戳
        assertNotNull(alarm.getStartTs());
        assertNotNull(alarm.getEndTs());
        assertNotNull(alarm.getAckTs());
        assertNotNull(alarm.getClearTs());
        assertTrue(alarm.getDuration() > 0); // 检查毫秒而不是秒
        log.info("   持续时间: {} 毫秒 ({} 秒)", alarm.getDuration(), alarm.getDurationSeconds());
        
        log.info("✅ 完整生命周期测试通过");
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 创建带简单告警规则的测试设备
     */
    private void createTestDeviceWithSimpleAlarmRule() {
        // 创建告警规则：温度 > 85°C
        AlarmRule rule = AlarmRule.builder()
            .id("test_high_temp_alarm")
            .alarmType("High Temperature")
            .createConditions(Map.of(
                AlarmSeverity.CRITICAL, AlarmCondition.simple(
                    AlarmConditionFilter.greaterThan("temperature", 85.0)
                )
            ))
            .clearCondition(AlarmCondition.simple(
                AlarmConditionFilter.lessThan("temperature", 75.0)
            ))
            .build();
        
        // 创建设备配置
        testProfile = DeviceProfile.builder()
            .id(DeviceProfileId.random())
            .name("Test Device Profile")
            .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
            .alarmRules(Arrays.asList(rule))
            .createdTime(System.currentTimeMillis())
            .build();
        
        testProfile = deviceService.saveProfile(testProfile);
        
        // 创建设备
        testDevice = Device.builder()
            .id(DeviceId.random())
            .name("Test Device")
            .type("TEST")
            .deviceProfileId(testProfile.getId())
            .accessToken("test-token")
            .configuration(PrometheusDeviceConfiguration.builder()
                .endpoint("http://localhost:9090")
                .label("instance=test")
                .build())
            .createdTime(System.currentTimeMillis())
            .build();
        
        testDevice = deviceService.save(testDevice);
    }
    
    /**
     * 创建带多级告警规则的测试设备
     */
    private void createTestDeviceWithMultiLevelAlarmRule() {
        // 多级告警规则
        AlarmRule rule = AlarmRule.builder()
            .id("test_multi_level_alarm")
            .alarmType("High Temperature")
            .createConditions(new TreeMap<>(Map.of(
                AlarmSeverity.CRITICAL, AlarmCondition.simple(
                    AlarmConditionFilter.greaterThan("temperature", 85.0)
                ),
                AlarmSeverity.MAJOR, AlarmCondition.simple(
                    AlarmConditionFilter.greaterThan("temperature", 80.0)
                ),
                AlarmSeverity.WARNING, AlarmCondition.simple(
                    AlarmConditionFilter.greaterThan("temperature", 75.0)
                )
            )))
            .clearCondition(AlarmCondition.simple(
                AlarmConditionFilter.lessThan("temperature", 70.0)
            ))
            .build();
        
        // 创建设备配置
        testProfile = DeviceProfile.builder()
            .id(DeviceProfileId.random())
            .name("Test Multi-Level Profile")
            .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
            .alarmRules(Arrays.asList(rule))
            .createdTime(System.currentTimeMillis())
            .build();
        
        testProfile = deviceService.saveProfile(testProfile);
        
        // 创建设备
        testDevice = Device.builder()
            .id(DeviceId.random())
            .name("Test Multi-Level Device")
            .type("TEST")
            .deviceProfileId(testProfile.getId())
            .accessToken("test-ml-token")
            .configuration(PrometheusDeviceConfiguration.builder()
                .endpoint("http://localhost:9090")
                .label("instance=test-ml")
                .build())
            .createdTime(System.currentTimeMillis())
            .build();
        
        testDevice = deviceService.save(testDevice);
    }
    
    /**
     * 创建遥测数据
     */
    private Map<String, TsKvEntry> createTelemetryData(String key, Long value) {
        Map<String, TsKvEntry> data = new HashMap<>();
        TsKvEntry entry = new BasicTsKvEntry(System.currentTimeMillis(), new LongDataEntry(key, value));
        data.put(key, entry);
        return data;
    }
}

