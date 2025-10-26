package com.minitb.domain.rule;

import com.minitb.domain.id.RuleChainId;
import com.minitb.domain.id.RuleNodeId;
import com.minitb.domain.messaging.TbMsg;
import com.minitb.domain.rule.node.RuleNode;
import com.minitb.domain.rule.node.RuleNodeContext;
import com.minitb.domain.rule.node.DefaultRuleNodeContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则链 - 规则节点的有序组合
 * 消息会按顺序流经每个节点
 */
@Slf4j
public class RuleChain {
    
    private final RuleChainId id;
    private final String name;
    private final List<RuleNode> nodes;
    private final long createdTime;
    
    public RuleChain(String name) {
        this.id = RuleChainId.random();
        this.name = name;
        this.nodes = new ArrayList<>();
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * 添加规则节点
     */
    public RuleChain addNode(RuleNode node) {
        // 建立责任链：将新节点链接到最后一个节点
        if (!nodes.isEmpty()) {
            RuleNode lastNode = nodes.get(nodes.size() - 1);
            lastNode.setNext(node);
        }
        nodes.add(node);
        log.info("规则链 [{}] 添加节点: {}", name, node.getName());
        return this;
    }

    /**
     * 处理消息 - 从第一个节点开始，通过责任链传递
     */
    public void process(TbMsg msg) {
        log.debug("规则链 [{}] 开始处理消息: {}", name, msg.getId());
        
        if (nodes.isEmpty()) {
            log.warn("规则链 [{}] 没有节点", name);
            return;
        }
        
        try {
            // 从第一个节点开始处理，后续节点通过责任链自动调用
            // 创建默认的RuleNodeContext
            RuleNodeContext context = new DefaultRuleNodeContext(
                null, // nodeId - 简化处理
                this.id, // ruleChainId
                this.name, // ruleChainName
                false, // debugMode
                null // messageHandler - 简化处理
            );
            nodes.get(0).onMsg(msg, context);
            log.debug("规则链 [{}] 消息处理完成: {}", name, msg.getId());
        } catch (Exception e) {
            log.error("规则链 [{}] 处理消息异常", name, e);
        }
    }

    public RuleChainId getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }

    public int getNodeCount() {
        return nodes.size();
    }
    
    /**
     * 获取所有规则节点
     */
    public List<RuleNode> getRuleNodes() {
        return new ArrayList<>(nodes);
    }
    
    /**
     * 判断是否为根规则链
     * 根规则链通常有特殊的名称或标识
     */
    public boolean isRoot() {
        return "Root Rule Chain".equals(name) || "root".equalsIgnoreCase(name);
    }
    
    /**
     * 设置是否为根规则链
     * 注意：由于name是final字段，此方法主要用于标记，实际业务逻辑可能需要重新创建RuleChain
     */
    public void setRoot(boolean isRoot) {
        // 注意：由于name是final字段，无法直接修改
        // 实际业务逻辑中，如果需要修改name，应该重新创建RuleChain对象
        log.info("设置规则链 [{}] 为根规则链: {}", name, isRoot);
    }
    
    /**
     * 检查是否包含指定的规则节点
     */
    public boolean hasRuleNode(RuleNodeId nodeId) {
        return nodes.stream()
                .anyMatch(node -> node.getId().equals(nodeId));
    }
    
    /**
     * 添加规则节点到规则链
     */
    public void addRuleNode(RuleNode ruleNode) {
        if (hasRuleNode(ruleNode.getId())) {
            throw new IllegalArgumentException("Rule node already exists: " + ruleNode.getId());
        }
        
        // 建立责任链：将新节点链接到最后一个节点
        if (!nodes.isEmpty()) {
            RuleNode lastNode = nodes.get(nodes.size() - 1);
            lastNode.setNext(ruleNode);
        }
        nodes.add(ruleNode);
        log.info("规则链 [{}] 添加节点: {}", name, ruleNode.getName());
    }
    
    /**
     * 移除规则节点
     */
    public void removeRuleNode(RuleNodeId nodeId) {
        boolean removed = nodes.removeIf(node -> node.getId().equals(nodeId));
        if (removed) {
            log.info("规则链 [{}] 移除节点: {}", name, nodeId);
        }
    }
    
    /**
     * 更新规则节点
     */
    public void updateRuleNode(RuleNode ruleNode) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId().equals(ruleNode.getId())) {
                nodes.set(i, ruleNode);
                log.info("规则链 [{}] 更新节点: {}", name, ruleNode.getName());
                return;
            }
        }
        throw new IllegalArgumentException("Rule node not found: " + ruleNode.getId());
    }
    
    /**
     * 处理消息
     */
    public void processMessage(Object msg) {
        if (nodes.isEmpty()) {
            log.warn("规则链 [{}] 没有节点，无法处理消息", name);
            return;
        }
        
        // 创建默认上下文
        DefaultRuleNodeContext context = new DefaultRuleNodeContext(
            null, this.id, this.name, false, null
        );
        
        // 开始处理消息
        try {
            nodes.get(0).onMsg((TbMsg) msg, context);
        } catch (Exception e) {
            log.error("规则链 [{}] 处理消息失败", name, e);
        }
    }
}




