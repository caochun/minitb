package com.minitb.domain.rule.node;

import com.minitb.domain.msg.TbMsg;

/**
 * 规则节点接口
 * 
 * 所有规则节点都实现此接口
 * 采用责任链模式处理消息
 */
public interface RuleNode {
    
    /**
     * 处理消息
     * @param msg 待处理的消息
     */
    void onMsg(TbMsg msg);
    
    /**
     * 设置下一个节点
     * @param next 下一个规则节点
     */
    void setNext(RuleNode next);
    
    /**
     * 获取节点名称
     * @return 节点名称
     */
    String getName();
}

