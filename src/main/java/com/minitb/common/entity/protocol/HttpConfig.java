package com.minitb.common.entity.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP 协议配置
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HttpConfig implements ProtocolConfig {
    
    /**
     * JSON Path 表达式
     * 用于从 HTTP 响应中提取数据
     * 例如: "$.data.temperature"
     */
    private String jsonPath;
    
    /**
     * HTTP 方法
     */
    @Builder.Default
    private String method = "POST";
    
    /**
     * 请求路径
     */
    private String path;
    
    @Override
    public String getProtocolType() {
        return "HTTP";
    }
}

