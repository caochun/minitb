package com.minitb.infrastructure.persistence.repository;

import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceRepository;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JPA Device Repository Adapter 集成测试
 * 
 * 测试重点：
 * - 真实数据库操作（H2 in-memory）
 * - Adapter 正确实现 Domain Repository 接口
 * - Entity ↔ Domain 对象转换
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 每个测试后自动回滚
class JpaDeviceRepositoryAdapterTest {
    
    @Autowired
    private DeviceRepository deviceRepository; // ✅ 注入的是 Domain 接口
    
    @Test
    void testSaveDevice() {
        // Given
        Device device = createTestDevice("Test Device", "SENSOR", "token-123");
        
        // When
        Device saved = deviceRepository.save(device);
        
        // Then
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Test Device", saved.getName());
        assertEquals("SENSOR", saved.getType());
        assertEquals("token-123", saved.getAccessToken());
    }
    
    @Test
    void testFindById() {
        // Given
        Device device = createTestDevice("Device A", "SENSOR", "token-a");
        Device saved = deviceRepository.save(device);
        
        // When
        Optional<Device> found = deviceRepository.findById(saved.getId());
        
        // Then
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("Device A", found.get().getName());
    }
    
    @Test
    void testFindByIdNotFound() {
        // Given
        DeviceId nonExistentId = DeviceId.random();
        
        // When
        Optional<Device> found = deviceRepository.findById(nonExistentId);
        
        // Then
        assertFalse(found.isPresent());
    }
    
    @Test
    void testFindByAccessToken() {
        // Given
        Device device = createTestDevice("Device B", "GATEWAY", "unique-token-b");
        deviceRepository.save(device);
        
        // When
        Optional<Device> found = deviceRepository.findByAccessToken("unique-token-b");
        
        // Then
        assertTrue(found.isPresent());
        assertEquals("Device B", found.get().getName());
        assertEquals("unique-token-b", found.get().getAccessToken());
    }
    
    @Test
    void testFindByAccessTokenNotFound() {
        // When
        Optional<Device> found = deviceRepository.findByAccessToken("non-existent-token");
        
        // Then
        assertFalse(found.isPresent());
    }
    
    @Test
    void testFindAll() {
        // Given
        deviceRepository.save(createTestDevice("Device 1", "SENSOR", "token-1"));
        deviceRepository.save(createTestDevice("Device 2", "GATEWAY", "token-2"));
        deviceRepository.save(createTestDevice("Device 3", "ACTUATOR", "token-3"));
        
        // When
        List<Device> devices = deviceRepository.findAll();
        
        // Then
        assertNotNull(devices);
        assertTrue(devices.size() >= 3, "应该至少有 3 个设备");
    }
    
    @Test
    void testUpdateDevice() {
        // Given
        Device device = createTestDevice("Original Name", "SENSOR", "token-update");
        Device saved = deviceRepository.save(device);
        
        // When - 更新设备名称
        saved.setName("Updated Name");
        Device updated = deviceRepository.save(saved);
        
        // Then
        assertEquals(saved.getId(), updated.getId(), "ID 应该保持不变");
        assertEquals("Updated Name", updated.getName(), "名称应该已更新");
    }
    
    @Test
    void testDeleteDevice() {
        // Given
        Device device = createTestDevice("To Be Deleted", "SENSOR", "token-delete");
        Device saved = deviceRepository.save(device);
        DeviceId deviceId = saved.getId();
        
        // When
        deviceRepository.deleteById(deviceId);
        
        // Then
        Optional<Device> found = deviceRepository.findById(deviceId);
        assertFalse(found.isPresent(), "设备应该已被删除");
    }
    
    @Test
    void testSaveDeviceWithProfile() {
        // Given
        DeviceProfileId profileId = DeviceProfileId.random();
        Device device = createTestDevice("Device with Profile", "SENSOR", "token-profile");
        device.setDeviceProfileId(profileId);
        
        // When
        Device saved = deviceRepository.save(device);
        
        // Then
        assertNotNull(saved.getDeviceProfileId());
        assertEquals(profileId, saved.getDeviceProfileId());
    }
    
    @Test
    void testEntityToDomainConversion() {
        // Given
        Device originalDevice = createTestDevice("Conversion Test", "SENSOR", "token-convert");
        originalDevice.setCreatedTime(123456789L);
        
        // When - 保存并重新加载（会经过 Entity ↔ Domain 转换）
        Device saved = deviceRepository.save(originalDevice);
        Optional<Device> reloaded = deviceRepository.findById(saved.getId());
        
        // Then
        assertTrue(reloaded.isPresent());
        Device reloadedDevice = reloaded.get();
        assertEquals(saved.getName(), reloadedDevice.getName());
        assertEquals(saved.getType(), reloadedDevice.getType());
        assertEquals(saved.getAccessToken(), reloadedDevice.getAccessToken());
        assertEquals(saved.getCreatedTime(), reloadedDevice.getCreatedTime());
    }
    
    // ==================== Helper Methods ====================
    
    private Device createTestDevice(String name, String type, String accessToken) {
        Device device = new Device();
        device.setId(DeviceId.random());
        device.setName(name);
        device.setType(type);
        device.setAccessToken(accessToken);
        device.setCreatedTime(System.currentTimeMillis());
        return device;
    }
}

