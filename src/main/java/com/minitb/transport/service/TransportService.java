package com.minitb.transport.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minitb.actor.MiniTbActorSystem;
import com.minitb.actor.device.DeviceActor;
import com.minitb.actor.msg.TransportToDeviceMsg;
import com.minitb.actor.ruleengine.RuleEngineActor;
import com.minitb.application.service.DeviceService;
import com.minitb.domain.device.Device;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.telemetry.*;
import com.minitb.domain.messaging.Message;
import com.minitb.domain.messaging.MessageType;
import com.minitb.ruleengine.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 传输服务 - 核心数据流的第二层
 * 职责：
 * 1. 设备认证（通过 DeviceService）
 * 2. 消息转换（JSON -> Actor 消息）
 * 3. 限流检查
 * 4. 通过 Actor 系统异步转发
 * 
 * Actor 系统架构：
 * - 每个设备有独立的 DeviceActor
 * - 消息通过 Actor 系统异步传递
 * - 自动队列缓冲和背压保护
 */
@Component
@Slf4j
public class TransportService {
    
    // 设备服务（替代原来的 ConcurrentHashMap）
    private final DeviceService deviceService;
    
    // 规则引擎服务
    private final RuleEngineService ruleEngineService;
    
    // Actor 系统
    private MiniTbActorSystem actorSystem;
    
    public TransportService(DeviceService deviceService, RuleEngineService ruleEngineService) {
        this.deviceService = deviceService;
        this.ruleEngineService = ruleEngineService;
    }
    
    /**
     * 设置 Actor 系统
     * 必须在使用前调用
     */
    public void setActorSystem(MiniTbActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        log.info("传输服务已设置 Actor 系统");
        
        // 创建规则引擎 Actor
        RuleEngineActor ruleEngineActor = new RuleEngineActor(ruleEngineService);
        actorSystem.createActor("RuleEngineActor", ruleEngineActor);
        log.info("规则引擎 Actor 已创建");
        
        // 为数据库中的所有设备创建 DeviceActor
        List<Device> devices = deviceService.findAll();
        for (Device device : devices) {
            createDeviceActor(device);
        }
        log.info("为 {} 个设备创建了 DeviceActor", devices.size());
    }
    
    /**
     * 创建设备 Actor
     */
    private void createDeviceActor(Device device) {
        if (actorSystem == null) {
            log.warn("Actor 系统未初始化，无法创建 DeviceActor");
            return;
        }
        
        DeviceActor deviceActor = new DeviceActor(device.getId(), device);
        actorSystem.createActor(deviceActor.getActorId(), deviceActor);
        log.debug("为设备 {} 创建 DeviceActor: {}", device.getName(), deviceActor.getActorId());
    }

    /**
     * 处理遥测数据上报
     * 这是核心入口方法！通过 Actor 系统异步处理
     */
    public void processTelemetry(String accessToken, String telemetryJson) {
        log.debug("接收到遥测数据: token={}, data={}", accessToken, telemetryJson);
        
        // 1. 设备认证
        Device device = authenticateDevice(accessToken);
        if (device == null) {
            log.warn("设备认证失败: token={}", accessToken);
            return;
        }
        
        // 2. 限流检查
        if (!checkRateLimit(device)) {
            log.warn("设备 {} 超过速率限制", device.getName());
            return;
        }
        
        // 3. 通过 Actor 系统异步发送
        if (actorSystem == null) {
            log.error("Actor 系统未初始化，无法处理消息");
            return;
        }
        
        TransportToDeviceMsg actorMsg = new TransportToDeviceMsg(
            device.getId(),
            device.getAccessToken(),
            telemetryJson,
            System.currentTimeMillis()
        );
        
        // 使用 DeviceActor 的静态方法获取 Actor ID
        String actorId = DeviceActor.actorIdFor(device.getId());
        
        log.debug("通过 Actor 系统发送消息: deviceId={}, actorId={}", device.getId(), actorId);
        actorSystem.tell(actorId, actorMsg);
    }

