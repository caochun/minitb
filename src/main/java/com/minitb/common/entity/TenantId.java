package com.minitb.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 租户ID
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantId {
    private UUID id;

    public static TenantId fromUUID(UUID uuid) {
        return new TenantId(uuid);
    }

    public static TenantId systemTenantId() {
        return new TenantId(new UUID(0, 0));
    }
    
    public static TenantId random() {
        return new TenantId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
