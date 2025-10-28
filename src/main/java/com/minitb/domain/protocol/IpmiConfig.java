package com.minitb.domain.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IPMI 协议配置
 * 
 * 用于 TelemetryDefinition 中，定义如何从 IPMI 传感器获取数据
 * 
 * 注意：
 * - 这里只定义 "获取哪个传感器"（指标级别）
 * - 连接信息（host, username, password）存储在 Device.configuration 中（设备级别）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IpmiConfig implements ProtocolConfig {
    
    /**
     * IPMI 传感器名称
     * 
     * 对应 ipmitool sensor list 输出的第一列
     * 例如:
     * - "CPU0_TEMP" : CPU 0 温度传感器
     * - "CPU1_TEMP" : CPU 1 温度传感器
     * - "CPU0_FAN" : CPU 0 风扇传感器
     * - "SYS_FAN2" : 系统风扇 2
     * - "P_12V" : 12V 电压传感器
     */
    private String sensorName;
    
    @Override
    @JsonIgnore  // 不序列化，避免重复
    public String getProtocolType() {
        return "IPMI";
    }
}

