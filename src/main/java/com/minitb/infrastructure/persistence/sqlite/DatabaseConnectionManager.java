package com.minitb.infrastructure.persistence.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接管理器接口
 * 
 * 支持多种实现：
 * - SqliteConnectionManager: 单连接 + 自动重连
 * - HikariCpSqliteConnectionManager: HikariCP 连接池
 */
public interface DatabaseConnectionManager {
    
    /**
     * 获取数据库连接
     * 
     * ⚠️ 注意：
     * - SqliteConnectionManager: 返回单例连接（同步锁保护）
     * - HikariCpSqliteConnectionManager: 从连接池获取，需要调用者关闭
     * 
     * @return 数据库连接
     */
    Connection getConnection();
    
    /**
     * 检查连接是否可用
     * 
     * @return true 如果连接可用
     */
    boolean isConnected();
}

