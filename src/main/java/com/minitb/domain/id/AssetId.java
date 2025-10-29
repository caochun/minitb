package com.minitb.domain.id;

import java.util.UUID;

/**
 * 资产ID
 */
public class AssetId extends EntityId {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造器：使用指定UUID
     */
    public AssetId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static AssetId fromUUID(UUID uuid) {
        return new AssetId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static AssetId fromString(String assetId) {
        return new AssetId(UUID.fromString(assetId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static AssetId random() {
        return new AssetId(UUID.randomUUID());
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.ASSET;
    }
}
