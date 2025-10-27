package com.minitb.infrastructure.web.controller;

import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.infrastructure.web.dto.DeviceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备管理 REST API
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {
    
    private final DeviceService deviceService;
    
    /**
     * 获取所有设备
     * GET /api/devices
     */
    @GetMapping
    public List<DeviceDto> getAllDevices() {
        log.debug("API: 获取所有设备");
        
        return deviceService.findAll().stream()
                .map(DeviceDto::fromDomain)
                .collect(Collectors.toList());
    }
}

