package com.minitb.actor;

/**
 * Actor 接口
 * 所有 Actor 必须实现此接口
 */
public interface MiniTbActor {
    
    /**
     * 处理消息
     * @param msg 消息
     * @return true 表示消息已处理，false 表示不认识此消息
     */
    boolean process(MiniTbActorMsg msg);
    
    /**
     * 获取 Actor ID
     */
    String getActorId();
    
    /**
     * 初始化
     */
    default void init(MiniTbActorContext ctx) throws Exception {
        // 默认不需要初始化
    }
    
    /**
     * 销毁
     */
    default void destroy() throws Exception {
        // 默认不需要清理
    }
}

