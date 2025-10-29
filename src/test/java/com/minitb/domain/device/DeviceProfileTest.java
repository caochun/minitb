package com.minitb.domain.device;

import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.telemetry.DataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeviceProfile 领域模型单元测试
 */
class DeviceProfileTest {
    
    @Test
    void testDeviceProfileCreation() {
        // Given
        DeviceProfileId profileId = DeviceProfileId.random();
        String name = "Temperature Monitor Profile";
        String description = "Profile for temperature monitoring devices";
        boolean strictMode = true;
        DeviceProfile.DataSourceType dataSourceType = DeviceProfile.DataSourceType.PROMETHEUS;
        
        List<TelemetryDefinition> telemetryDefs = new ArrayList<>();
        telemetryDefs.add(TelemetryDefinition.simple("temperature", DataType.DOUBLE));
        telemetryDefs.add(TelemetryDefinition.simple("humidity", DataType.DOUBLE));
        
        // When
        DeviceProfile profile = new DeviceProfile();
        profile.setId(profileId);
        profile.setName(name);
        profile.setDescription(description);
        profile.setStrictMode(strictMode);
        profile.setDataSourceType(dataSourceType);
        profile.setTelemetryDefinitions(telemetryDefs);
        profile.setCreatedTime(System.currentTimeMillis());
        
        // Then
        assertNotNull(profile);
        assertEquals(profileId, profile.getId());
        assertEquals(name, profile.getName());
        assertEquals(description, profile.getDescription());
        assertTrue(profile.isStrictMode());
        assertEquals(dataSourceType, profile.getDataSourceType());
        assertEquals(2, profile.getTelemetryDefinitions().size());
    }
    
    @Test
    void testTelemetryDefinitionCreation() {
        // Given
        String key = "temperature";
        DataType dataType = DataType.DOUBLE;
        
        // When
        TelemetryDefinition telemetryDef = TelemetryDefinition.simple(key, dataType);
        
        // Then
        assertNotNull(telemetryDef);
        assertEquals(key, telemetryDef.getKey());
        assertEquals(dataType, telemetryDef.getDataType());
    }
    
    @Test
    void testTelemetryDefinitionEquality() {
        // Given
        TelemetryDefinition def1 = TelemetryDefinition.simple("temperature", DataType.DOUBLE);
        TelemetryDefinition def2 = TelemetryDefinition.simple("temperature", DataType.DOUBLE);
        
        // Then
        assertEquals(def1.getKey(), def2.getKey(), "相同的遥测定义应该有相同的 key");
        assertEquals(def1.getDataType(), def2.getDataType(), "相同的遥测定义应该有相同的 dataType");
    }
    
    @Test
    void testDeviceProfileWithEmptyTelemetryDefinitions() {
        // Given
        DeviceProfile profile = new DeviceProfile();
        profile.setId(DeviceProfileId.random());
        profile.setName("Simple Profile");
        profile.setTelemetryDefinitions(new ArrayList<>());
        
        // Then
        assertNotNull(profile.getTelemetryDefinitions());
        assertTrue(profile.getTelemetryDefinitions().isEmpty());
    }
    
    @Test
    void testStrictModeValidation() {
        // Given
        DeviceProfile strictProfile = new DeviceProfile();
        strictProfile.setStrictMode(true);
        
        DeviceProfile lenientProfile = new DeviceProfile();
        lenientProfile.setStrictMode(false);
        
        // Then
        assertTrue(strictProfile.isStrictMode(), "严格模式应该启用");
        assertFalse(lenientProfile.isStrictMode(), "宽松模式应该禁用");
    }
    
    @Test
    void testDataSourceTypeValues() {
        // Given
        DeviceProfile prometheusProfile = new DeviceProfile();
        prometheusProfile.setDataSourceType(DeviceProfile.DataSourceType.PROMETHEUS);
        
        DeviceProfile mqttProfile = new DeviceProfile();
        mqttProfile.setDataSourceType(DeviceProfile.DataSourceType.MQTT);
        
        // Then
        assertEquals(DeviceProfile.DataSourceType.PROMETHEUS, prometheusProfile.getDataSourceType());
        assertEquals(DeviceProfile.DataSourceType.MQTT, mqttProfile.getDataSourceType());
    }
}

