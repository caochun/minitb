package com.minitb.dao;

import com.minitb.domain.entity.DeviceProfile;
import com.minitb.domain.entity.DeviceProfileId;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB设备配置DAO接口
 * 定义设备配置相关的数据访问操作
 */
public interface DeviceProfileDao extends EntityDao<DeviceProfile, DeviceProfileId> {
    
    /**
     * 根据配置类型查找设备配置
     */
    List<DeviceProfile> findByType(String type);
    
    /**
     * 根据配置类型统计设备配置数量
     */
    long countByType(String type);
    
    /**
     * 根据传输类型查找设备配置
     */
    List<DeviceProfile> findByTransportType(String transportType);
    
    /**
     * 根据传输类型统计设备配置数量
     */
    long countByTransportType(String transportType);
    
    /**
     * 根据配置名称模糊查找
     */
    List<DeviceProfile> findByNameLike(String namePattern);
    
    /**
     * 根据是否为默认配置查找
     */
    List<DeviceProfile> findByIsDefault(boolean isDefault);
    
    /**
     * 根据是否为默认配置统计数量
     */
    long countByIsDefault(boolean isDefault);
    
    /**
     * 获取默认设备配置
     */
    Optional<DeviceProfile> findDefault();
    
    /**
     * 设置默认设备配置
     */
    void setDefault(DeviceProfileId deviceProfileId);
    
    /**
     * 取消默认设备配置
     */
    void unsetDefault(DeviceProfileId deviceProfileId);
    
    /**
     * 获取设备配置统计信息
     */
    DeviceProfileStatistics getDeviceProfileStatistics();
    
    /**
     * 检查设备配置是否被设备使用
     */
    boolean isUsedByDevices(DeviceProfileId deviceProfileId);
    
    /**
     * 根据数据源类型查找设备配置
     */
    List<DeviceProfile> findByDataSourceType(DeviceProfile.DataSourceType dataSourceType);
    
    /**
     * 设备配置统计信息
     */
    class DeviceProfileStatistics {
        private final long totalCount;
        private final long defaultCount;
        private final long customCount;
        private final long mqttCount;
        private final long coapCount;
        private final long httpCount;
        
        public DeviceProfileStatistics(long totalCount, long defaultCount, long customCount, 
                                     long mqttCount, long coapCount, long httpCount) {
            this.totalCount = totalCount;
            this.defaultCount = defaultCount;
            this.customCount = customCount;
            this.mqttCount = mqttCount;
            this.coapCount = coapCount;
            this.httpCount = httpCount;
        }
        
        // Getters
        public long getTotalCount() { return totalCount; }
        public long getDefaultCount() { return defaultCount; }
        public long getCustomCount() { return customCount; }
        public long getMqttCount() { return mqttCount; }
        public long getCoapCount() { return coapCount; }
        public long getHttpCount() { return httpCount; }
    }
}