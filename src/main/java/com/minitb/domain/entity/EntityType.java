package com.minitb.domain.entity;

/**
 * 实体类型枚举
 * 
 * 定义MiniTB中支持的实体类型
 */
public enum EntityType {
    
    /**
     * 设备
     */
    DEVICE("Device"),
    
    /**
     * 资产
     */
    ASSET("Asset"),
    
    /**
     * 设备配置
     */
    DEVICE_PROFILE("DeviceProfile"),
    
    /**
     * 规则链
     */
    RULE_CHAIN("RuleChain"),
    
    /**
     * 用户
     */
    USER("User"),
    
    /**
     * 租户
     */
    TENANT("Tenant"),
    
    /**
     * 客户
     */
    CUSTOMER("Customer"),
    
    /**
     * 仪表板
     */
    DASHBOARD("Dashboard"),
    
    /**
     * 告警
     */
    ALARM("Alarm");
    
    private final String type;
    
    EntityType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return type;
    }
}
