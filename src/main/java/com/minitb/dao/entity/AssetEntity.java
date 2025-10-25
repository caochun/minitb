package com.minitb.dao.entity;

import com.minitb.common.entity.Asset;
import com.minitb.common.entity.AssetId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资产实体类 - 数据库映射
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetEntity {
    
    private String id;
    private String name;
    private String type;
    private String label;
    private Long createdTime;
    
    /**
     * Domain → Entity
     */
    public static AssetEntity fromDomain(Asset asset) {
        if (asset == null) {
            return null;
        }
        
        return AssetEntity.builder()
                .id(asset.getId() != null ? asset.getId().toString() : null)
                .name(asset.getName())
                .type(asset.getType())
                .label(asset.getLabel())
                .createdTime(asset.getCreatedTime())
                .build();
    }
    
    /**
     * Entity → Domain
     */
    public Asset toDomain() {
        return Asset.builder()
                .id(id != null ? AssetId.fromString(id) : null)
                .name(name)
                .type(type)
                .label(label)
                .createdTime(createdTime != null ? createdTime : 0L)
                .build();
    }
}

