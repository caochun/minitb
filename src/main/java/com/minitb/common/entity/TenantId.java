package com.minitb.common.entity;

import java.util.UUID;

/**
 * 租户ID
 * 
 * 改进点：
 * 1. 不可变ID（继承自UUIDBased）
 * 2. 缓存hashCode
 * 3. 工厂方法
 * 4. 类型安全
 */
public class TenantId extends UUIDBased {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 系统租户ID（固定UUID）
     */
    private static final UUID SYSTEM_TENANT_UUID = new UUID(0, 0);
    
    /**
     * 构造器：使用指定UUID
     */
    public TenantId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static TenantId fromUUID(UUID uuid) {
        return new TenantId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static TenantId fromString(String tenantId) {
        return new TenantId(UUID.fromString(tenantId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static TenantId random() {
        return new TenantId(UUID.randomUUID());
    }
    
    /**
     * 工厂方法：获取系统租户ID
     */
    public static TenantId systemTenantId() {
        return new TenantId(SYSTEM_TENANT_UUID);
    }
    
    /**
     * 判断是否为系统租户
     */
    public boolean isSystemTenant() {
        return SYSTEM_TENANT_UUID.equals(getId());
    }
}
