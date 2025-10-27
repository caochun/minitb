package com.minitb.domain.alarm;

import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.EntityId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警实体（简化版）
 * 
 * 简化说明：
 * - 移除了多租户相关（TenantId, CustomerId）
 * - 移除了传播机制（propagate, propagateToOwner 等）
 * - 移除了分配功能（assigneeId, assignTs）
 * - 保留核心告警功能
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alarm {
    
    /**
     * 告警ID
     */
    private AlarmId id;
    
    /**
     * 告警类型
     * 例如: "High Temperature", "Low Battery", "Connection Lost"
     */
    private String type;
    
    /**
     * 告警来源实体
     * 可以是 Device, Asset 等
     */
    private EntityId originator;
    
    /**
     * 严重级别
     */
    private AlarmSeverity severity;
    
    /**
     * 是否已确认
     */
    private boolean acknowledged;
    
    /**
     * 是否已清除
     */
    private boolean cleared;
    
    /**
     * 告警开始时间（毫秒时间戳）
     */
    private long startTs;
    
    /**
     * 告警结束时间（最后更新时间）
     */
    private long endTs;
    
    /**
     * 确认时间
     */
    private long ackTs;
    
    /**
     * 清除时间
     */
    private long clearTs;
    
    /**
     * 告警详细信息（可选，JSON格式）
     */
    private String details;
    
    /**
     * 创建时间
     */
    private long createdTime;
    
    /**
     * 获取告警状态（计算字段）
     */
    public AlarmStatus getStatus() {
        return AlarmStatus.from(cleared, acknowledged);
    }
    
    /**
     * 确认告警
     */
    public void acknowledge() {
        this.acknowledged = true;
        this.ackTs = System.currentTimeMillis();
        this.endTs = this.ackTs;
    }
    
    /**
     * 清除告警
     */
    public void clear() {
        this.cleared = true;
        this.clearTs = System.currentTimeMillis();
        this.endTs = this.clearTs;
    }
    
    /**
     * 判断告警是否活跃
     */
    public boolean isActive() {
        return !cleared;
    }
}


