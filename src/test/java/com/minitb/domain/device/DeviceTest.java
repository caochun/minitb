package com.minitb.domain.device;

import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Device 领域模型单元测试
 * 
 * 测试重点：
 * - 对象创建和属性设置
 * - 业务规则验证
 * - 不依赖任何外部框架
 */
class DeviceTest {
    
    @Test
    void testDeviceCreation() {
        // Given
        DeviceId deviceId = DeviceId.random();
        DeviceProfileId profileId = DeviceProfileId.random();
        String name = "Temperature Sensor";
        String type = "SENSOR";
        String accessToken = "test-token-123";
        long createdTime = System.currentTimeMillis();
        
        // When
        Device device = new Device();
        device.setId(deviceId);
        device.setName(name);
        device.setType(type);
        device.setDeviceProfileId(profileId);
        device.setAccessToken(accessToken);
        device.setCreatedTime(createdTime);
        
        // Then
        assertNotNull(device);
        assertEquals(deviceId, device.getId());
        assertEquals(name, device.getName());
        assertEquals(type, device.getType());
        assertEquals(profileId, device.getDeviceProfileId());
        assertEquals(accessToken, device.getAccessToken());
        assertEquals(createdTime, device.getCreatedTime());
    }
    
    @Test
    void testDeviceIdUniqueness() {
        // Given & When
        DeviceId id1 = DeviceId.random();
        DeviceId id2 = DeviceId.random();
        
        // Then
        assertNotEquals(id1, id2, "不同的 DeviceId 应该不相等");
        assertNotEquals(id1.getId(), id2.getId(), "不同的 UUID 应该不相等");
    }
    
    @Test
    void testDeviceIdEquality() {
        // Given
        DeviceId id1 = DeviceId.random();
        DeviceId id2 = DeviceId.fromUUID(id1.getId());
        
        // Then
        assertEquals(id1, id2, "相同 UUID 的 DeviceId 应该相等");
        assertEquals(id1.hashCode(), id2.hashCode(), "相同 DeviceId 的 hashCode 应该相等");
    }
    
    @Test
    void testDeviceEquality() {
        // Given
        DeviceId deviceId = DeviceId.random();
        DeviceProfileId profileId = DeviceProfileId.random();
        
        Device device1 = new Device();
        device1.setId(deviceId);
        device1.setName("Device A");
        device1.setDeviceProfileId(profileId);
        
        Device device2 = new Device();
        device2.setId(deviceId);
        device2.setName("Device A");
        device2.setDeviceProfileId(profileId);
        
        // Then
        assertEquals(device1, device2, "相同 ID 的设备应该相等");
    }
    
    @Test
    void testDeviceWithNullId() {
        // Given
        Device device = new Device();
        device.setName("Test Device");
        
        // Then
        assertNull(device.getId(), "新创建的设备 ID 应该为 null");
    }
    
    @Test
    void testAccessTokenValidation() {
        // Given
        Device device = new Device();
        String validToken = "valid-access-token-abc123";
        
        // When
        device.setAccessToken(validToken);
        
        // Then
        assertEquals(validToken, device.getAccessToken());
        assertNotNull(device.getAccessToken());
        assertFalse(device.getAccessToken().isEmpty());
    }
}

