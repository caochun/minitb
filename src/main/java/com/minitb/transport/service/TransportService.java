package com.minitb.transport.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minitb.common.entity.Device;
import com.minitb.common.kv.*;
import com.minitb.common.msg.TbMsg;
import com.minitb.common.msg.TbMsgType;
import com.minitb.ruleengine.RuleEngineService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 传输服务 - 核心数据流的第二层
 * 职责：
 * 1. 设备认证
 * 2. 消息转换（JSON -> TbMsg）
 * 3. 限流检查
 * 4. 转发到规则引擎
 */
@Slf4j
public class TransportService {
    
    // 设备注册表（简化实现，实际应该查数据库）
    private final Map<String, Device> deviceRegistry = new ConcurrentHashMap<>();
    
    // 规则引擎服务
    private final RuleEngineService ruleEngineService;
    
    public TransportService(RuleEngineService ruleEngineService) {
        this.ruleEngineService = ruleEngineService;
        initDefaultDevices();
    }

    /**
     * 初始化默认测试设备
     */
    private void initDefaultDevices() {
        // MQTT设备
        Device device1 = new Device("温度传感器-01", "TemperatureSensor", "test-token-001");
        Device device2 = new Device("湿度传感器-01", "HumiditySensor", "test-token-002");
        
        deviceRegistry.put(device1.getAccessToken(), device1);
        deviceRegistry.put(device2.getAccessToken(), device2);
        
        // Prometheus 数据源设备1: Prometheus 自身进程
        Device promDevice = new Device("Prometheus进程监控", "PrometheusMonitor", "test-token-prom");
        deviceRegistry.put(promDevice.getAccessToken(), promDevice);
        
        // Prometheus 数据源设备2: node_exporter 系统监控
        Device nodeDevice = new Device("系统资源监控", "NodeExporter", "test-token-node");
        deviceRegistry.put(nodeDevice.getAccessToken(), nodeDevice);
        
        log.info("初始化默认设备: {}, {}, {}, {}", 
                 device1.getName(), device2.getName(), promDevice.getName(), nodeDevice.getName());
    }

    /**
     * 注册新设备
     */
    public void registerDevice(Device device) {
        deviceRegistry.put(device.getAccessToken(), device);
        log.info("设备注册成功: {} (token: {})", device.getName(), device.getAccessToken());
    }

    /**
     * 处理遥测数据上报
     * 这是核心入口方法！
     */
    public void processTelemetry(String accessToken, String telemetryJson) {
        log.info("接收到遥测数据: token={}, data={}", accessToken, telemetryJson);
        
        // 1. 设备认证
        Device device = authenticateDevice(accessToken);
        if (device == null) {
            log.warn("设备认证失败: token={}", accessToken);
            return;
        }
        
        // 2. 限流检查（简化实现，实际应该有更复杂的限流逻辑）
        if (!checkRateLimit(device)) {
            log.warn("设备 {} 超过速率限制", device.getName());
            return;
        }
        
        // 3. 解析JSON数据为强类型KvEntry
        List<TsKvEntry> tsKvEntries;
        try {
            tsKvEntries = parseJsonToKvEntries(telemetryJson);
            log.info("解析得到 {} 个遥测数据点", tsKvEntries.size());
        } catch (Exception e) {
            log.error("JSON解析失败: {}", telemetryJson, e);
            return;
        }
        
        // 4. 创建元数据
        Map<String, String> metaData = createMetaData(device);
        
        // 5. 创建TbMsg消息（同时包含JSON和强类型数据）
        TbMsg tbMsg = TbMsg.newMsg(
            TbMsgType.POST_TELEMETRY_REQUEST,
            device.getId(),
            metaData,
            telemetryJson,      // 保留原始JSON用于兼容性
            tsKvEntries         // 强类型数据用于高效处理
        );
        tbMsg.setTenantId(device.getTenantId());
        
        log.info("创建TbMsg: {}, 包含 {} 个强类型数据点", tbMsg.getId(), tsKvEntries.size());
        
        // 6. 发送到规则引擎 - 这是数据流的关键转折点！
        sendToRuleEngine(tbMsg);
    }

    /**
     * 处理属性上报
     */
    public void processAttributes(String accessToken, String attributesJson) {
        log.info("接收到属性数据: token={}, data={}", accessToken, attributesJson);
        
        Device device = authenticateDevice(accessToken);
        if (device == null) {
            return;
        }
        
        Map<String, String> metaData = createMetaData(device);
        TbMsg tbMsg = TbMsg.newMsg(
            TbMsgType.POST_ATTRIBUTES_REQUEST,
            device.getId(),
            metaData,
            attributesJson
        );
        tbMsg.setTenantId(device.getTenantId());
        
        sendToRuleEngine(tbMsg);
    }

    /**
     * 设备认证
     */
    private Device authenticateDevice(String accessToken) {
        Device device = deviceRegistry.get(accessToken);
        if (device != null) {
            log.debug("设备认证成功: {}", device.getName());
        } else {
            log.warn("设备认证失败: token={}", accessToken);
        }
        return device;
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
     * 发送消息到规则引擎
     * 这是TransportService的核心输出
     */
    private void sendToRuleEngine(TbMsg tbMsg) {
        log.info("发送消息到规则引擎: {}", tbMsg.getId());
        
        // 在实际ThingsBoard中，这里会：
        // 1. 发送到Kafka/RabbitMQ消息队列
        // 2. 异步处理
        // 3. 负载均衡到不同的Rule Engine实例
        
        // 这里简化为直接调用
        ruleEngineService.processMessage(tbMsg);
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
     * 获取已注册设备列表
     */
    public Map<String, Device> getDeviceRegistry() {
        return new HashMap<>(deviceRegistry);
    }
}


