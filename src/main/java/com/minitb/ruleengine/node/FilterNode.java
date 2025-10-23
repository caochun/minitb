package com.minitb.ruleengine.node;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minitb.common.msg.TbMsg;
import lombok.extern.slf4j.Slf4j;

/**
 * 过滤节点 - 根据条件过滤消息
 * 示例：只处理温度大于30的数据
 */
@Slf4j
public class FilterNode implements RuleNode {
    
    private final String filterKey;
    private final double threshold;
    
    public FilterNode(String filterKey, double threshold) {
        this.filterKey = filterKey;
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "FilterNode[" + filterKey + " > " + threshold + "]";
    }

    @Override
    public TbMsg onMsg(TbMsg msg) {
        try {
            JsonObject data = JsonParser.parseString(msg.getData()).getAsJsonObject();
            
            if (data.has(filterKey)) {
                double value = data.get(filterKey).getAsDouble();
                
                if (value > threshold) {
                    log.info("[{}] 消息通过过滤: {}={}", getName(), filterKey, value);
                    return msg;
                } else {
                    log.debug("[{}] 消息被过滤: {}={}", getName(), filterKey, value);
                    return null; // 返回null表示消息被过滤
                }
            } else {
                log.warn("[{}] 数据中不包含字段: {}", getName(), filterKey);
                return msg; // 字段不存在时，默认放行
            }
        } catch (Exception e) {
            log.error("[{}] 处理消息失败", getName(), e);
            return null;
        }
    }
}

