package com.minitb.domain.rule.node;

import com.minitb.domain.msg.TbMsg;
import com.minitb.domain.entity.RuleNodeId;
import com.minitb.performance.PerformanceMetrics;
import com.minitb.storage.TelemetryStorage;
import lombok.extern.slf4j.Slf4j;

/**
 * 性能感知的保存遥测数据节点
 * 在保存数据的同时记录性能指标
 */
@Slf4j
public class PerformanceAwareSaveTelemetryNode implements RuleNode {
    
    private final TelemetryStorage storage;
    private final PerformanceMetrics metrics;
    private final RuleNodeId id;
    private RuleNode next;
    
    public PerformanceAwareSaveTelemetryNode(TelemetryStorage storage, PerformanceMetrics metrics) {
        this.storage = storage;
        this.metrics = metrics;
        this.id = RuleNodeId.random();
    }

    @Override
    public RuleNodeId getId() {
        return id;
    }

    @Override
    public String getName() {
        return "PerformanceAwareSaveTelemetryNode";
    }
    
    @Override
    public String getNodeType() {
        return "ACTION";
    }
    
    @Override
    public void setNext(RuleNode next) {
        this.next = next;
    }
    
    @Override
    public void init(RuleNodeConfig config, RuleNodeContext context) {
        // 不需要初始化
    }

    @Override
    public void onMsg(TbMsg msg, RuleNodeContext context) {
        long startTime = System.nanoTime();
        
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
            
            // 计算延迟并记录指标
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            // 从消息中提取发送时间戳（如果存在）
            long sendTime = extractSendTime(msg);
            if (sendTime > 0) {
                // 计算端到端延迟（发送到保存）
                long endToEndLatency = endTime - sendTime;
                metrics.recordMessageProcessed(msg.getOriginator().toString(), endToEndLatency);
            } else {
                // 只记录处理延迟
                metrics.recordMessageProcessed(msg.getOriginator().toString(), latencyNanos);
            }
            
            // 传递给下一个节点
            if (next != null) {
                next.onMsg(msg, context);
            }
            
        } catch (Exception e) {
            log.error("[{}] 保存遥测数据失败", getName(), e);
            metrics.recordMessageFailed();
        }
    }
    
    /**
     * 从消息中提取发送时间戳（纳秒）
     * 用于计算端到端延迟
     */
    private long extractSendTime(TbMsg msg) {
        try {
            // 从消息数据中提取 sendTimeNanos 字段
            String data = msg.getData();
            if (data != null && data.contains("\"sendTimeNanos\"")) {
                // 简单的JSON解析提取 sendTimeNanos
                int index = data.indexOf("\"sendTimeNanos\":");
                if (index != -1) {
                    int startIndex = index + 16; // "sendTimeNanos": 的长度
                    int endIndex = data.indexOf(",", startIndex);
                    if (endIndex == -1) {
                        endIndex = data.indexOf("}", startIndex);
                    }
                    if (endIndex > startIndex) {
                        String timeStr = data.substring(startIndex, endIndex).trim();
                        return Long.parseLong(timeStr);
                    }
                }
            }
            
            // 如果没有 sendTimeNanos 字段，返回 0
            return 0;
            
        } catch (Exception e) {
            log.debug("无法提取发送时间戳: {}", e.getMessage());
            return 0;
        }
    }
}
