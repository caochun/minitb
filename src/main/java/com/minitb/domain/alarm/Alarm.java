package com.minitb.domain.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.minitb.domain.id.AlarmId;
import com.minitb.domain.id.DeviceId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警实体（聚合根）
 * 
 * 设计原则：
 * - 不可变的核心字段（id, deviceId, type, startTs）
 * - 可变的状态字段（severity, status, endTs, ackTs, clearTs）
 * - 丰富的领域行为方法
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alarm {
    
    /**
     * 告警唯一标识
     */
    private AlarmId id;
    
    /**
     * 告警来源设备ID
     */
    private DeviceId originator;
    
    /**
     * 告警来源设备名称（冗余字段，便于显示）
     */
    private String originatorName;
    
    /**
     * 告警类型
     * 例如: "High Temperature", "High Power Usage", "Connection Lost"
     */
    private String type;
    
    /**
     * 告警严重程度
     */
    private AlarmSeverity severity;
    
    /**
     * 告警开始时间（毫秒时间戳）
     */
    private long startTs;
    
    /**
     * 告警结束时间（最后更新时间，毫秒时间戳）
     */
    private long endTs;
    
    /**
     * 告警确认时间（毫秒时间戳）
     */
    private Long ackTs;
    
    /**
     * 告警清除时间（毫秒时间戳）
     */
    private Long clearTs;
    
    /**
     * 告警详情（JSON 格式）
     * 可以包含触发告警的具体数据、阈值等信息
     */
    private JsonNode details;
    
    /**
     * 创建时间
     */
    private long createdTime;
    
    // ==================== 领域行为 ====================
    
    /**
     * 获取当前告警状态
     */
    public AlarmStatus getStatus() {
        return AlarmStatus.from(isCleared(), isAcknowledged());
    }
    
    /**
     * 是否已清除
     */
    public boolean isCleared() {
        return clearTs != null && clearTs > 0;
    }
    
    /**
     * 是否已确认
     */
    public boolean isAcknowledged() {
        return ackTs != null && ackTs > 0;
    }
    
    /**
     * 确认告警
     */
    public void acknowledge() {
        acknowledge(System.currentTimeMillis());
    }
    
    /**
     * 确认告警（指定时间）
     */
    public void acknowledge(long timestamp) {
        if (isCleared()) {
            throw new IllegalStateException("Cannot acknowledge a cleared alarm");
        }
        this.ackTs = timestamp;
        this.endTs = timestamp;
    }
    
    /**
     * 清除告警
     */
    public void clear() {
        clear(System.currentTimeMillis());
    }
    
    /**
     * 清除告警（指定时间）
     */
    public void clear(long timestamp) {
        this.clearTs = timestamp;
        this.endTs = timestamp;
    }
    
    /**
     * 更新严重程度
     */
    public void updateSeverity(AlarmSeverity newSeverity) {
        updateSeverity(newSeverity, System.currentTimeMillis());
    }
    
    /**
     * 更新严重程度（指定时间）
     */
    public void updateSeverity(AlarmSeverity newSeverity, long timestamp) {
        if (isCleared()) {
            throw new IllegalStateException("Cannot update severity of a cleared alarm");
        }
        this.severity = newSeverity;
        this.endTs = timestamp;
    }
    
    /**
     * 获取告警持续时间（毫秒）
     */
    public long getDuration() {
        if (clearTs != null) {
            return clearTs - startTs;
        }
        return System.currentTimeMillis() - startTs;
    }
    
    
    public long getDurationSeconds() {
        return getDuration() / 1000;
    }
    
    @Override
    public String toString() {
        return String.format("Alarm[id=%s, type=%s, severity=%s, status=%s, device=%s]",
            id, type, severity, getStatus(), originatorName);
    }
}
