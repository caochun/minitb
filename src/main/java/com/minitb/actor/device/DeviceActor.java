package com.minitb.actor.device;

import com.minitb.actor.MiniTbActor;
import com.minitb.actor.MiniTbActorContext;
import com.minitb.actor.MiniTbActorMsg;
import com.minitb.actor.msg.ToRuleEngineMsg;
import com.minitb.actor.msg.TransportToDeviceMsg;
import com.minitb.domain.entity.Device;
import com.minitb.domain.entity.DeviceId;
import com.minitb.domain.ts.*;
import com.minitb.domain.msg.TbMsg;
import com.minitb.domain.msg.TbMsgType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备 Actor
 * 
 * 职责:
 * 1. 管理设备会话（连接/断开）
 * 2. 处理设备上报的遥测数据
 * 3. 转发消息到规则引擎
 * 4. 维护设备状态
 * 
 * 优势:
 * - 每个设备独立的消息队列，互不干扰
 * - 同一设备的消息串行处理，状态一致性
 * - 异步处理，不阻塞传输层
 */
@Slf4j
public class DeviceActor implements MiniTbActor {
    
    private static final String RULE_ENGINE_ACTOR_ID = "RuleEngineActor";
    
    private final DeviceId deviceId;
    private final Device device;
    
    // 设备状态
    private volatile boolean connected = false;
    private volatile long lastActivityTime = System.currentTimeMillis();
    
    // 会话管理（一个设备可能有多个会话，例如MQTT多个连接）
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    // Actor 上下文
    private MiniTbActorContext ctx;
    
    public DeviceActor(DeviceId deviceId, Device device) {
        this.deviceId = deviceId;
        this.device = device;
    }
    
    @Override
    public void init(MiniTbActorContext ctx) throws Exception {
        this.ctx = ctx;
        log.info("[{}] Device Actor 初始化: {}", deviceId, device.getName());
    }
    
    @Override
    public boolean process(MiniTbActorMsg msg) {
        switch (msg.getActorMsgType()) {
            case TRANSPORT_TO_DEVICE_MSG:
                onTransportMsg((TransportToDeviceMsg) msg);
                return true;
            case DEVICE_CONNECTED_MSG:
                onDeviceConnected();
                return true;
            case DEVICE_DISCONNECTED_MSG:
                onDeviceDisconnected();
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 处理传输层消息
     */
    private void onTransportMsg(TransportToDeviceMsg msg) {
        log.debug("[{}] 收到遥测数据: {}", deviceId, msg.getPayload());
        
        // 更新最后活动时间
        lastActivityTime = System.currentTimeMillis();
        
        // 解析 JSON 为强类型数据
        List<TsKvEntry> tsKvEntries = parseJsonToKvEntries(msg.getPayload());
        
        // 创建 TbMsg（包含强类型数据）
        TbMsg tbMsg = TbMsg.newMsg(
                TbMsgType.POST_TELEMETRY_REQUEST,
                deviceId,
                null,  // metaData
                msg.getPayload(),
                tsKvEntries
        );
        
        // 转发到规则引擎
        ctx.tell(RULE_ENGINE_ACTOR_ID, new ToRuleEngineMsg(tbMsg));
        
        log.debug("[{}] 消息已转发到规则引擎，包含 {} 个数据点", deviceId, tsKvEntries.size());
    }
    
    /**
     * 解析 JSON 为强类型 KvEntry 列表
     */
    private List<TsKvEntry> parseJsonToKvEntries(String jsonStr) {
        List<TsKvEntry> entries = new ArrayList<>();
        
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();
            long ts = System.currentTimeMillis();
            
            // 如果 JSON 中包含 timestamp 字段，使用该时间戳
            if (jsonObject.has("timestamp")) {
                ts = jsonObject.get("timestamp").getAsLong();
            }
            
            // 遍历所有字段
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                
                // 跳过一些特殊字段
                if (key.equals("timestamp") || key.equals("deviceId") || 
                    key.equals("deviceName") || key.equals("sendTimeNanos")) {
                    continue;
                }
                
                // 根据 JSON 类型创建相应的 TsKvEntry
                TsKvEntry kvEntry = createKvEntry(key, value, ts);
                if (kvEntry != null) {
                    entries.add(kvEntry);
                }
            }
            
        } catch (Exception e) {
            log.error("[{}] JSON 解析失败: {}", deviceId, jsonStr, e);
        }
        
        return entries;
    }
    
    /**
     * 根据 JSON 元素类型创建 KvEntry
     */
    private TsKvEntry createKvEntry(String key, JsonElement value, long ts) {
        if (value.isJsonNull()) {
            return null;
        }
        
        KvEntry kvEntry = null;
        
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isNumber()) {
                // 数字类型：优先尝试 long，再尝试 double
                try {
                    double numValue = value.getAsDouble();
                    if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                        // 整数
                        kvEntry = new LongDataEntry(key, value.getAsLong());
                    } else {
                        // 浮点数
                        kvEntry = new DoubleDataEntry(key, numValue);
                    }
                } catch (NumberFormatException e) {
                    kvEntry = new StringDataEntry(key, value.getAsString());
                }
            } else if (value.getAsJsonPrimitive().isBoolean()) {
                kvEntry = new BooleanDataEntry(key, value.getAsBoolean());
            } else if (value.getAsJsonPrimitive().isString()) {
                kvEntry = new StringDataEntry(key, value.getAsString());
            }
        } else if (value.isJsonObject() || value.isJsonArray()) {
            // JSON 对象或数组，转换为 JSON 字符串存储
            kvEntry = new JsonDataEntry(key, value.toString());
        }
        
        return kvEntry != null ? new BasicTsKvEntry(ts, kvEntry) : null;
    }
    
    /**
     * 处理设备连接
     */
    private void onDeviceConnected() {
        connected = true;
        lastActivityTime = System.currentTimeMillis();
        log.info("[{}] 设备已连接: {}", deviceId, device.getName());
    }
    
    /**
     * 处理设备断开
     */
    private void onDeviceDisconnected() {
        connected = false;
        sessions.clear();
        log.info("[{}] 设备已断开: {}", deviceId, device.getName());
    }
    
    @Override
    public void destroy() throws Exception {
        log.info("[{}] Device Actor 销毁: {}", deviceId, device.getName());
        sessions.clear();
    }
    
    @Override
    public String getActorId() {
        return "Device:" + deviceId.getId().toString();
    }
    
    /**
     * 会话信息
     */
    private static class SessionInfo {
        private final String sessionId;
        private final long createTime;
        
        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.createTime = System.currentTimeMillis();
        }
    }
}

