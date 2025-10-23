package com.minitb.ruleengine.node;

import com.minitb.common.msg.TbMsg;
import com.minitb.storage.TelemetryStorage;
import lombok.extern.slf4j.Slf4j;

/**
 * 保存遥测数据节点 - 将数据持久化到存储
 */
@Slf4j
public class SaveTelemetryNode implements RuleNode {
    
    private final TelemetryStorage storage;
    
    public SaveTelemetryNode(TelemetryStorage storage) {
        this.storage = storage;
    }

    @Override
    public String getName() {
        return "SaveTelemetryNode";
    }

    @Override
    public TbMsg onMsg(TbMsg msg) {
        try {
            // 保存遥测数据
            storage.save(
                msg.getOriginator(),
                msg.getTimestamp(),
                msg.getData()
            );
            
            log.info("[{}] 保存遥测数据成功: deviceId={}, ts={}", 
                    getName(), msg.getOriginator(), msg.getTimestamp());
            
            return msg; // 继续传递消息给下一个节点
            
        } catch (Exception e) {
            log.error("[{}] 保存遥测数据失败", getName(), e);
            return null;
        }
    }
}

