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
    private RuleNode next;
    
    public FilterNode(String filterKey, double threshold) {
        this.filterKey = filterKey;
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "FilterNode[" + filterKey + " > " + threshold + "]";
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }

    @Override
    public void onMsg(TbMsg msg) {
        try {
            JsonObject data = JsonParser.parseString(msg.getData()).getAsJsonObject();
            
            if (data.has(filterKey)) {
                double value = data.get(filterKey).getAsDouble();
                
                if (value > threshold) {
                    log.info("[{}] 消息通过过滤: {}={}", getName(), filterKey, value);
                    if (next != null) {
                        next.onMsg(msg);
                    }
                } else {
                    log.debug("[{}] 消息被过滤: {}={}", getName(), filterKey, value);
                    // 消息被过滤，不传递给下一个节点
                }
            } else {
                log.warn("[{}] 数据中不包含字段: {}", getName(), filterKey);
                // 字段不存在时，默认放行
                if (next != null) {
                    next.onMsg(msg);
                }
            }
        } catch (Exception e) {
            log.error("[{}] 处理消息失败", getName(), e);
        }
    }
}




