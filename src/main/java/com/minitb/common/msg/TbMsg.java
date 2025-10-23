package com.minitb.common.msg;

import com.minitb.common.data.DeviceId;
import com.minitb.common.data.TenantId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ThingsBoard核心消息对象
 * 这是整个数据流的核心载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TbMsg {
    /**
     * 消息唯一ID
     */
    private UUID id;
    
    /**
     * 租户ID
     */
    private TenantId tenantId;
    
    /**
     * 消息类型
     */
    private TbMsgType type;
    
    /**
     * 消息发起者（设备ID）
     */
    private DeviceId originator;
    
    /**
     * 消息元数据（设备名称、类型、时间戳等）
     */
    @Builder.Default
    private Map<String, String> metaData = new HashMap<>();
    
    /**
     * 消息数据（JSON格式）
     */
    private String data;
    
    /**
     * 消息创建时间戳
     */
    private long timestamp;
    
    /**
     * 规则链ID（可选）
     */
    private String ruleChainId;
    
    /**
     * 队列名称
     */
    private String queueName;

    /**
     * 创建新消息的便捷方法
     */
    public static TbMsg newMsg(TbMsgType type, DeviceId originator, Map<String, String> metaData, String data) {
        return TbMsg.builder()
                .id(UUID.randomUUID())
                .type(type)
                .originator(originator)
                .metaData(metaData != null ? metaData : new HashMap<>())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .queueName("Main")
                .build();
    }

    /**
     * 添加元数据
     */
    public void addMetaData(String key, String value) {
        if (this.metaData == null) {
            this.metaData = new HashMap<>();
        }
        this.metaData.put(key, value);
    }

    /**
     * 复制消息
     */
    public TbMsg copy() {
        return TbMsg.builder()
                .id(UUID.randomUUID())
                .tenantId(this.tenantId)
                .type(this.type)
                .originator(this.originator)
                .metaData(new HashMap<>(this.metaData))
                .data(this.data)
                .timestamp(this.timestamp)
                .ruleChainId(this.ruleChainId)
                .queueName(this.queueName)
                .build();
    }

    @Override
    public String toString() {
        return "TbMsg{" +
                "id=" + id +
                ", type=" + type +
                ", originator=" + originator +
                ", data='" + data + '\'' +
                '}';
    }
}

