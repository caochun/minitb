package com.minitb.dao;

import com.minitb.domain.entity.Asset;
import com.minitb.domain.entity.AssetId;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB资产DAO接口
 * 定义资产相关的数据访问操作
 */
public interface AssetDao extends EntityDao<Asset, AssetId> {
    
    /**
     * 根据资产类型查找资产
     */
    List<Asset> findByType(String type);
    
    /**
     * 根据资产类型统计资产数量
     */
    long countByType(String type);
    
    /**
     * 根据资产状态查找资产
     */
    List<Asset> findByStatus(String status);
    
    /**
     * 根据资产状态统计资产数量
     */
    long countByStatus(String status);
    
    /**
     * 根据资产标签查找资产
     */
    List<Asset> findByLabel(String label);
    
    /**
     * 根据资产名称模糊查找
     */
    List<Asset> findByNameLike(String namePattern);
    
    /**
     * 根据父资产ID查找子资产
     */
    List<Asset> findByParentAssetId(String parentAssetId);
    
    /**
     * 根据父资产ID统计子资产数量
     */
    long countByParentAssetId(String parentAssetId);
    
    /**
     * 获取资产统计信息
     */
    AssetStatistics getAssetStatistics();
    
    /**
     * 资产统计信息
     */
    class AssetStatistics {
        private final long totalCount;
        private final long activeCount;
        private final long inactiveCount;
        private final long onlineCount;
        private final long offlineCount;
        
        public AssetStatistics(long totalCount, long activeCount, long inactiveCount, 
                             long onlineCount, long offlineCount) {
            this.totalCount = totalCount;
            this.activeCount = activeCount;
            this.inactiveCount = inactiveCount;
            this.onlineCount = onlineCount;
            this.offlineCount = offlineCount;
        }
        
        // Getters
        public long getTotalCount() { return totalCount; }
        public long getActiveCount() { return activeCount; }
        public long getInactiveCount() { return inactiveCount; }
        public long getOnlineCount() { return onlineCount; }
        public long getOfflineCount() { return offlineCount; }
    }
}