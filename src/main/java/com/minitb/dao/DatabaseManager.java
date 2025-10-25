package com.minitb.dao;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据库连接管理器
 * 
 * 职责：
 * 1. 管理数据库连接（单例模式）
 * 2. 初始化数据库表结构
 * 3. 提供连接访问
 * 
 * 特点：
 * - 无需 Spring，纯 JDBC
 * - SQLite 嵌入式，无需安装数据库服务
 * - 单文件数据库，便于管理
 */
@Slf4j
public class DatabaseManager {
    
    private static final String DB_URL = "jdbc:sqlite:minitb.db";
    private static Connection connection;
    
    /**
     * 获取数据库连接（单例）
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            // 启用外键约束
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            log.info("SQLite 数据库连接已建立: {}", DB_URL);
        }
        return connection;
    }
    
    /**
     * 初始化数据库表
     */
    public static void initDatabase() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            
            // ==================== 设备配置表 ====================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS device_profile (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    data_source_type TEXT,
                    strict_mode INTEGER DEFAULT 0,
                    telemetry_definitions_json TEXT,
                    created_time INTEGER NOT NULL
                )
            """);
            
            // ==================== 设备表 ====================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS device (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    access_token TEXT UNIQUE NOT NULL,
                    device_profile_id TEXT,
                    created_time INTEGER NOT NULL,
                    FOREIGN KEY (device_profile_id) REFERENCES device_profile(id)
                )
            """);
            
            // ==================== 资产表 ====================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS asset (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    label TEXT,
                    created_time INTEGER NOT NULL
                )
            """);
            
            // ==================== 实体关系表 ====================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS entity_relation (
                    from_id TEXT NOT NULL,
                    from_type TEXT NOT NULL,
                    to_id TEXT NOT NULL,
                    to_type TEXT NOT NULL,
                    relation_type TEXT NOT NULL,
                    type_group TEXT NOT NULL,
                    PRIMARY KEY (from_id, from_type, to_id, to_type, relation_type, type_group)
                )
            """);
            
            // ==================== 创建索引 ====================
            
            // 设备表索引
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_device_token 
                ON device(access_token)
            """);
            
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_device_profile 
                ON device(device_profile_id)
            """);
            
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_device_type 
                ON device(type)
            """);
            
            // 资产表索引
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_asset_type 
                ON asset(type)
            """);
            
            // 实体关系表索引
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_relation_from 
                ON entity_relation(from_id, from_type)
            """);
            
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_relation_to 
                ON entity_relation(to_id, to_type)
            """);
            
            log.info("数据库表初始化完成");
            log.info("  - device_profile 表");
            log.info("  - device 表");
            log.info("  - asset 表");
            log.info("  - entity_relation 表");
            log.info("  - 索引已创建");
        }
    }
    
    /**
     * 关闭数据库连接
     */
    public static void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("数据库连接已关闭");
            } catch (SQLException e) {
                log.error("关闭数据库连接失败", e);
            }
        }
    }
    
    /**
     * 获取数据库文件路径
     */
    public static String getDatabasePath() {
        return DB_URL.replace("jdbc:sqlite:", "");
    }
}

