package com.minitb.infrastructure.persistence.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 基于 HikariCP 的 SQLite 连接管理器
 * 
 * 职责：
 * - 使用 HikariCP 连接池管理 SQLite 连接
 * - 提供高性能、线程安全的连接管理
 * - 自动管理连接的创建、复用和销毁
 * 
 * 优势：
 * - 连接复用，提升性能
 * - 自动连接检测和恢复
 * - 线程安全，无需额外同步
 * - 资源管理自动化
 */
@Component("hikariCpConnectionManager")
@ConditionalOnProperty(
    prefix = "minitb.storage.sqlite",
    name = "use-hikari",
    havingValue = "true"
)
@Slf4j
public class HikariCpSqliteConnectionManager implements DatabaseConnectionManager {
    
    @Value("${minitb.storage.sqlite.path:data/minitb.db}")
    private String dbPath;
    
    private HikariDataSource dataSource;
    
    @PostConstruct
    public void initialize() throws SQLException {
        log.info("初始化 SQLite 数据库（HikariCP 连接池）: {}", dbPath);
        
        // 确保目录存在
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            log.info("创建数据库目录: {} (成功: {})", parentDir.getAbsolutePath(), created);
        }
        
        // 配置 HikariCP
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        
        // ⚠️ SQLite 特殊配置：由于代码中不关闭连接，使用多连接模式
        config.setMaximumPoolSize(10);         // ⭐ 增加连接池大小，允许"泄漏"的连接
        config.setMinimumIdle(1);              // 保持 1 个空闲连接
        config.setConnectionTimeout(5000);      // 连接超时 5 秒
        config.setIdleTimeout(300000);          // 空闲超时 5 分钟
        config.setMaxLifetime(600000);          // 连接最大存活 10 分钟
        config.setAutoCommit(true);             // 自动提交
        config.setLeakDetectionThreshold(60000); // ⭐ 连接泄漏检测 60 秒
        
        // SQLite 性能优化参数
        config.addDataSourceProperty("journal_mode", "WAL");        // ⭐ WAL 模式
        config.addDataSourceProperty("busy_timeout", "5000");       // 锁超时 5 秒
        config.addDataSourceProperty("foreign_keys", "ON");         // 启用外键
        config.addDataSourceProperty("synchronous", "NORMAL");      // 同步模式
        config.addDataSourceProperty("cache_size", "10000");        // 缓存大小
        
        // 连接池名称
        config.setPoolName("MiniTB-SQLite-Pool");
        
        // 创建数据源
        dataSource = new HikariDataSource(config);
        
        log.info("✓ HikariCP 连接池已创建");
        log.info("  - 最大连接数: {}", config.getMaximumPoolSize());
        log.info("  - 最小空闲连接: {}", config.getMinimumIdle());
        log.info("  - WAL 模式: 已启用");
        
        // 初始化表结构
        try (Connection conn = dataSource.getConnection()) {
            createTablesIfNotExist(conn);
        }
        
        log.info("SQLite 数据库初始化完成（HikariCP）");
    }
    
    /**
     * 获取数据库连接
     * ⭐ 从连接池获取连接，调用者负责关闭（归还到池）
     */
    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            log.error("从连接池获取连接失败", e);
            throw new RuntimeException("Failed to get connection from pool", e);
        }
    }
    
    /**
     * 创建表结构（如果不存在）
     */
    private void createTablesIfNotExist(Connection conn) throws SQLException {
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
        
        // 4. 创建 alarm 表
        String createAlarmTable = """
            CREATE TABLE IF NOT EXISTS alarm (
                id TEXT PRIMARY KEY,
                device_id TEXT NOT NULL,
                device_name TEXT,
                type TEXT NOT NULL,
                severity TEXT NOT NULL,
                start_ts INTEGER NOT NULL,
                end_ts INTEGER NOT NULL,
                ack_ts INTEGER,
                clear_ts INTEGER,
                details TEXT,
                created_time INTEGER NOT NULL,
                FOREIGN KEY (device_id) 
                    REFERENCES device(id) 
                    ON DELETE CASCADE
            )
        """;
        
        String createAlarmDeviceIndex = """
            CREATE INDEX IF NOT EXISTS idx_alarm_device 
            ON alarm(device_id)
        """;
        
        String createAlarmStatusIndex = """
            CREATE INDEX IF NOT EXISTS idx_alarm_status 
            ON alarm(clear_ts, ack_ts)
        """;
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createDeviceProfileTable);
            log.info("✓ device_profile 表已就绪");
            
            stmt.execute(createDeviceTable);
            log.info("✓ device 表已就绪");
            
            stmt.execute(createAlarmTable);
            log.info("✓ alarm 表已就绪");
            
            stmt.execute(createDeviceTokenIndex);
            stmt.execute(createDeviceProfileIdIndex);
            stmt.execute(createAlarmDeviceIndex);
            stmt.execute(createAlarmStatusIndex);
            log.info("✓ 索引已创建");
        }
    }
    
    /**
     * 关闭连接池
     */
    @PreDestroy
    public void cleanup() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP 连接池已关闭");
        }
    }
    
    /**
     * 检查连接池是否可用
     */
    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * 获取连接池统计信息
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "连接池未初始化";
        }
        return String.format("连接池状态 [总连接: %d, 活动: %d, 空闲: %d, 等待: %d]",
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}

