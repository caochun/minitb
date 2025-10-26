package com.minitb.actor.msg;

import com.minitb.actor.MiniTbActorMsg;
import com.minitb.domain.id.DeviceId;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 从传输层到设备 Actor 的消息
 * 
 * Actor 消息类型: TRANSPORT_TO_DEVICE_MSG
 * 路由: MqttTransportService/HttpTransportService → DeviceActor
 * 
 * 职责:
 * - 携带从传输层(MQTT/HTTP)接收到的原始数据
 * - DeviceActor 会解析 payload，创建 TbMsg (包含业务消息类型)
 * - 然后转发给 RuleEngineActor
 */
@Data
@AllArgsConstructor
public class TransportToDeviceMsg implements MiniTbActorMsg {
    
    private final DeviceId deviceId;
    private final String accessToken;
    private final String payload;      // JSON 格式的遥测数据
    private final long timestamp;
    
    @Override
    public ActorMsgType getActorMsgType() {
        return ActorMsgType.TRANSPORT_TO_DEVICE_MSG;
    }
}

