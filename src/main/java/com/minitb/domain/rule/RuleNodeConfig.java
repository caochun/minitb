package com.minitb.domain.rule;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则节点配置类
 * 
 * 职责：
 * 1. 存储节点的配置参数
 * 2. 提供类型安全的配置访问
 * 3. 提供配置的默认值
 * 4. 验证配置的有效性
 */
@Data
@Slf4j
public class RuleNodeConfig {
    
    /**
     * 节点类型
     */
    private String nodeType;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 配置参数映射
     */
    private Map<String, Object> configuration;
    
    /**
     * 是否启用调试模式
     */
    private boolean debugMode;
    
    /**
     * 队列名称（可选）
     */
    private String queueName;
    
    /**
     * 版本号
     */
    private int version;
    
    public RuleNodeConfig() {
        this.configuration = new HashMap<>();
        this.debugMode = false;
        this.version = 1;
    }
    
    public RuleNodeConfig(String nodeType, String nodeName) {
        this();
        this.nodeType = nodeType;
        this.nodeName = nodeName;
    }
    
    // ==================== 配置访问方法 ====================
    
    /**
     * 获取字符串配置
     */
    public String getStringConfig(String key) {
        return getStringConfig(key, null);
    }
    
    public String getStringConfig(String key, String defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
    
    /**
     * 获取数值配置
     */
    public Double getDoubleConfig(String key) {
        return getDoubleConfig(key, null);
    }
    
    public Double getDoubleConfig(String key, Double defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取整数配置
     */
    public Integer getIntConfig(String key) {
        return getIntConfig(key, null);
    }
    
    public Integer getIntConfig(String key, Integer defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取布尔配置
     */
    public Boolean getBooleanConfig(String key) {
        return getBooleanConfig(key, null);
    }
    
    public Boolean getBooleanConfig(String key, Boolean defaultValue) {
        Object value = configuration.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 设置配置
     */
    public void setConfig(String key, Object value) {
        configuration.put(key, value);
    }
    
    /**
     * 检查配置是否存在
     */
    public boolean hasConfig(String key) {
        return configuration.containsKey(key);
    }
    
    // ==================== 验证方法 ====================
    
    /**
     * 验证配置的有效性
     */
    public boolean validate() {
        if (nodeType == null || nodeType.trim().isEmpty()) {
            log.error("节点类型不能为空");
            return false;
        }
        
        if (nodeName == null || nodeName.trim().isEmpty()) {
            log.error("节点名称不能为空");
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证必需的配置项
     */
    public boolean validateRequired(String... requiredKeys) {
        for (String key : requiredKeys) {
            if (!hasConfig(key)) {
                log.error("缺少必需的配置项: {}", key);
                return false;
            }
        }
        return true;
    }
    
    // ==================== 工厂方法 ====================
    
    /**
     * 创建过滤节点配置
     */
    public static RuleNodeConfig createFilterConfig(String filterKey, double threshold) {
        RuleNodeConfig config = new RuleNodeConfig("FILTER", "FilterNode");
        config.setConfig("filterKey", filterKey);
        config.setConfig("threshold", threshold);
        return config;
    }
    
    /**
     * 创建日志节点配置
     */
    public static RuleNodeConfig createLogConfig(String logLevel, String format) {
        RuleNodeConfig config = new RuleNodeConfig("LOG", "LogNode");
        config.setConfig("logLevel", logLevel);
        config.setConfig("format", format);
        return config;
    }
    
    /**
     * 创建转换节点配置
     */
    public static RuleNodeConfig createTransformConfig(String script) {
        RuleNodeConfig config = new RuleNodeConfig("TRANSFORM", "TransformNode");
        config.setConfig("script", script);
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("RuleNodeConfig{type='%s', name='%s', config=%s, debug=%s}", 
                nodeType, nodeName, configuration, debugMode);
    }
}
