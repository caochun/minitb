package com.minitb.ruleengine;

import com.minitb.common.msg.TbMsg;
import com.minitb.common.msg.TbMsgType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 规则引擎服务 - 核心数据流的第三层
 * 职责：
 * 1. 管理规则链
 * 2. 路由消息到对应的规则链
 * 3. 异步处理消息
 */
@Slf4j
public class RuleEngineService {
    
    // 规则链注册表
    private final Map<String, RuleChain> ruleChains = new ConcurrentHashMap<>();
    
    // 默认规则链（根规则链）
    private RuleChain rootRuleChain;
    
    // 异步处理线程池
    private final ExecutorService executorService;
    
    public RuleEngineService() {
        this.executorService = Executors.newFixedThreadPool(4);
        log.info("规则引擎服务初始化完成");
    }

    /**
     * 设置根规则链
     */
    public void setRootRuleChain(RuleChain ruleChain) {
        this.rootRuleChain = ruleChain;
        log.info("设置根规则链: {}", ruleChain.getName());
    }

    /**
     * 注册规则链
     */
    public void registerRuleChain(String id, RuleChain ruleChain) {
        ruleChains.put(id, ruleChain);
        log.info("注册规则链: {} (id: {})", ruleChain.getName(), id);
    }

    /**
     * 处理消息 - 规则引擎的核心入口
     * 这是从TransportService接收消息的地方
     */
    public void processMessage(TbMsg msg) {
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
    private void processMessageInternal(TbMsg msg) {
        // 1. 根据消息类型和规则链ID选择规则链
        RuleChain targetRuleChain = selectRuleChain(msg);
        
        if (targetRuleChain == null) {
            log.warn("未找到合适的规则链处理消息: {}", msg.getId());
            return;
        }
        
        // 2. 在实际ThingsBoard中，这里会：
        //    - 通过Actor模型处理
        //    - TenantActor -> RuleChainActor -> RuleNodeActor
        //    - 消息在Actor之间传递
        
        // 3. 执行规则链处理
        log.info("使用规则链 [{}] 处理消息", targetRuleChain.getName());
        targetRuleChain.process(msg);
    }

    /**
     * 选择规则链
     */
    private RuleChain selectRuleChain(TbMsg msg) {
        // 如果消息指定了规则链ID
        if (msg.getRuleChainId() != null) {
            RuleChain chain = ruleChains.get(msg.getRuleChainId());
            if (chain != null) {
                return chain;
            }
        }
        
        // 否则使用根规则链
        return rootRuleChain;
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




