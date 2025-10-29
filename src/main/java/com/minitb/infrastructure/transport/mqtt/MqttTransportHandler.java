package com.minitb.infrastructure.transport.mqtt;

import com.minitb.infrastructure.transport.service.TransportService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT传输处理器 - 核心数据流的第一层
 * 职责：
 * 1. 处理MQTT协议消息
 * 2. 设备连接管理
 * 3. 消息解析和转发
 */
@Slf4j
public class MqttTransportHandler extends SimpleChannelInboundHandler<MqttMessage> {
    
    private final TransportService transportService;
    private String deviceToken; // 设备令牌（从CONNECT消息的username获取）
    
    public MqttTransportHandler(TransportService transportService) {
        this.transportService = transportService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("MQTT客户端连接: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttMessageType messageType = msg.fixedHeader().messageType();
        
        switch (messageType) {
            case CONNECT:
                handleConnect(ctx, (MqttConnectMessage) msg);
                break;
            case PUBLISH:
                handlePublish(ctx, (MqttPublishMessage) msg);
                break;
            case SUBSCRIBE:
                handleSubscribe(ctx, (MqttSubscribeMessage) msg);
                break;
            case PINGREQ:
                handlePingReq(ctx);
                break;
            case DISCONNECT:
                handleDisconnect(ctx);
                break;
            default:
                log.warn("不支持的MQTT消息类型: {}", messageType);
        }
    }

    /**
     * 处理CONNECT消息
     */
    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        // 从username获取设备token
        deviceToken = msg.payload().userName();
        
        log.info("设备连接请求: token={}, clientId={}", 
                deviceToken, msg.payload().clientIdentifier());
        
        // 发送CONNACK响应
        MqttConnAckMessage connAck = MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .build();
        ctx.writeAndFlush(connAck);
        
        log.info("设备连接成功: token={}", deviceToken);
    }

    /**
     * 处理PUBLISH消息 - 这是数据上报的入口！
     */
    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        String topic = msg.variableHeader().topicName();
        ByteBuf payload = msg.payload();
        String payloadString = payload.toString(CharsetUtil.UTF_8);
        
        log.info("接收PUBLISH消息: topic={}, payload={}", topic, payloadString);
        
        // 根据topic类型处理不同的消息
        if (topic.startsWith("v1/devices/me/telemetry")) {
            // 遥测数据上报
            transportService.processTelemetry(deviceToken, payloadString);
        } else if (topic.startsWith("v1/devices/me/attributes")) {
            // 属性数据上报
            transportService.processAttributes(deviceToken, payloadString);
        } else {
            log.warn("不支持的topic: {}", topic);
        }
        
        // 如果QoS > 0，需要发送PUBACK
        if (msg.fixedHeader().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
            MqttMessage pubAck = MqttMessageBuilders.pubAck()
                    .packetId(msg.variableHeader().packetId())
                    .build();
            ctx.writeAndFlush(pubAck);
        }
    }

    /**
     * 处理SUBSCRIBE消息
     */
    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        log.info("接收SUBSCRIBE消息: topics={}", 
                msg.payload().topicSubscriptions());
        
        // 发送SUBACK响应
        MqttSubAckMessage subAck = MqttMessageBuilders.subAck()
                .packetId(msg.variableHeader().messageId())
                .addGrantedQos(MqttQoS.AT_MOST_ONCE)
                .build();
        ctx.writeAndFlush(subAck);
    }

    /**
     * 处理PINGREQ消息
     */
    private void handlePingReq(ChannelHandlerContext ctx) {
        MqttMessage pingResp = new MqttMessage(
            new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0)
        );
        ctx.writeAndFlush(pingResp);
    }

    /**
     * 处理DISCONNECT消息
     */
    private void handleDisconnect(ChannelHandlerContext ctx) {
        log.info("设备断开连接: token={}", deviceToken);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("MQTT处理异常", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("MQTT连接关闭: {}", ctx.channel().remoteAddress());
    }
}

