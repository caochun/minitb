package com.minitb.actor;

/**
 * Actor 上下文接口
 * 提供 Actor 与系统交互的能力
 */
public interface MiniTbActorContext {
    
    /**
     * 发送消息给其他 Actor
     * @param actorId 目标 Actor ID
     * @param msg 消息
     */
    void tell(String actorId, MiniTbActorMsg msg);
    
    /**
     * 发送高优先级消息
     * @param actorId 目标 Actor ID
     * @param msg 消息
     */
    void tellWithHighPriority(String actorId, MiniTbActorMsg msg);
    
    /**
     * 获取当前 Actor 的 ID
     */
    String getSelf();
    
    /**
     * 停止指定 Actor
     */
    void stop(String actorId);
}



