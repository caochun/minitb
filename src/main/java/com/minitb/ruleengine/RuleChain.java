package com.minitb.ruleengine;

import com.minitb.common.msg.TbMsg;
import com.minitb.ruleengine.node.RuleNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 规则链 - 规则节点的有序组合
 * 消息会按顺序流经每个节点
 */
@Slf4j
public class RuleChain {
    
    private final String id;
    private final String name;
    private final List<RuleNode> nodes;
    
    public RuleChain(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.nodes = new ArrayList<>();
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
            nodes.get(0).onMsg(msg);
            log.debug("规则链 [{}] 消息处理完成: {}", name, msg.getId());
        } catch (Exception e) {
            log.error("规则链 [{}] 处理消息异常", name, e);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getNodeCount() {
        return nodes.size();
    }
}




