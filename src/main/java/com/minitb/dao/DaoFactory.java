package com.minitb.dao;

import com.minitb.dao.impl.DeviceDaoImpl;
import com.minitb.dao.impl.AssetDaoImpl;
import com.minitb.dao.impl.DeviceProfileDaoImpl;
import com.minitb.dao.impl.EntityRelationDaoImpl;
import com.minitb.dao.impl.RuleChainDaoImpl;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MiniTB DAO工厂类
 * 负责创建和管理所有DAO实例
 */
@Slf4j
public class DaoFactory {
    
    private final Connection connection;
    private final DeviceDao deviceDao;
    private final AssetDao assetDao;
    private final DeviceProfileDao deviceProfileDao;
    private final EntityRelationDao entityRelationDao;
    private final RuleChainDao ruleChainDao;
    
    public DaoFactory(Connection connection) throws SQLException {
        this.connection = connection;
        
        // 初始化所有DAO
        this.deviceDao = new DeviceDaoImpl(connection);
        this.assetDao = new AssetDaoImpl(connection);
        this.deviceProfileDao = new DeviceProfileDaoImpl(connection);
        this.entityRelationDao = new EntityRelationDaoImpl(connection);
        this.ruleChainDao = new RuleChainDaoImpl(connection);
        
        log.info("DAO工厂初始化完成");
    }
    
    /**
     * 获取设备DAO
     */
    public DeviceDao getDeviceDao() {
        return deviceDao;
    }
    
    /**
     * 获取资产DAO
     */
    public AssetDao getAssetDao() {
        return assetDao;
    }
    
    /**
     * 获取设备配置DAO
     */
    public DeviceProfileDao getDeviceProfileDao() {
        return deviceProfileDao;
    }
    
    /**
     * 获取实体关系DAO
     */
    public EntityRelationDao getEntityRelationDao() {
        return entityRelationDao;
    }
    
    /**
     * 获取规则链DAO
     */
    public RuleChainDao getRuleChainDao() {
        return ruleChainDao;
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * 关闭所有资源
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            log.error("关闭数据库连接失败", e);
        }
    }
    
    /**
     * 检查数据库连接是否有效
     */
    public boolean isConnectionValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            log.error("检查数据库连接失败", e);
            return false;
        }
    }
    
    /**
     * 获取DAO统计信息
     */
    public DaoStatistics getStatistics() {
        try {
            return new DaoStatistics(
                deviceDao.count(),
                assetDao.count(),
                deviceProfileDao.count(),
                entityRelationDao.count(),
                ruleChainDao.count()
            );
        } catch (Exception e) {
            log.error("获取DAO统计信息失败", e);
            return new DaoStatistics(0, 0, 0, 0, 0);
        }
    }
    
    /**
     * DAO统计信息
     */
    public static class DaoStatistics {
        private final long deviceCount;
        private final long assetCount;
        private final long deviceProfileCount;
        private final long relationCount;
        private final long ruleChainCount;
        
        public DaoStatistics(long deviceCount, long assetCount, long deviceProfileCount, 
                           long relationCount, long ruleChainCount) {
            this.deviceCount = deviceCount;
            this.assetCount = assetCount;
            this.deviceProfileCount = deviceProfileCount;
            this.relationCount = relationCount;
            this.ruleChainCount = ruleChainCount;
        }
        
        // Getters
        public long getDeviceCount() { return deviceCount; }
        public long getAssetCount() { return assetCount; }
        public long getDeviceProfileCount() { return deviceProfileCount; }
        public long getRelationCount() { return relationCount; }
        public long getRuleChainCount() { return ruleChainCount; }
        
        @Override
        public String toString() {
            return String.format("DaoStatistics{devices=%d, assets=%d, profiles=%d, relations=%d, ruleChains=%d}",
                    deviceCount, assetCount, deviceProfileCount, relationCount, ruleChainCount);
        }
    }
}
