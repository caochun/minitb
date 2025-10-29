package com.minitb.domain.device;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 设备配置接口
 * 
 * 不同类型的数据源需要不同的连接配置信息。
 * 使用策略模式，每种数据源类型有自己的配置实现。
 * 
 * 设计原则：
 * - Device 保持简洁，不为所有类型添加字段
 * - 配置按需实现，避免大量 null 字段
 * - 类型安全，编译时检查
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PrometheusDeviceConfiguration.class, name = "PROMETHEUS"),
    @JsonSubTypes.Type(value = IpmiDeviceConfiguration.class, name = "IPMI")
})
public interface DeviceConfiguration {
    /**
     * 获取配置类型
     * 用于序列化/反序列化时的类型识别
     */
    String getConfigurationType();
}

