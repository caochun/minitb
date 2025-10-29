package com.minitb.ruleengine;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.actor.msg.ToRuleChainMsg;
import com.minitb.actor.rulechain.RuleChainActor;
import com.minitb.domain.id.RuleChainId;
import com.minitb.domain.messaging.Message;
import com.minitb.domain.rule.RuleChain;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 规则引擎服务 - 核心数据流的第三层
 * 职责：
 * 1. 管理规则链和RuleChainActor
 * 2. 路由消息到对应的规则链Actor
 * 3. 异步处理消息
 * 
 * 架构升级（V2.0）：
 * - 每个RuleChain有独立的RuleChainActor
 * - 消息通过Actor系统异步路由到RuleChainActor
 * - 规则链之间完全隔离，互不阻塞
 */
@Slf4j
public class RuleEngineService {
    
    // 规则链注册表
    private final Map<String, RuleChain> ruleChains = new ConcurrentHashMap<>();
    
    // 默认规则链（根规则链）
    private RuleChain rootRuleChain;
    private RuleChainId rootRuleChainId;
    
    // Actor系统引用（用于创建RuleChainActor）
    private MiniTbActorSystem actorSystem;
    
    // 异步处理线程池（保留用于向后兼容，但优先使用Actor系统）
    private final ExecutorService executorService;
    
    public RuleEngineService() {
        this.executorService = Executors.newFixedThreadPool(4);
        log.info("规则引擎服务初始化完成");
    }
    
    /**
     * 设置Actor系统
     * 必须在使用RuleChainActor之前调用
     */
    public void setActorSystem(MiniTbActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        log.info("规则引擎服务已设置Actor系统");
    }

    /**
     * 设置根规则链
     */
    public void setRootRuleChain(RuleChain ruleChain) {
        this.rootRuleChain = ruleChain;
        this.rootRuleChainId = ruleChain.getId();
        log.info("设置根规则链: {} (id: {})", ruleChain.getName(), rootRuleChainId);
        
        // 如果Actor系统已设置，创建根规则链Actor
        if (actorSystem != null) {
            createRuleChainActor(rootRuleChainId, ruleChain);
        }
    }

    /**
     * 注册规则链
     */
    public void registerRuleChain(String id, RuleChain ruleChain) {
        ruleChains.put(id, ruleChain);
        log.info("注册规则链: {} (id: {})", ruleChain.getName(), id);
        
        // 如果Actor系统已设置，创建RuleChainActor
        if (actorSystem != null) {
            RuleChainId ruleChainId = RuleChainId.fromString(id);
            createRuleChainActor(ruleChainId, ruleChain);
        }
    }
    
    /**
     * 创建RuleChainActor
     */
    private void createRuleChainActor(RuleChainId ruleChainId, RuleChain ruleChain) {
        RuleChainActor actor = new RuleChainActor(ruleChainId, ruleChain);
        actorSystem.createActor(RuleChainActor.actorIdFor(ruleChainId), actor);
        log.info("✓ 创建RuleChainActor: {} (id: {})", ruleChain.getName(), ruleChainId);
    }

    /**
     * 处理消息 - 规则引擎的核心入口
     * 这是从TransportService接收消息的地方
     */
    public void processMessage(Message msg) {
        log.info("规则引擎接收消息: type={}, originator={}", 
                msg.getType(), msg.getOriginator());
        
        // 异步处理消息（模拟真实ThingsBoard的异步处理）
        executorService.submit(() -> {
            try {
                processMessageInternal(msg);
            } catch (Exception e) {
                log.error("处理消息异常: {}", msg.getId(), e);
            }
        });
    }

    /**
     * 内部消息处理逻辑
     */
    private void processMessageInternal(Message msg) {
        // 1. 根据消息类型和规则链ID选择规则链
        RuleChain targetRuleChain = selectRuleChain(msg);
        
        if (targetRuleChain == null) {
            log.warn("未找到合适的规则链处理消息: {}", msg.getId());
            return;
        }
        
        // 2. 如果Actor系统已设置，通过RuleChainActor处理（推荐方式）
        if (actorSystem != null) {
            RuleChainId targetRuleChainId = getRuleChainId(msg, targetRuleChain);
            String actorId = RuleChainActor.actorIdFor(targetRuleChainId);
            
            log.debug("路由消息到RuleChainActor: {} [{}]", targetRuleChain.getName(), actorId);
            actorSystem.tell(actorId, new ToRuleChainMsg(msg));
            
        } else {
            // 3. 降级方案：如果Actor系统未设置，同步处理（向后兼容）
            log.debug("使用同步模式处理消息（Actor系统未设置）");
            targetRuleChain.process(msg);
        }
    }
    
    /**
     * 获取规则链ID
     */
    private RuleChainId getRuleChainId(Message msg, RuleChain ruleChain) {
        // 如果是根规则链
        if (ruleChain == rootRuleChain) {
            return rootRuleChainId;
        }
        
        // 从注册表中查找
        for (Map.Entry<String, RuleChain> entry : ruleChains.entrySet()) {
            if (entry.getValue() == ruleChain) {
                return RuleChainId.fromString(entry.getKey());
            }
        }
        
        // 默认返回ruleChain自己的ID
        return ruleChain.getId();
    }

    /**
     * 选择规则链
     * 
     * 路由优先级：
     * 1. 消息中指定的规则链ID（最高优先级）
     * 2. DeviceProfile中配置的默认规则链ID
     * 3. 租户的根规则链（Root Rule Chain）
     * 
     * 这与ThingsBoard的路由逻辑一致
     */
    private RuleChain selectRuleChain(Message msg) {
        // 优先级1: 消息中指定了规则链ID
        if (msg.getRuleChainId() != null && !msg.getRuleChainId().isEmpty()) {
            RuleChain chain = ruleChains.get(msg.getRuleChainId());
            if (chain != null) {
                log.debug("使用消息指定的规则链: {}", chain.getName());
                return chain;
            }
            log.warn("消息指定的规则链不存在: {}, 将使用根规则链", msg.getRuleChainId());
        }
        
        // 优先级2: 使用根规则链（目前MiniTB只有根规则链）
        // 注意: DeviceProfile中的defaultRuleChainId会在TransportService中
        //      设置到Message.ruleChainId，所以在这里已经体现在优先级1中
        if (rootRuleChain != null) {
            log.debug("使用根规则链: {}", rootRuleChain.getName());
            return rootRuleChain;
        }
        
        log.error("未找到任何可用的规则链处理消息: {}", msg.getId());
        return null;
    }

    /**
     * 获取规则链信息
     */
    public void printRuleChains() {
        log.info("=== 已注册的规则链 ===");
        if (rootRuleChain != null) {
            log.info("根规则链: {} (节点数: {})", 
                    rootRuleChain.getName(), rootRuleChain.getNodeCount());
        }
        ruleChains.forEach((id, chain) -> {
            log.info("规则链: {} (id: {}, 节点数: {})", 
                    chain.getName(), id, chain.getNodeCount());
        });
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        log.info("规则引擎服务关闭中...");
        executorService.shutdown();
    }
}




