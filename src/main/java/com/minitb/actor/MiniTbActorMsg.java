package com.minitb.actor;

/**
 * Actor 消息接口
 * 所有通过 Actor 系统传递的消息都必须实现此接口
 * 
 * 设计说明:
 * Actor 消息类型 (ActorMsgType) 用于 Actor 系统的消息路由，决定"消息发给哪个 Actor"
 * 业务消息类型 (TbMsgType) 用于业务逻辑处理，决定"消息内容是什么类型的数据"
 * 
 * 类比: Actor 消息是信封(决定地址)，业务消息是信件内容(决定内容类型)
 */
public interface MiniTbActorMsg {
    
    /**
     * 获取 Actor 消息类型 (用于路由)
     */
    ActorMsgType getActorMsgType();
    
    /**
     * 当 Actor 停止时的回调
     */
    default void onActorStopped() {
        // 默认不处理
    }
    
    /**
     * Actor 消息类型枚举
     * 
     * 作用: 决定消息在 Actor 系统中的路由路径
     * - 从哪个 Actor 发出
     * - 发送到哪个 Actor
     * - Actor 如何处理这类消息
     */
    enum ActorMsgType {
        // === 传输层到设备 Actor ===
        TRANSPORT_TO_DEVICE_MSG,        // 从传输层(MQTT/HTTP) → DeviceActor
        
        // === 设备 Actor 到规则引擎 Actor ===
        TO_RULE_ENGINE_MSG,             // 从 DeviceActor → RuleEngineActor
        
        // === 设备管理消息 ===
        DEVICE_CONNECTED_MSG,           // 通知 DeviceActor: 设备已连接
        DEVICE_DISCONNECTED_MSG,        // 通知 DeviceActor: 设备已断开
        DEVICE_UPDATE_MSG,              // 通知 DeviceActor: 设备信息更新
        
        // === 系统消息 ===
        SYSTEM_SHUTDOWN_MSG             // 通知所有 Actor: 系统关闭
    }
}

