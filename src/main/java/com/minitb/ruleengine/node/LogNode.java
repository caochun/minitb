package com.minitb.ruleengine.node;

import com.minitb.common.msg.TbMsg;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志节点 - 记录消息信息
 */
@Slf4j
public class LogNode implements RuleNode {
    
    private final String prefix;
    
    public LogNode(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return "LogNode[" + prefix + "]";
    }

    @Override
    public TbMsg onMsg(TbMsg msg) {
        log.info("[{}] 消息详情: type={}, originator={}, data={}", 
                prefix,
                msg.getType(),
                msg.getOriginator(),
                msg.getData());
        
        return msg; // 日志节点不修改消息，直接传递
    }
}

