package com.minitb.application.service;

import com.minitb.application.service.impl.DeviceServiceImpl;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.DeviceProfileRepository;
import com.minitb.domain.device.DeviceRepository;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceService 单元测试
 * 
 * 测试重点：
 * - 应用层业务逻辑
 * - Mock Repository（不依赖真实数据库）
 * - 验证 Repository 调用
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
    
    @Mock
    private DeviceRepository deviceRepository; // ✅ Mock Domain 接口
    
    @Mock
    private DeviceProfileRepository deviceProfileRepository;
    
    @InjectMocks
    private DeviceServiceImpl deviceService; // 被测试的应用服务
    
    private Device testDevice;
    private DeviceProfile testProfile;
    
    @BeforeEach
    void setUp() {
        testDevice = new Device();
        testDevice.setId(DeviceId.random());
        testDevice.setName("Test Device");
        testDevice.setType("SENSOR");
        testDevice.setAccessToken("test-token");
        testDevice.setCreatedTime(System.currentTimeMillis());
        
        testProfile = new DeviceProfile();
        testProfile.setId(DeviceProfileId.random());
        testProfile.setName("Test Profile");
        testProfile.setTelemetryDefinitions(new ArrayList<>());
    }
    
    // ==================== Device Tests ====================
    
    @Test
    void testSaveDevice() {
        // Given
        when(deviceRepository.save(any(Device.class))).thenReturn(testDevice);
        
        // When
        Device saved = deviceService.save(testDevice);
        
        // Then
        assertNotNull(saved);
        assertEquals("Test Device", saved.getName());
        verify(deviceRepository, times(1)).save(any(Device.class));
    }
    
    @Test
    void testSaveDeviceGeneratesIdIfNull() {
        // Given
        Device deviceWithoutId = new Device();
        deviceWithoutId.setName("New Device");
        deviceWithoutId.setAccessToken("new-token");
        
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device arg = invocation.getArgument(0);
            return arg;
        });
        
        // When
        Device saved = deviceService.save(deviceWithoutId);
        
        // Then
        assertNotNull(saved.getId(), "应该自动生成 ID");
        assertNotEquals(0, saved.getCreatedTime(), "应该自动生成创建时间");
        verify(deviceRepository).save(any(Device.class));
    }
    
    @Test
    void testFindDeviceById() {
        // Given
        DeviceId deviceId = testDevice.getId();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(testDevice));
        
        // When
        Optional<Device> found = deviceService.findById(deviceId);
        
        // Then
        assertTrue(found.isPresent());
        assertEquals(testDevice.getId(), found.get().getId());
        verify(deviceRepository, times(1)).findById(deviceId);
    }
    
    @Test
    void testFindDeviceByIdNotFound() {
        // Given
        DeviceId nonExistentId = DeviceId.random();
        when(deviceRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        
        // When
        Optional<Device> found = deviceService.findById(nonExistentId);
        
        // Then
        assertFalse(found.isPresent());
        verify(deviceRepository).findById(nonExistentId);
    }
    
    @Test
    void testFindDeviceByAccessToken() {
        // Given
        String token = "test-token";
        when(deviceRepository.findByAccessToken(token)).thenReturn(Optional.of(testDevice));
        
        // When
        Optional<Device> found = deviceService.findByAccessToken(token);
        
        // Then
        assertTrue(found.isPresent());
        assertEquals(token, found.get().getAccessToken());
        verify(deviceRepository).findByAccessToken(token);
    }
    
    @Test
    void testFindAllDevices() {
        // Given
        List<Device> devices = Arrays.asList(
            testDevice,
            createDevice("Device 2", "GATEWAY", "token-2"),
            createDevice("Device 3", "ACTUATOR", "token-3")
        );
        when(deviceRepository.findAll()).thenReturn(devices);
        
        // When
        List<Device> found = deviceService.findAll();
        
        // Then
        assertNotNull(found);
        assertEquals(3, found.size());
        verify(deviceRepository).findAll();
    }
    
    @Test
    void testDeleteDevice() {
        // Given
        DeviceId deviceId = testDevice.getId();
        doNothing().when(deviceRepository).deleteById(deviceId);
        
        // When
        deviceService.delete(deviceId);
        
        // Then
        verify(deviceRepository, times(1)).deleteById(deviceId);
    }
    
    @Test
    void testExistsByAccessToken() {
        // Given
        String token = "existing-token";
        when(deviceRepository.findByAccessToken(token)).thenReturn(Optional.of(testDevice));
        
        // When
        boolean exists = deviceService.existsByAccessToken(token);
        
        // Then
        assertTrue(exists);
        verify(deviceRepository).findByAccessToken(token);
    }
    
    @Test
    void testNotExistsByAccessToken() {
        // Given
        String token = "non-existent-token";
        when(deviceRepository.findByAccessToken(token)).thenReturn(Optional.empty());
        
        // When
        boolean exists = deviceService.existsByAccessToken(token);
        
        // Then
        assertFalse(exists);
        verify(deviceRepository).findByAccessToken(token);
    }
    
    // ==================== DeviceProfile Tests ====================
    
    @Test
    void testSaveProfile() {
        // Given
        when(deviceProfileRepository.save(any(DeviceProfile.class))).thenReturn(testProfile);
        
        // When
        DeviceProfile saved = deviceService.saveProfile(testProfile);
        
        // Then
        assertNotNull(saved);
        assertEquals("Test Profile", saved.getName());
        verify(deviceProfileRepository, times(1)).save(any(DeviceProfile.class));
    }
    
    @Test
    void testSaveProfileGeneratesIdIfNull() {
        // Given
        DeviceProfile profileWithoutId = new DeviceProfile();
        profileWithoutId.setName("New Profile");
        
        when(deviceProfileRepository.save(any(DeviceProfile.class))).thenAnswer(invocation -> {
            DeviceProfile arg = invocation.getArgument(0);
            return arg;
        });
        
        // When
        DeviceProfile saved = deviceService.saveProfile(profileWithoutId);
        
        // Then
        assertNotNull(saved.getId(), "应该自动生成 ID");
        assertNotEquals(0, saved.getCreatedTime(), "应该自动生成创建时间");
        verify(deviceProfileRepository).save(any(DeviceProfile.class));
    }
    
    @Test
    void testFindProfileById() {
        // Given
        DeviceProfileId profileId = testProfile.getId();
        when(deviceProfileRepository.findById(profileId)).thenReturn(Optional.of(testProfile));
        
        // When
        Optional<DeviceProfile> found = deviceService.findProfileById(profileId);
        
        // Then
        assertTrue(found.isPresent());
        assertEquals(profileId, found.get().getId());
        verify(deviceProfileRepository).findById(profileId);
    }
    
    @Test
    void testFindAllProfiles() {
        // Given
        List<DeviceProfile> profiles = Arrays.asList(
            testProfile,
            createProfile("Profile 2"),
            createProfile("Profile 3")
        );
        when(deviceProfileRepository.findAll()).thenReturn(profiles);
        
        // When
        List<DeviceProfile> found = deviceService.findAllProfiles();
        
        // Then
        assertNotNull(found);
        assertEquals(3, found.size());
        verify(deviceProfileRepository).findAll();
    }
    
    @Test
    void testDeleteProfile() {
        // Given
        DeviceProfileId profileId = testProfile.getId();
        doNothing().when(deviceProfileRepository).deleteById(profileId);
        
        // When
        deviceService.deleteProfile(profileId);
        
        // Then
        verify(deviceProfileRepository, times(1)).deleteById(profileId);
    }
    
    // ==================== Helper Methods ====================
    
    private Device createDevice(String name, String type, String accessToken) {
        Device device = new Device();
        device.setId(DeviceId.random());
        device.setName(name);
        device.setType(type);
        device.setAccessToken(accessToken);
        device.setCreatedTime(System.currentTimeMillis());
        return device;
    }
    
    private DeviceProfile createProfile(String name) {
        DeviceProfile profile = new DeviceProfile();
        profile.setId(DeviceProfileId.random());
        profile.setName(name);
        profile.setTelemetryDefinitions(new ArrayList<>());
        profile.setCreatedTime(System.currentTimeMillis());
        return profile;
    }
}


