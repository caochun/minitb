package com.minitb.domain.rule;

import com.minitb.domain.messaging.Message;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.AssetId;
import com.minitb.domain.id.RuleChainId;
import com.minitb.domain.id.RuleNodeId;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 规则节点上下文接口
 * 
 * 职责：
 * 1. 消息流控制 - 控制消息在规则链中的流转
 * 2. 服务访问 - 提供对系统服务的访问
 * 3. 状态管理 - 管理节点的运行时状态
 * 4. 错误处理 - 统一的错误处理机制
 */
public interface RuleNodeContext {
    
    // ==================== 消息流控制 ====================
    
    /**
     * 成功处理消息，发送到下一个节点
     * @param msg 处理成功的消息
     */
    void tellSuccess(Message msg);
    
    /**
     * 处理失败，发送到失败节点
     * @param msg 处理失败的消息
     * @param error 错误信息
     */
    void tellFailure(Message msg, Throwable error);
    
    /**
     * 发送到指定关系类型的节点
     * @param msg 消息
     * @param relationType 关系类型 (SUCCESS, FAILURE, TRUE, FALSE等)
     */
    void tellNext(Message msg, String relationType);
    
    /**
     * 延迟发送给自己
     * @param msg 消息
     * @param delayMs 延迟时间（毫秒）
     */
    void tellSelf(Message msg, long delayMs);
    
    /**
     * 异步处理消息
     * @param msg 消息
     * @param processor 处理器
     * @param onSuccess 成功回调
     * @param onFailure 失败回调
     */
    void processAsync(Message msg, Consumer<Message> processor, 
                     Consumer<Message> onSuccess, Consumer<Throwable> onFailure);
    
    // ==================== 服务访问 ====================
    
    /**
     * 获取设备服务
     */
    Object getDeviceService();
    
    /**
     * 获取资产服务
     */
    Object getAssetService();
    
    /**
     * 获取遥测服务
     */
    Object getTelemetryService();
    
    /**
     * 获取告警服务
     */
    Object getAlarmService();
    
    // ==================== 状态管理 ====================
    
    /**
     * 获取当前节点ID
     */
    RuleNodeId getSelfId();
    
    /**
     * 获取规则链ID
     */
    RuleChainId getRuleChainId();
    
    /**
     * 获取规则链名称
     */
    String getRuleChainName();
    
    /**
     * 获取节点状态
     */
    Map<String, Object> getNodeState();
    
    /**
     * 设置节点状态
     */
    void setNodeState(String key, Object value);
    
    /**
     * 清除节点状态
     */
    void clearNodeState();
    
    // ==================== 消息创建 ====================
    
    /**
     * 创建新的消息
     */
    Message createMsg(String type, String data, Map<String, String> metadata);
    
    /**
     * 创建设备消息
     */
    Message createDeviceMsg(DeviceId deviceId, String type, String data);
    
    /**
     * 创建资产消息
     */
    Message createAssetMsg(AssetId assetId, String type, String data);
    
    // ==================== 日志和调试 ====================
    
    /**
     * 记录调试日志
     */
    void logDebug(String message, Object... args);
    
    /**
     * 记录信息日志
     */
    void logInfo(String message, Object... args);
    
    /**
     * 记录警告日志
     */
    void logWarn(String message, Object... args);
    
    /**
     * 记录错误日志
     */
    void logError(String message, Throwable error);
    
    /**
     * 是否启用调试模式
     */
    boolean isDebugMode();
    
    // ==================== 统计信息 ====================
    
    /**
     * 增加处理计数
     */
    void incrementProcessedCount();
    
    /**
     * 增加成功计数
     */
    void incrementSuccessCount();
    
    /**
     * 增加失败计数
     */
    void incrementFailureCount();
    
    /**
     * 获取处理统计
     */
    Map<String, Long> getStatistics();
}
