package com.minitb.domain.device;

import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    private DeviceId id;
    private String name;
    private String type;
    private String accessToken;
    private long createdTime;
    
    /**
     * 设备配置文件ID（强类型）
     * 定义了设备应该有哪些遥测数据
     */
    private DeviceProfileId deviceProfileId;
    
    /**
     * Prometheus 标签映射（可选，仅 PROMETHEUS 类型设备使用）
     * 
     * 用于从 Prometheus 查询结果中识别此设备的数据
     * 格式: "labelKey=labelValue"
     * 
     * 示例:
     * - "instance=server-01:9100"
     * - "job=node-exporter"
     * - "node=kubernetes-node-1"
     * 
     * 工作原理:
     * 1. PrometheusDataPuller 执行 PromQL 查询
     * 2. 查询返回多个时间序列（每个都有标签）
     * 3. 根据此字段过滤出属于当前设备的数据
     * 4. 使用 accessToken 调用 processTelemetry() 关联设备
     */
    private String prometheusLabel;

    public Device(String name, String type, String accessToken) {
        this.id = DeviceId.random();
        this.name = name;
        this.type = type;
        this.accessToken = accessToken;
        this.createdTime = System.currentTimeMillis();
    }
    
    public Device(String name, String type, String accessToken, DeviceProfileId deviceProfileId) {
        this(name, type, accessToken);
        this.deviceProfileId = deviceProfileId;
    }
}
