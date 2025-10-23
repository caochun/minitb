package com.minitb.ruleengine.node;

import com.minitb.common.msg.TbMsg;

/**
 * 规则节点接口
 * 所有规则节点都需要实现这个接口
 */
public interface RuleNode {
    
    /**
     * 节点名称
     */
    String getName();
    
    /**
     * 处理消息
     * @param msg 输入消息
     * @return 处理后的消息（可能是null，表示消息被过滤）
     */
    TbMsg onMsg(TbMsg msg);
    
    /**
     * 节点初始化
     */
    default void init() {
        // 默认空实现
    }
    
    /**
     * 节点销毁
     */
    default void destroy() {
        // 默认空实现
    }
}

