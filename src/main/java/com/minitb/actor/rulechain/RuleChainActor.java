package com.minitb.actor.rulechain;

import com.minitb.actor.MiniTbActor;
import com.minitb.actor.MiniTbActorContext;
import com.minitb.actor.MiniTbActorMsg;
import com.minitb.actor.msg.ToRuleChainMsg;
import com.minitb.domain.id.RuleChainId;
import com.minitb.domain.messaging.Message;
import com.minitb.domain.rule.RuleChain;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则链Actor
 * 
 * 职责：
 * 1. 管理单个规则链的生命周期
 * 2. 处理路由到该规则链的消息
 * 3. 提供独立的消息队列和处理能力
 * 
 * 优势：
 * - 每个规则链独立处理，互不阻塞
 * - 可以为不同规则链分配不同优先级
 * - 便于监控和管理每个规则链的性能
 * - 资源隔离更好
 * 
 * 对比ThingsBoard：
 * - ThingsBoard每个RuleNode也是独立Actor（更细粒度）
 * - MiniTB只在RuleChain级别使用Actor（简化实现）
 */
@Slf4j
public class RuleChainActor implements MiniTbActor {
    
    private static final String ACTOR_ID_PREFIX = "RuleChain:";
    
    private final RuleChainId ruleChainId;
    private final RuleChain ruleChain;
    private MiniTbActorContext ctx;
    
    // 统计信息
    private long processedMessageCount = 0;
    private long failedMessageCount = 0;
    private long lastProcessTime = 0;
    
    public RuleChainActor(RuleChainId ruleChainId, RuleChain ruleChain) {
        this.ruleChainId = ruleChainId;
        this.ruleChain = ruleChain;
    }
    
    @Override
    public void init(MiniTbActorContext ctx) throws Exception {
        this.ctx = ctx;
        log.info("[{}] RuleChainActor 初始化: {} (节点数: {})", 
            ruleChainId, ruleChain.getName(), ruleChain.getNodeCount());
    }
    
    @Override
    public boolean process(MiniTbActorMsg msg) {
        if (msg.getActorMsgType() == MiniTbActorMsg.ActorMsgType.TO_RULE_CHAIN_MSG) {
            onToRuleChainMsg((ToRuleChainMsg) msg);
            return true;
        }
        return false;
    }
    
    /**
     * 处理规则链消息
     */
    private void onToRuleChainMsg(ToRuleChainMsg msg) {
        Message message = msg.getMessage();
        
        log.debug("[{}] 收到消息: originator={}, type={}", 
            ruleChain.getName(), 
            message.getOriginator(), 
            message.getType());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 通过规则链处理消息
            ruleChain.process(message);
            
            processedMessageCount++;
            lastProcessTime = System.currentTimeMillis() - startTime;
            
            log.debug("[{}] 消息处理完成，耗时: {}ms", ruleChain.getName(), lastProcessTime);
            
        } catch (Exception e) {
            failedMessageCount++;
            log.error("[{}] 消息处理失败: {}", ruleChain.getName(), message.getId(), e);
        }
    }
    
    @Override
    public void destroy() throws Exception {
        log.info("[{}] RuleChainActor 销毁，统计信息: 处理消息={}, 失败消息={}", 
            ruleChain.getName(), processedMessageCount, failedMessageCount);
    }
    
    @Override
    public String getActorId() {
        return ACTOR_ID_PREFIX + ruleChainId.toString();
    }
    
    /**
     * 静态方法：根据RuleChainId生成ActorId
     */
    public static String actorIdFor(RuleChainId ruleChainId) {
        return ACTOR_ID_PREFIX + ruleChainId.toString();
    }
    
    /**
     * 获取规则链ID
     */
    public RuleChainId getRuleChainId() {
        return ruleChainId;
    }
    
    /**
     * 获取规则链名称
     */
    public String getRuleChainName() {
        return ruleChain.getName();
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("[%s] 已处理: %d, 失败: %d, 上次耗时: %dms",
            ruleChain.getName(), processedMessageCount, failedMessageCount, lastProcessTime);
    }
}

