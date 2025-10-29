package com.minitb.actor.msg;

import com.minitb.actor.MiniTbActorMsg;
import com.minitb.domain.messaging.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发送到规则链Actor的消息
 * 
 * 用于将设备消息路由到特定的规则链进行处理
 */
@AllArgsConstructor
@Getter
public class ToRuleChainMsg implements MiniTbActorMsg {
    
    private final Message message;
    
    @Override
    public ActorMsgType getActorMsgType() {
        return ActorMsgType.TO_RULE_CHAIN_MSG;
    }
    
    @Override
    public void onActorStopped() {
        // RuleChainActor已停止，消息无法处理
    }
}

