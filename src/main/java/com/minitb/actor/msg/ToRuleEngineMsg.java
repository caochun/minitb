package com.minitb.actor.msg;

import com.minitb.actor.MiniTbActorMsg;
import com.minitb.domain.messaging.TbMsg;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发送到规则引擎的消息
 * 
 * Actor 消息类型: TO_RULE_ENGINE_MSG
 * 路由: DeviceActor → RuleEngineActor
 * 
 * 职责:
 * - 携带业务消息 TbMsg (包含业务消息类型: POST_TELEMETRY_REQUEST, ALARM, etc.)
 * - RuleEngineActor 会根据 TbMsg.type 决定如何处理业务逻辑
 * 
 * 示例:
 * ActorMsgType = TO_RULE_ENGINE_MSG (Actor层: 告诉系统发给 RuleEngineActor)
 * TbMsg.type = POST_TELEMETRY_REQUEST (业务层: 告诉规则引擎这是遥测数据)
 */
@Data
@AllArgsConstructor
public class ToRuleEngineMsg implements MiniTbActorMsg {
    
    private final TbMsg tbMsg;  // 包含业务消息类型和数据
    
    @Override
    public ActorMsgType getActorMsgType() {
        return ActorMsgType.TO_RULE_ENGINE_MSG;
    }
}

