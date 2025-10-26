package com.minitb.dao.impl;

import com.minitb.dao.BaseDaoImpl;
import com.minitb.dao.RuleChainDao;
import com.minitb.domain.rule.RuleChain;
import com.minitb.domain.id.RuleChainId;
import com.minitb.dao.entity.RuleChainEntity;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MiniTB规则链DAO实现
 * 基于SQLite的规则链数据访问实现
 * 负责领域模型与实体模型的转换
 */
@Slf4j
public class RuleChainDaoImpl extends BaseDaoImpl<RuleChain, RuleChainId> implements RuleChainDao {
    
    public RuleChainDaoImpl(Connection connection) {
        super(connection, "rule_chains", "id");
    }
    
    @Override
    protected RuleChain insert(RuleChain ruleChain) throws SQLException {
        // 1. 领域模型 -> 实体模型
        RuleChainEntity entity = RuleChainEntity.fromDomain(ruleChain);
        
        String sql = """
            INSERT INTO rule_chains (id, name, type, configuration, created_time)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entity.getId());
            stmt.setString(2, entity.getName());
            stmt.setString(3, entity.getType());
            stmt.setString(4, entity.getConfiguration());
            stmt.setLong(5, entity.getCreatedTime());
            
            stmt.executeUpdate();
            return ruleChain;
        }
    }
    
    @Override
    protected RuleChain update(RuleChain ruleChain) throws SQLException {
        // 1. 领域模型 -> 实体模型
        RuleChainEntity entity = RuleChainEntity.fromDomain(ruleChain);
        
        String sql = """
            UPDATE rule_chains SET name = ?, type = ?, configuration = ?
            WHERE id = ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entity.getName());
            stmt.setString(2, entity.getType());
            stmt.setString(3, entity.getConfiguration());
            stmt.setString(4, entity.getId());
            
            stmt.executeUpdate();
            return ruleChain;
        }
    }
    
    @Override
    protected RuleChain mapRowToEntity(ResultSet rs) throws SQLException {
        // 1. 从ResultSet创建实体模型
        RuleChainEntity entity = new RuleChainEntity();
        entity.setId(rs.getString("id"));
        entity.setName(rs.getString("name"));
        entity.setType(rs.getString("type"));
        entity.setConfiguration(rs.getString("configuration"));
        entity.setCreatedTime(rs.getLong("created_time"));
        
        // 2. 实体模型 -> 领域模型
        return entity.toDomain();
    }
    
    @Override
    protected RuleChainId getEntityId(RuleChain ruleChain) {
        return ruleChain.getId();
    }
    
    // ==================== 业务方法实现 ====================
    
    @Override
    public List<RuleChain> findByType(String type) {
        try {
            String sql = "SELECT * FROM rule_chains WHERE type = ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<RuleChain> ruleChains = new ArrayList<>();
                    while (rs.next()) {
                        ruleChains.add(mapRowToEntity(rs));
                    }
                    return ruleChains;
                }
            }
        } catch (SQLException e) {
            log.error("根据规则链类型查找失败: type={}", type, e);
            throw new RuntimeException("查找规则链失败: " + e.getMessage());
        }
    }
    
    @Override
    public long countByType(String type) {
        try {
            String sql = "SELECT COUNT(*) FROM rule_chains WHERE type = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计规则链数量失败: type={}", type, e);
            throw new RuntimeException("统计规则链数量失败: " + e.getMessage());
        }
    }
    
    
    @Override
    public List<RuleChain> findByNameLike(String namePattern) {
        try {
            String sql = "SELECT * FROM rule_chains WHERE name LIKE ? ORDER BY created_time DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, "%" + namePattern + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    List<RuleChain> ruleChains = new ArrayList<>();
                    while (rs.next()) {
                        ruleChains.add(mapRowToEntity(rs));
                    }
                    return ruleChains;
                }
            }
        } catch (SQLException e) {
            log.error("根据规则链名称模糊查找失败: namePattern={}", namePattern, e);
            throw new RuntimeException("查找规则链失败: " + e.getMessage());
        }
    }
    
    @Override
    public RuleChainStatistics getRuleChainStatistics() {
        try {
            String sql = """
                SELECT 
                    COUNT(*) as total_count,
                    SUM(CASE WHEN type = 'CORE' THEN 1 ELSE 0 END) as core_count,
                    SUM(CASE WHEN type = 'EDGE' THEN 1 ELSE 0 END) as edge_count
                FROM rule_chains
                """;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new RuleChainStatistics(
                            rs.getLong("total_count"),
                            rs.getLong("core_count"),
                            rs.getLong("edge_count")
                        );
                    }
                }
            }
            return new RuleChainStatistics(0, 0, 0);
        } catch (SQLException e) {
            log.error("获取规则链统计信息失败", e);
            throw new RuntimeException("获取规则链统计信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<RuleChain> findByName(String name) {
        try {
            String sql = "SELECT * FROM rule_chains WHERE name = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToRuleChain(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            log.error("根据名称查找规则链失败", e);
            throw new RuntimeException("根据名称查找规则链失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean existsByName(String name) {
        try {
            String sql = "SELECT 1 FROM rule_chains WHERE name = ? LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("检查规则链名称是否存在失败", e);
            throw new RuntimeException("检查规则链名称是否存在失败: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<RuleChain> findRootRuleChain() {
        try {
            String sql = "SELECT * FROM rule_chains WHERE name = 'Root Rule Chain' OR name = 'root' LIMIT 1";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToRuleChain(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            log.error("查找根规则链失败", e);
            throw new RuntimeException("查找根规则链失败: " + e.getMessage());
        }
    }
    
    /**
     * 将ResultSet映射为RuleChain对象
     */
    private RuleChain mapResultSetToRuleChain(ResultSet rs) throws SQLException {
        RuleChainId id = RuleChainId.fromString(rs.getString("id"));
        String name = rs.getString("name");
        String type = rs.getString("type");
        String configuration = rs.getString("configuration");
        long createdTime = rs.getLong("created_time");
        
        RuleChain ruleChain = new RuleChain(name);
        // 注意：这里需要根据实际的RuleChain构造函数调整
        return ruleChain;
    }
}
