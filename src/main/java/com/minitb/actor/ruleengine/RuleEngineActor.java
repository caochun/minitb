package com.minitb.actor.ruleengine;

import com.minitb.actor.MiniTbActor;
import com.minitb.actor.MiniTbActorContext;
import com.minitb.actor.MiniTbActorMsg;
import com.minitb.actor.msg.ToRuleEngineMsg;
import com.minitb.ruleengine.RuleEngineService;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则引擎 Actor
 * 
 * 职责:
 * 1. 接收来自设备的消息
 * 2. 通过规则链处理消息
 * 3. 协调规则节点的执行
 * 
 * 优势:
 * - 规则引擎异步处理，不阻塞设备 Actor
 * - 统一的规则引擎入口
 */
@Slf4j
public class RuleEngineActor implements MiniTbActor {
    
    private final RuleEngineService ruleEngineService;
    private MiniTbActorContext ctx;
    
    public RuleEngineActor(RuleEngineService ruleEngineService) {
        this.ruleEngineService = ruleEngineService;
    }
    
    @Override
    public void init(MiniTbActorContext ctx) throws Exception {
        this.ctx = ctx;
        log.info("Rule Engine Actor 初始化");
    }
    
    @Override
    public boolean process(MiniTbActorMsg msg) {
        if (msg.getActorMsgType() == MiniTbActorMsg.ActorMsgType.TO_RULE_ENGINE_MSG) {
            onToRuleEngineMsg((ToRuleEngineMsg) msg);
            return true;
        }
        return false;
    }
    
    /**
     * 处理规则引擎消息
     */
    private void onToRuleEngineMsg(ToRuleEngineMsg msg) {
        log.debug("规则引擎收到消息: deviceId={}, type={}", 
                msg.getTbMsg().getOriginator(), 
                msg.getTbMsg().getType());
        
        try {
            // 通过规则链处理消息
            ruleEngineService.processMessage(msg.getTbMsg());
        } catch (Exception e) {
            log.error("规则引擎处理消息失败", e);
        }
    }
    
    @Override
    public void destroy() throws Exception {
        log.info("Rule Engine Actor 销毁");
    }
    
    @Override
    public String getActorId() {
        return "RuleEngineActor";
    }
}

