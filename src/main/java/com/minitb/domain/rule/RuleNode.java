package com.minitb.domain.rule;

import com.minitb.domain.messaging.Message;
import com.minitb.domain.id.RuleNodeId;

/**
 * 规则节点接口
 * 
 * 所有规则节点都实现此接口
 * 采用责任链模式处理消息
 * 
 * 设计模式：
 * 1. 责任链模式 - 消息在节点间传递
 * 2. 策略模式 - 不同类型的节点有不同的处理策略
 * 3. 模板方法模式 - 统一的节点生命周期管理
 */
public interface RuleNode {
    
    /**
     * 初始化节点
     * @param config 节点配置
     * @param context 节点上下文
     */
    void init(RuleNodeConfig config, RuleNodeContext context);
    
    /**
     * 处理消息
     * @param msg 待处理的消息
     * @param context 节点上下文
     */
    void onMsg(Message msg, RuleNodeContext context);
    
    /**
     * 设置下一个节点（兼容旧接口）
     * @param next 下一个规则节点
     */
    void setNext(RuleNode next);
    
    /**
     * 获取节点ID
     * @return 节点ID
     */
    RuleNodeId getId();
    
    /**
     * 获取节点名称
     * @return 节点名称
     */
    String getName();
    
    /**
     * 获取节点类型
     * @return 节点类型
     */
    String getNodeType();
    
    /**
     * 获取节点类型（兼容方法）
     * @return 节点类型
     */
    default String getType() {
        return getNodeType();
    }
    
    /**
     * 销毁节点
     */
    default void destroy() {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 是否支持异步处理
     * @return true表示支持异步处理
     */
    default boolean isAsyncSupported() {
        return false;
    }
}



