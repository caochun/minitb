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
        nodes.add(node);
        log.info("规则链 [{}] 添加节点: {}", name, node.getName());
        return this;
    }

    /**
     * 处理消息 - 消息依次流经所有节点
     */
    public void process(TbMsg msg) {
        log.info("规则链 [{}] 开始处理消息: {}", name, msg.getId());
        
        TbMsg currentMsg = msg;
        
        for (int i = 0; i < nodes.size(); i++) {
            if (currentMsg == null) {
                log.info("规则链 [{}] 消息在节点 {} 被过滤", name, i);
                break;
            }
            
            RuleNode node = nodes.get(i);
            log.debug("规则链 [{}] 节点 {} 处理消息: {}", name, i, node.getName());
            
            try {
                currentMsg = node.onMsg(currentMsg);
            } catch (Exception e) {
                log.error("规则链 [{}] 节点 {} 处理失败", name, i, e);
                break;
            }
        }
        
        if (currentMsg != null) {
            log.info("规则链 [{}] 消息处理完成: {}", name, msg.getId());
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

