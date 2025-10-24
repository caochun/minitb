package com.minitb.ruleengine.node;

import com.minitb.common.msg.TbMsg;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志节点 - 记录消息信息
 */
@Slf4j
public class LogNode implements RuleNode {
    
    private final String prefix;
    private RuleNode next;
    
    public LogNode(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return "LogNode[" + prefix + "]";
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }

    @Override
    public void onMsg(TbMsg msg) {
        log.info("[{}] 消息详情: type={}, originator={}, data={}", 
                prefix,
                msg.getType(),
                msg.getOriginator(),
                msg.getData());
        
        // 传递给下一个节点
        if (next != null) {
            next.onMsg(msg);
        }
    }
}




