package com.minitb.integration;

import com.minitb.MiniTBSpringBootApplication;
import com.minitb.infrastructure.persistence.sqlite.DatabaseConnectionManager;
import com.minitb.infrastructure.persistence.sqlite.HikariCpSqliteConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HikariCP 连接池测试
 */
@SpringBootTest(classes = MiniTBSpringBootApplication.class)
@ActiveProfiles("hikari-test")
@Slf4j
public class HikariCpConnectionPoolTest {
    
    @Autowired
    private DatabaseConnectionManager connectionManager;
    
    @Test
    void testHikariCpConnectionManager() {
        log.info("========================================");
        log.info("   测试 HikariCP 连接池");
        log.info("========================================");
        
        // 验证是 HikariCP 实现
        assertTrue(connectionManager instanceof HikariCpSqliteConnectionManager,
            "应该使用 HikariCP 实现");
        log.info("✓ 连接管理器类型: {}", connectionManager.getClass().getSimpleName());
        
        // 测试连接可用性
        assertTrue(connectionManager.isConnected(), "连接池应该可用");
        log.info("✓ 连接池状态: 可用");
        
        // 测试获取连接
        try (Connection conn = connectionManager.getConnection()) {
            assertNotNull(conn, "应该能获取到连接");
            assertFalse(conn.isClosed(), "连接应该是打开的");
            log.info("✓ 成功获取连接");
            
            // 测试执行查询
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertTrue(rs.next(), "查询应该返回结果");
                assertEquals(1, rs.getInt(1), "查询结果应该是 1");
                log.info("✓ 连接可以执行查询");
            }
            
        } catch (Exception e) {
            fail("不应该抛出异常: " + e.getMessage());
        }
        
        // 测试连接池统计
        if (connectionManager instanceof HikariCpSqliteConnectionManager) {
            HikariCpSqliteConnectionManager hikariManager = 
                (HikariCpSqliteConnectionManager) connectionManager;
            String stats = hikariManager.getPoolStats();
            log.info("📊 {}", stats);
            assertNotNull(stats, "统计信息不应为空");
        }
        
        log.info("✅ HikariCP 连接池测试通过");
    }
    
    @Test
    void testConcurrentConnections() throws InterruptedException {
        log.info("========================================");
        log.info("   测试并发连接");
        log.info("========================================");
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try (Connection conn = connectionManager.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT " + index)) {
                    if (rs.next() && rs.getInt(1) == index) {
                        results[index] = true;
                    }
                } catch (Exception e) {
                    log.error("线程 {} 执行失败", index, e);
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证所有线程都成功
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i], "线程 " + i + " 应该成功");
        }
        
        log.info("✅ {} 个并发连接全部成功", threadCount);
    }
}

