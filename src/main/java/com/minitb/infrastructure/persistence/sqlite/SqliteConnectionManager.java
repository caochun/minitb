package com.minitb.infrastructure.persistence.sqlite;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接管理器
 * 
 * 职责：
 * - 管理 SQLite 数据库连接
 * - 自动创建数据库文件和目录
 * - 初始化表结构
 * - 连接生命周期管理
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "sqlite")
@Slf4j
public class SqliteConnectionManager {
    
    @Value("${minitb.storage.sqlite.path:data/minitb.db}")
    private String dbPath;
    
    private Connection connection;
    
    @PostConstruct
    public void initialize() throws SQLException {
        log.info("初始化 SQLite 数据库: {}", dbPath);
        
        // 确保目录存在
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            log.info("创建数据库目录: {} (成功: {})", parentDir.getAbsolutePath(), created);
        }
        
        // 建立连接
        String url = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(url);
        
        // 启用外键约束
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        
        log.info("SQLite 连接已建立: {}", url);
        
        // 初始化表结构
        createTablesIfNotExist();
        
        log.info("SQLite 数据库初始化完成");
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * 创建表结构（如果不存在）
     */
    private void createTablesIfNotExist() throws SQLException {
        log.info("检查并创建表结构...");
        
        // 1. 创建 device_profile 表
        String createDeviceProfileTable = """
            CREATE TABLE IF NOT EXISTS device_profile (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT,
                telemetry_definitions_json TEXT,
                alarm_rules_json TEXT,
                strict_mode INTEGER DEFAULT 0,
                data_source_type TEXT,
                prometheus_endpoint TEXT,
                prometheus_device_label_key TEXT,
                default_rule_chain_id TEXT,
                default_queue_name TEXT,
                created_time INTEGER,
                updated_time INTEGER,
                UNIQUE(name)
            )
        """;
        
        // 2. 创建 device 表
        String createDeviceTable = """
            CREATE TABLE IF NOT EXISTS device (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT,
                access_token TEXT UNIQUE NOT NULL,
                device_profile_id TEXT,
                configuration TEXT,
                created_time INTEGER,
                updated_time INTEGER,
                FOREIGN KEY (device_profile_id) 
                    REFERENCES device_profile(id) 
                    ON DELETE CASCADE
            )
        """;
        
        // 3. 创建索引（优化查询性能）
        String createDeviceTokenIndex = """
            CREATE INDEX IF NOT EXISTS idx_device_access_token 
            ON device(access_token)
        """;
        
        String createDeviceProfileIdIndex = """
            CREATE INDEX IF NOT EXISTS idx_device_profile_id 
            ON device(device_profile_id)
        """;
        
        // 移除 prometheus_label 索引（该字段已移到 configuration 中）
        // String createDeviceLabelIndex = """
        //     CREATE INDEX IF NOT EXISTS idx_device_prometheus_label 
        //     ON device(prometheus_label)
        // """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDeviceProfileTable);
            log.info("✓ device_profile 表已就绪");
            
            stmt.execute(createDeviceTable);
            log.info("✓ device 表已就绪");
            
            stmt.execute(createDeviceTokenIndex);
            stmt.execute(createDeviceProfileIdIndex);
            // stmt.execute(createDeviceLabelIndex);  // 已注释，列不存在
            log.info("✓ 索引已创建");
        }
    }
    
    /**
     * 关闭连接
     */
    @PreDestroy
    public void cleanup() {
        if (connection != null) {
            try {
                connection.close();
                log.info("SQLite 连接已关闭");
            } catch (SQLException e) {
                log.error("关闭 SQLite 连接失败", e);
            }
        }
    }
    
    /**
     * 检查连接是否有效
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}


