package com.minitb.domain.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQTT 协议配置
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MqttConfig implements ProtocolConfig {
    
    /**
     * MQTT Topic（可选，用于覆盖默认topic）
     * 默认使用 "v1/devices/me/telemetry"
     */
    private String topic;
    
    /**
     * QoS 等级
     */
    @Builder.Default
    private int qos = 1;
    
    /**
     * 是否保留消息
     */
    @Builder.Default
    private boolean retain = false;
    
    @Override
    public String getProtocolType() {
        return "MQTT";
    }
}

