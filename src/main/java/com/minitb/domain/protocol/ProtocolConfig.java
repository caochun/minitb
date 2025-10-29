package com.minitb.domain.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 协议配置接口
 * 不同的数据源协议实现这个接口
 * 
 * 使用 Jackson 多态序列化支持
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "protocolType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PrometheusConfig.class, name = "PROMETHEUS"),
    @JsonSubTypes.Type(value = MqttConfig.class, name = "MQTT"),
    @JsonSubTypes.Type(value = HttpConfig.class, name = "HTTP"),
    @JsonSubTypes.Type(value = IpmiConfig.class, name = "IPMI")
})
public interface ProtocolConfig {
    
    /**
     * 获取协议类型
     */
    String getProtocolType();
}

