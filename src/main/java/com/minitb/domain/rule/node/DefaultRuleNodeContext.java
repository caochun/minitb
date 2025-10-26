package com.minitb.domain.rule.node;

import com.minitb.domain.msg.TbMsg;
import com.minitb.domain.msg.TbMsgType;
import com.minitb.domain.entity.DeviceId;
import com.minitb.domain.entity.AssetId;
import com.minitb.domain.rule.RuleChainId;
import com.minitb.domain.entity.RuleNodeId;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 默认规则节点上下文实现
 * 
 * 提供规则节点的基本功能实现
 */
@Slf4j
public class DefaultRuleNodeContext implements RuleNodeContext {
    
    private final RuleNodeId nodeId;
    private final RuleChainId ruleChainId;
    private final String ruleChainName;
    private final boolean debugMode;
    
    // 状态管理
    private final Map<String, Object> nodeState = new HashMap<>();
    
    // 统计信息
    private final Map<String, Long> statistics = new HashMap<>();
    
    // 异步执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // 消息处理器
    private final Consumer<TbMsg> messageHandler;
    
    public DefaultRuleNodeContext(RuleNodeId nodeId, RuleChainId ruleChainId, 
                                 String ruleChainName, boolean debugMode,
                                 Consumer<TbMsg> messageHandler) {
        this.nodeId = nodeId;
        this.ruleChainId = ruleChainId;
        this.ruleChainName = ruleChainName;
        this.debugMode = debugMode;
        this.messageHandler = messageHandler;
        
        // 初始化统计信息
        statistics.put("processed", 0L);
        statistics.put("success", 0L);
        statistics.put("failure", 0L);
    }
    
    // ==================== 消息流控制 ====================
    
    @Override
    public void tellSuccess(TbMsg msg) {
        logDebug("消息处理成功，发送到下一个节点: {}", msg.getId());
        incrementSuccessCount();
        
        if (messageHandler != null) {
            messageHandler.accept(msg);
        }
    }
    
    @Override
    public void tellFailure(TbMsg msg, Throwable error) {
        logError("消息处理失败: " + msg.getId(), error);
        incrementFailureCount();
        
        // 可以在这里添加失败处理逻辑，比如发送到失败队列
        if (messageHandler != null) {
            messageHandler.accept(msg);
        }
    }
    
    @Override
    public void tellNext(TbMsg msg, String relationType) {
        logDebug("发送消息到关系类型: {} -> {}", relationType, msg.getId());
        
        // 根据关系类型决定消息流向
        switch (relationType.toUpperCase()) {
            case "SUCCESS":
            case "TRUE":
                tellSuccess(msg);
                break;
            case "FAILURE":
            case "FALSE":
                tellFailure(msg, new RuntimeException("节点返回失败状态"));
                break;
            default:
                logWarn("未知的关系类型: {}", relationType);
                tellSuccess(msg);
                break;
        }
    }
    
    @Override
    public void tellSelf(TbMsg msg, long delayMs) {
        logDebug("延迟 {}ms 后重新处理消息: {}", delayMs, msg.getId());
        
        scheduler.schedule(() -> {
            try {
                // 重新处理消息
                if (messageHandler != null) {
                    messageHandler.accept(msg);
                }
            } catch (Exception e) {
                logError("延迟处理消息失败: " + msg.getId(), e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void processAsync(TbMsg msg, Consumer<TbMsg> processor, 
                           Consumer<TbMsg> onSuccess, Consumer<Throwable> onFailure) {
        logDebug("异步处理消息: {}", msg.getId());
        
        CompletableFuture.runAsync(() -> {
            try {
                processor.accept(msg);
                if (onSuccess != null) {
                    onSuccess.accept(msg);
                }
            } catch (Exception e) {
                if (onFailure != null) {
                    onFailure.accept(e);
                } else {
                    logError("异步处理消息失败: " + msg.getId(), e);
                }
            }
        });
    }
    
    // ==================== 服务访问 ====================
    
    @Override
    public Object getDeviceService() {
        // TODO: 返回设备服务实例
        logWarn("设备服务未实现");
        return null;
    }
    
    @Override
    public Object getAssetService() {
        // TODO: 返回资产服务实例
        logWarn("资产服务未实现");
        return null;
    }
    
    @Override
    public Object getTelemetryService() {
        // TODO: 返回遥测服务实例
        logWarn("遥测服务未实现");
        return null;
    }
    
    @Override
    public Object getAlarmService() {
        // TODO: 返回告警服务实例
        logWarn("告警服务未实现");
        return null;
    }
    
    // ==================== 状态管理 ====================
    
    @Override
    public RuleNodeId getSelfId() {
        return nodeId;
    }
    
    @Override
    public RuleChainId getRuleChainId() {
        return ruleChainId;
    }
    
    @Override
    public String getRuleChainName() {
        return ruleChainName;
    }
    
    @Override
    public Map<String, Object> getNodeState() {
        return new HashMap<>(nodeState);
    }
    
    @Override
    public void setNodeState(String key, Object value) {
        nodeState.put(key, value);
        logDebug("设置节点状态: {} = {}", key, value);
    }
    
    @Override
    public void clearNodeState() {
        nodeState.clear();
        logDebug("清除节点状态");
    }
    
    // ==================== 消息创建 ====================
    
    @Override
    public TbMsg createMsg(String type, String data, Map<String, String> metadata) {
        return TbMsg.builder()
                .type(TbMsgType.valueOf(type))
                .data(data)
                .metaData(metadata)
                .build();
    }
    
    @Override
    public TbMsg createDeviceMsg(DeviceId deviceId, String type, String data) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("deviceId", deviceId.toString());
        metadata.put("entityType", "DEVICE");
        
        return createMsg(type, data, metadata);
    }
    
    @Override
    public TbMsg createAssetMsg(AssetId assetId, String type, String data) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("assetId", assetId.toString());
        metadata.put("entityType", "ASSET");
        
        return createMsg(type, data, metadata);
    }
    
    // ==================== 日志和调试 ====================
    
    @Override
    public void logDebug(String message, Object... args) {
        if (debugMode) {
            log.debug("[{}][{}] " + message, nodeId, ruleChainName, args);
        }
    }
    
    @Override
    public void logInfo(String message, Object... args) {
        log.info("[{}][{}] " + message, nodeId, ruleChainName, args);
    }
    
    @Override
    public void logWarn(String message, Object... args) {
        log.warn("[{}][{}] " + message, nodeId, ruleChainName, args);
    }
    
    @Override
    public void logError(String message, Throwable error) {
        log.error("[{}][{}] " + message, nodeId, ruleChainName, error);
    }
    
    @Override
    public boolean isDebugMode() {
        return debugMode;
    }
    
    // ==================== 统计信息 ====================
    
    @Override
    public void incrementProcessedCount() {
        statistics.merge("processed", 1L, Long::sum);
    }
    
    @Override
    public void incrementSuccessCount() {
        statistics.merge("success", 1L, Long::sum);
    }
    
    @Override
    public void incrementFailureCount() {
        statistics.merge("failure", 1L, Long::sum);
    }
    
    @Override
    public Map<String, Long> getStatistics() {
        return new HashMap<>(statistics);
    }
    
    // ==================== 资源清理 ====================
    
    /**
     * 关闭上下文，清理资源
     */
    public void shutdown() {
        logInfo("关闭规则节点上下文: {}", nodeId);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public String toString() {
        return String.format("DefaultRuleNodeContext{nodeId=%s, ruleChainId=%s, ruleChainName='%s', debug=%s, stats=%s}", 
                nodeId, ruleChainId, ruleChainName, debugMode, statistics);
    }
}
