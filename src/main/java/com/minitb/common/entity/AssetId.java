package com.minitb.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 资产ID
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetId {
    private UUID id;
    
    public AssetId(String uuid) {
        this.id = UUID.fromString(uuid);
    }
    
    public static AssetId random() {
        return new AssetId(UUID.randomUUID());
    }
}

