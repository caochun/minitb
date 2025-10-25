package com.minitb.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资产实体
 * 
 * Asset代表物理或逻辑资产，例如:
 * - 建筑物
 * - 房间
 * - 机器
 * - 车辆
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Asset {
    /**
     * 资产ID
     */
    private AssetId id;
    
    /**
     * 资产名称
     */
    private String name;
    
    /**
     * 资产类型 (Building, Room, Vehicle等)
     */
    private String type;
    
    /**
     * 资产标签
     */
    private String label;
    
    /**
     * 创建时间
     */
    private long createdTime;
    
    /**
     * 构造方法 - 创建新资产
     */
    public Asset(String name, String type) {
        this.id = AssetId.random();
        this.name = name;
        this.type = type;
        this.createdTime = System.currentTimeMillis();
    }
}

