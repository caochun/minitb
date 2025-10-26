package com.minitb.web.controller;

import com.minitb.dao.device.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.web.dto.ApiResponse;
import com.minitb.web.dto.DeviceDto;
import io.javalin.http.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备管理 Controller
 */
@Slf4j
@RequiredArgsConstructor
public class DeviceController {
    
    private final DeviceService deviceService;
    
    /**
     * 获取所有设备
     * GET /api/devices
     */
    public void getAllDevices(Context ctx) {
        try {
            List<Device> devices = deviceService.findAll();
            List<DeviceDto> deviceDtos = devices.stream()
                    .map(DeviceDto::fromDomain)
                    .collect(Collectors.toList());
            ctx.json(ApiResponse.success(deviceDtos));
        } catch (Exception e) {
            log.error("获取设备列表失败", e);
            ctx.status(500).json(ApiResponse.error("获取设备列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取设备
     * GET /api/devices/{id}
     */
    public void getDeviceById(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            DeviceId deviceId = DeviceId.fromString(id);
            Device device = deviceService.getById(deviceId);
            ctx.json(ApiResponse.success(DeviceDto.fromDomain(device)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error("无效的设备ID"));
        } catch (Exception e) {
            log.error("获取设备失败", e);
            ctx.status(404).json(ApiResponse.error("设备不存在: " + e.getMessage()));
        }
    }
    
    /**
     * 创建设备
     * POST /api/devices
     */
    public void createDevice(Context ctx) {
        try {
            // 解析请求体
            String name = ctx.formParam("name");
            String type = ctx.formParam("type");
            String accessToken = ctx.formParam("accessToken");
            String deviceProfileIdStr = ctx.formParam("deviceProfileId");
            
            if (name == null || name.trim().isEmpty()) {
                ctx.status(400).json(ApiResponse.error("设备名称不能为空"));
                return;
            }
            if (type == null || type.trim().isEmpty()) {
                ctx.status(400).json(ApiResponse.error("设备类型不能为空"));
                return;
            }
            
            // 创建设备
            Device device = new Device(name, type, accessToken);
            if (deviceProfileIdStr != null && !deviceProfileIdStr.trim().isEmpty()) {
                device.setDeviceProfileId(DeviceProfileId.fromString(deviceProfileIdStr));
            }
            
            Device savedDevice = deviceService.save(device);
            ctx.status(201).json(ApiResponse.success("设备创建成功", DeviceDto.fromDomain(savedDevice)));
        } catch (Exception e) {
            log.error("创建设备失败", e);
            ctx.status(400).json(ApiResponse.error("创建设备失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新设备
     * PUT /api/devices/{id}
     */
    public void updateDevice(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            DeviceId deviceId = DeviceId.fromString(id);
            
            // 获取现有设备
            Device device = deviceService.getById(deviceId);
            
            // 更新字段
            String name = ctx.formParam("name");
            String type = ctx.formParam("type");
            String accessToken = ctx.formParam("accessToken");
            String deviceProfileIdStr = ctx.formParam("deviceProfileId");
            
            if (name != null && !name.trim().isEmpty()) {
                device.setName(name);
            }
            if (type != null && !type.trim().isEmpty()) {
                device.setType(type);
            }
            if (accessToken != null) {
                device.setAccessToken(accessToken);
            }
            if (deviceProfileIdStr != null && !deviceProfileIdStr.trim().isEmpty()) {
                device.setDeviceProfileId(DeviceProfileId.fromString(deviceProfileIdStr));
            }
            
            Device updatedDevice = deviceService.save(device);
            ctx.json(ApiResponse.success("设备更新成功", DeviceDto.fromDomain(updatedDevice)));
        } catch (Exception e) {
            log.error("更新设备失败", e);
            ctx.status(400).json(ApiResponse.error("更新设备失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除设备
     * DELETE /api/devices/{id}
     */
    public void deleteDevice(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            DeviceId deviceId = DeviceId.fromString(id);
            deviceService.delete(deviceId);
            ctx.json(ApiResponse.success("设备删除成功", null));
        } catch (Exception e) {
            log.error("删除设备失败", e);
            ctx.status(400).json(ApiResponse.error("删除设备失败: " + e.getMessage()));
        }
    }
}