    /**
     * 处理属性上报
     * 注意：属性上报目前简化实现，可以考虑也通过 Actor 系统处理
     */
    public void processAttributes(String accessToken, String attributesJson) {
        log.debug("接收到属性数据: token={}, data={}", accessToken, attributesJson);
        
        Device device = authenticateDevice(accessToken);
        if (device == null) {
            return;
        }
        
        // 解析为强类型数据
        List<TsKvEntry> tsKvEntries;
        try {
            tsKvEntries = parseJsonToKvEntries(attributesJson);
        } catch (Exception e) {
            log.error("属性JSON解析失败: {}", attributesJson, e);
            return;
        }
        
        Map<String, String> metaData = createMetaData(device);
        Message tbMsg = Message.newMsg(
            MessageType.POST_ATTRIBUTES_REQUEST,
            device.getId(),
            metaData,
            attributesJson,
            tsKvEntries
        );
        
        // 直接调用规则引擎（简化实现）
        ruleEngineService.processMessage(tbMsg);
    }

    /**
     * 设备认证
     */
    private Device authenticateDevice(String accessToken) {
        return deviceService.findByAccessToken(accessToken)
                .map(device -> {
                    log.debug("设备认证成功: {}", device.getName());
                    return device;
                })
                .orElseGet(() -> {
                    log.warn("设备认证失败: token={}", accessToken);
                    return null;
                });
    }

    /**
     * 限流检查（简化实现）
     */
    private boolean checkRateLimit(Device device) {
        // 实际实现应该使用令牌桶或漏桶算法
        // 这里简化为总是返回true
        return true;
    }

    /**
     * 创建消息元数据
     */
    private Map<String, String> createMetaData(Device device) {
        Map<String, String> metaData = new HashMap<>();
        metaData.put("deviceName", device.getName());
        metaData.put("deviceType", device.getType());
        metaData.put("ts", String.valueOf(System.currentTimeMillis()));
        return metaData;
    }


    /**
     * 解析JSON为强类型KvEntry列表
     * 核心功能：将 JSON 字符串转换为类型安全的 TsKvEntry 列表
     * 
     * 示例输入: {"temperature": 25.5, "humidity": 60, "online": true, "status": "running"}
     * 示例输出: [
     *   BasicTsKvEntry(ts, DoubleDataEntry("temperature", 25.5)),
     *   BasicTsKvEntry(ts, LongDataEntry("humidity", 60)),
     *   BasicTsKvEntry(ts, BooleanDataEntry("online", true)),
     *   BasicTsKvEntry(ts, StringDataEntry("status", "running"))
     * ]
     */
    private List<TsKvEntry> parseJsonToKvEntries(String json) {
        List<TsKvEntry> entries = new ArrayList<>();
        long ts = System.currentTimeMillis();
        
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        
        for (String key : jsonObject.keySet()) {
            JsonElement element = jsonObject.get(key);
            KvEntry kvEntry = null;
            
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isBoolean()) {
                    // 布尔类型
                    kvEntry = new BooleanDataEntry(key, element.getAsBoolean());
                } else if (element.getAsJsonPrimitive().isNumber()) {
                    // 数值类型：判断是整数还是浮点数
                    try {
                        double value = element.getAsDouble();
                        if (value == Math.floor(value) && !Double.isInfinite(value)) {
                            // 整数
                            kvEntry = new LongDataEntry(key, element.getAsLong());
                        } else {
                            // 浮点数
                            kvEntry = new DoubleDataEntry(key, value);
                        }
                    } catch (NumberFormatException e) {
                        // 降级为字符串
                        kvEntry = new StringDataEntry(key, element.getAsString());
                    }
                } else if (element.getAsJsonPrimitive().isString()) {
                    // 字符串类型
                    kvEntry = new StringDataEntry(key, element.getAsString());
                }
            } else if (element.isJsonObject() || element.isJsonArray()) {
                // JSON对象或数组
                kvEntry = new JsonDataEntry(key, element.toString());
            }
            
            if (kvEntry != null) {
                TsKvEntry tsKvEntry = new BasicTsKvEntry(ts, kvEntry);
                entries.add(tsKvEntry);
                log.debug("解析数据点: key={}, type={}, value={}", 
                         key, kvEntry.getDataType(), kvEntry.getValueAsString());
            }
        }
        
        return entries;
    }

    /**
     * 获取所有设备
     */
    public List<Device> getAllDevices() {
        return deviceService.findAll();
    }
}


