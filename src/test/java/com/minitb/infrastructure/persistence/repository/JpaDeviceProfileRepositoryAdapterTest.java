package com.minitb.infrastructure.persistence.repository;

import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.DeviceProfileRepository;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.telemetry.DataType;
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
 * JPA DeviceProfile Repository Adapter 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaDeviceProfileRepositoryAdapterTest {
    
    @Autowired
    private DeviceProfileRepository deviceProfileRepository; // ✅ 注入的是 Domain 接口
    
    @Test
    void testSaveDeviceProfile() {
        // Given
        DeviceProfile profile = createTestProfile("Test Profile", "Test Description");
        
        // When
        DeviceProfile saved = deviceProfileRepository.save(profile);
        
        // Then
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Test Profile", saved.getName());
        assertEquals("Test Description", saved.getDescription());
    }
    
    @Test
    void testSaveProfileWithTelemetryDefinitions() {
        // Given
        DeviceProfile profile = createTestProfile("Sensor Profile", "For sensors");
        List<TelemetryDefinition> telemetryDefs = new ArrayList<>();
        telemetryDefs.add(TelemetryDefinition.simple("temperature", DataType.DOUBLE));
        telemetryDefs.add(TelemetryDefinition.simple("humidity", DataType.DOUBLE));
        telemetryDefs.add(TelemetryDefinition.simple("pressure", DataType.LONG));
        profile.setTelemetryDefinitions(telemetryDefs);
        
        // When
        DeviceProfile saved = deviceProfileRepository.save(profile);
        
        // Then
        assertNotNull(saved.getTelemetryDefinitions());
        assertEquals(3, saved.getTelemetryDefinitions().size());
        
        // 验证遥测定义是否正确保存
        TelemetryDefinition tempDef = saved.getTelemetryDefinitions().stream()
                .filter(def -> "temperature".equals(def.getKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(tempDef);
        assertEquals(DataType.DOUBLE, tempDef.getDataType());
    }
    
    @Test
    void testFindById() {
        // Given
        DeviceProfile profile = createTestProfile("Profile A", "Description A");
        DeviceProfile saved = deviceProfileRepository.save(profile);
        
        // When
        Optional<DeviceProfile> found = deviceProfileRepository.findById(saved.getId());
        
        // Then
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("Profile A", found.get().getName());
    }
    
    @Test
    void testFindByIdNotFound() {
        // Given
        DeviceProfileId nonExistentId = DeviceProfileId.random();
        
        // When
        Optional<DeviceProfile> found = deviceProfileRepository.findById(nonExistentId);
        
        // Then
        assertFalse(found.isPresent());
    }
    
    @Test
    void testFindAll() {
        // Given
        deviceProfileRepository.save(createTestProfile("Profile 1", "Desc 1"));
        deviceProfileRepository.save(createTestProfile("Profile 2", "Desc 2"));
        deviceProfileRepository.save(createTestProfile("Profile 3", "Desc 3"));
        
        // When
        List<DeviceProfile> profiles = deviceProfileRepository.findAll();
        
        // Then
        assertNotNull(profiles);
        assertTrue(profiles.size() >= 3);
    }
    
    @Test
    void testUpdateProfile() {
        // Given
        DeviceProfile profile = createTestProfile("Original", "Original Description");
        DeviceProfile saved = deviceProfileRepository.save(profile);
        
        // When
        saved.setName("Updated");
        saved.setDescription("Updated Description");
        DeviceProfile updated = deviceProfileRepository.save(saved);
        
        // Then
        assertEquals(saved.getId(), updated.getId());
        assertEquals("Updated", updated.getName());
        assertEquals("Updated Description", updated.getDescription());
    }
    
    @Test
    void testDeleteProfile() {
        // Given
        DeviceProfile profile = createTestProfile("To Delete", "Will be deleted");
        DeviceProfile saved = deviceProfileRepository.save(profile);
        DeviceProfileId profileId = saved.getId();
        
        // When
        deviceProfileRepository.deleteById(profileId);
        
        // Then
        Optional<DeviceProfile> found = deviceProfileRepository.findById(profileId);
        assertFalse(found.isPresent());
    }
    
    @Test
    void testStrictModeFlag() {
        // Given
        DeviceProfile strictProfile = createTestProfile("Strict Profile", "Strict mode enabled");
        strictProfile.setStrictMode(true);
        
        // When
        DeviceProfile saved = deviceProfileRepository.save(strictProfile);
        Optional<DeviceProfile> reloaded = deviceProfileRepository.findById(saved.getId());
        
        // Then
        assertTrue(reloaded.isPresent());
        assertTrue(reloaded.get().isStrictMode());
    }
    
    @Test
    void testDataSourceType() {
        // Given
        DeviceProfile profile = createTestProfile("Prometheus Profile", "For Prometheus");
        profile.setDataSourceType(DeviceProfile.DataSourceType.PROMETHEUS);
        
        // When
        DeviceProfile saved = deviceProfileRepository.save(profile);
        Optional<DeviceProfile> reloaded = deviceProfileRepository.findById(saved.getId());
        
        // Then
        assertTrue(reloaded.isPresent());
        assertEquals(DeviceProfile.DataSourceType.PROMETHEUS, reloaded.get().getDataSourceType());
    }
    
    @Test
    void testTelemetryDefinitionPersistence() {
        // Given
        DeviceProfile profile = createTestProfile("Complex Profile", "With telemetry");
        List<TelemetryDefinition> telemetryDefs = new ArrayList<>();
        telemetryDefs.add(TelemetryDefinition.simple("cpu_usage", DataType.DOUBLE));
        telemetryDefs.add(TelemetryDefinition.simple("memory_usage", DataType.LONG));
        telemetryDefs.add(TelemetryDefinition.simple("status", DataType.STRING));
        telemetryDefs.add(TelemetryDefinition.simple("is_active", DataType.BOOLEAN));
        profile.setTelemetryDefinitions(telemetryDefs);
        
        // When
        DeviceProfile saved = deviceProfileRepository.save(profile);
        Optional<DeviceProfile> reloaded = deviceProfileRepository.findById(saved.getId());
        
        // Then
        assertTrue(reloaded.isPresent());
        assertEquals(4, reloaded.get().getTelemetryDefinitions().size());
        
        // 验证每个遥测定义类型
        List<TelemetryDefinition> reloadedDefs = reloaded.get().getTelemetryDefinitions();
        assertTrue(reloadedDefs.stream().anyMatch(def -> 
            "cpu_usage".equals(def.getKey()) && DataType.DOUBLE == def.getDataType()));
        assertTrue(reloadedDefs.stream().anyMatch(def -> 
            "memory_usage".equals(def.getKey()) && DataType.LONG == def.getDataType()));
        assertTrue(reloadedDefs.stream().anyMatch(def -> 
            "status".equals(def.getKey()) && DataType.STRING == def.getDataType()));
        assertTrue(reloadedDefs.stream().anyMatch(def -> 
            "is_active".equals(def.getKey()) && DataType.BOOLEAN == def.getDataType()));
    }
    
    // ==================== Helper Methods ====================
    
    private DeviceProfile createTestProfile(String name, String description) {
        DeviceProfile profile = new DeviceProfile();
        profile.setId(DeviceProfileId.random());
        profile.setName(name);
        profile.setDescription(description);
        profile.setStrictMode(false);
        profile.setDataSourceType(DeviceProfile.DataSourceType.MQTT);
        profile.setTelemetryDefinitions(new ArrayList<>());
        profile.setCreatedTime(System.currentTimeMillis());
        return profile;
    }
}

