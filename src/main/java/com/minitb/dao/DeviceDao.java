package com.minitb.dao;

import com.minitb.domain.device.Device;
import com.minitb.domain.id.DeviceId;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备DAO接口
 * 定义设备相关的数据访问操作
 */
public interface DeviceDao extends EntityDao<Device, DeviceId> {
    
    /**
     * 根据访问令牌查找设备
     */
    Optional<Device> findByAccessToken(String accessToken);
    
    /**
     * 检查访问令牌是否存在
     */
    boolean existsByAccessToken(String accessToken);
    
    /**
     * 根据设备配置ID查找设备
     */
    List<Device> findByDeviceProfileId(String deviceProfileId);
    
    /**
     * 根据设备配置ID统计设备数量
     */
    long countByDeviceProfileId(String deviceProfileId);
    
    /**
     * 根据设备配置ID删除设备
     */
    void deleteByDeviceProfileId(String deviceProfileId);
    
    /**
     * 根据设备类型查找设备
     */
    List<Device> findByType(String type);
    
    /**
     * 根据设备类型统计设备数量
     */
    long countByType(String type);
    
    /**
     * 根据设备状态查找设备
     */
    List<Device> findByStatus(String status);
    
    /**
     * 根据设备状态统计设备数量
     */
    long countByStatus(String status);
    
    /**
     * 根据设备名称模糊查找
     */
    List<Device> findByNameLike(String namePattern);
    
    /**
     * 根据设备标签查找设备
     */
    List<Device> findByLabel(String label);
    
    /**
     * 获取设备统计信息
     */
    DeviceStatistics getDeviceStatistics();
    
    /**
     * 设备统计信息
     */
    class DeviceStatistics {
        private final long totalCount;
        private final long activeCount;
        private final long inactiveCount;
        private final long onlineCount;
        private final long offlineCount;
        
        public DeviceStatistics(long totalCount, long activeCount, long inactiveCount, 
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