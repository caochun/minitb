package com.minitb.relation;

/**
 * 实体关系搜索方向
 */
public enum EntitySearchDirection {
    /**
     * 从实体出发的关系 (出边)
     * 例如: Device → Asset
     */
    FROM,
    
    /**
     * 指向实体的关系 (入边)
     * 例如: Asset ← Device
     */
    TO
}

