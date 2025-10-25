package com.minitb.domain.protocol;

/**
 * 协议配置接口
 * 不同的数据源协议实现这个接口
 */
public interface ProtocolConfig {
    
    /**
     * 获取协议类型
     */
    String getProtocolType();
}

