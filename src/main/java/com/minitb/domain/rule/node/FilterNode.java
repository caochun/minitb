package com.minitb.domain.rule.node;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minitb.domain.ts.DataType;
import com.minitb.domain.ts.TsKvEntry;
import com.minitb.domain.msg.TbMsg;
import com.minitb.domain.entity.RuleNodeId;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 过滤节点 - 根据条件过滤消息
 * 示例：只处理温度大于30的数据
 */
@Slf4j
public class FilterNode implements RuleNode {
    
    private final RuleNodeId id;
    private final String filterKey;
    private final double threshold;
    private RuleNode next;
    
    public FilterNode(String filterKey, double threshold) {
        this.id = RuleNodeId.random();
        this.filterKey = filterKey;
        this.threshold = threshold;
    }

    @Override
    public RuleNodeId getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return "FilterNode[" + filterKey + " > " + threshold + "]";
    }
    
    @Override
    public String getNodeType() {
        return "FILTER";
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }
    
    @Override
    public void init(RuleNodeConfig config, RuleNodeContext context) {
        // FilterNode不需要特殊初始化
    }

    @Override
    public void onMsg(TbMsg msg, RuleNodeContext context) {
        try {
            // 优先使用强类型数据
            if (msg.hasTsKvEntries()) {
                filterWithTypedData(msg, context);
            } else {
                // 降级为JSON解析
                filterWithJsonData(msg, context);
            }
        } catch (Exception e) {
            log.error("[{}] 处理消息失败", getName(), e);
        }
    }
    
    /**
     * 使用强类型数据过滤
     */
    private void filterWithTypedData(TbMsg msg, RuleNodeContext context) {
        Optional<TsKvEntry> entry = msg.getTsKvEntries().stream()
                .filter(e -> e.getKey().equals(filterKey))
                .findFirst();
        
        if (entry.isPresent()) {
            TsKvEntry tsKvEntry = entry.get();
            double value = 0;
            
            // 根据数据类型提取数值
            if (tsKvEntry.getDataType() == DataType.DOUBLE) {
                value = tsKvEntry.getDoubleValue().orElse(0.0);
            } else if (tsKvEntry.getDataType() == DataType.LONG) {
                value = tsKvEntry.getLongValue().orElse(0L).doubleValue();
            } else {
                log.warn("[{}] 字段 '{}' 不是数值类型: {}", getName(), filterKey, tsKvEntry.getDataType());
                // 非数值类型，默认放行
                if (next != null) {
                    next.onMsg(msg, context);
                }
                return;
            }
            
            if (value > threshold) {
                log.debug("[{}] 消息通过过滤（强类型）: {}={}", getName(), filterKey, value);
                if (next != null) {
                    next.onMsg(msg, context);
                }
            } else {
                log.trace("[{}] 消息被过滤（强类型）: {}={}", getName(), filterKey, value);
            }
        } else {
            log.warn("[{}] 数据中不包含字段: {}", getName(), filterKey);
            // 字段不存在时，默认放行
            if (next != null) {
                next.onMsg(msg, context);
            }
        }
    }
    
    /**
     * 使用JSON数据过滤（兼容模式）
     */
    private void filterWithJsonData(TbMsg msg, RuleNodeContext context) {
        JsonObject data = JsonParser.parseString(msg.getData()).getAsJsonObject();
        
        if (data.has(filterKey)) {
            double value = data.get(filterKey).getAsDouble();
            
            if (value > threshold) {
                log.debug("[{}] 消息通过过滤（兼容模式）: {}={}", getName(), filterKey, value);
                if (next != null) {
                    next.onMsg(msg, context);
                }
            } else {
                log.trace("[{}] 消息被过滤（兼容模式）: {}={}", getName(), filterKey, value);
            }
        } else {
            log.warn("[{}] 数据中不包含字段: {}", getName(), filterKey);
            // 字段不存在时，默认放行
            if (next != null) {
                next.onMsg(msg, context);
            }
        }
    }
}




