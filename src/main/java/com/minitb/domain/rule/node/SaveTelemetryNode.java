package com.minitb.domain.rule.node;

import com.minitb.domain.msg.TbMsg;
import com.minitb.storage.TelemetryStorage;
import lombok.extern.slf4j.Slf4j;

/**
 * 保存遥测数据节点 - 将数据持久化到存储
 */
@Slf4j
public class SaveTelemetryNode implements RuleNode {
    
    private final TelemetryStorage storage;
    private RuleNode next;
    
    public SaveTelemetryNode(TelemetryStorage storage) {
        this.storage = storage;
    }

    @Override
    public String getName() {
        return "SaveTelemetryNode";
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }

    @Override
    public void onMsg(TbMsg msg) {
        try {
            // 优先使用强类型数据
            if (msg.hasTsKvEntries()) {
                storage.save(msg.getOriginator(), msg.getTsKvEntries());
                log.debug("[{}] 保存遥测数据成功（强类型）: deviceId={}, 数据点数={}", 
                        getName(), msg.getOriginator(), msg.getTsKvEntries().size());
            } else {
                // 降级为兼容模式
                storage.save(msg.getOriginator(), msg.getTimestamp(), msg.getData());
                log.debug("[{}] 保存遥测数据成功（兼容模式）: deviceId={}, ts={}", 
                        getName(), msg.getOriginator(), msg.getTimestamp());
            }
            
            // 传递给下一个节点
            if (next != null) {
                next.onMsg(msg);
            }
            
        } catch (Exception e) {
            log.error("[{}] 保存遥测数据失败", getName(), e);
        }
    }
}




