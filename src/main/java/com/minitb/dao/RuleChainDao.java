package com.minitb.dao;

import com.minitb.domain.rule.RuleChain;
import com.minitb.domain.id.RuleChainId;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB规则链DAO接口
 * 定义规则链相关的数据访问操作
 */
public interface RuleChainDao extends BaseDao<RuleChain, RuleChainId> {
    
    /**
     * 根据规则链类型查找规则链
     */
    List<RuleChain> findByType(String type);
    
    /**
     * 根据规则链类型统计规则链数量
     */
    long countByType(String type);
    
    /**
     * 根据规则链名称查找
     */
    Optional<RuleChain> findByName(String name);
    
    /**
     * 检查规则链名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 查找根规则链
     */
    Optional<RuleChain> findRootRuleChain();
    
    /**
     * 根据规则链名称模糊查找
     */
    List<RuleChain> findByNameLike(String namePattern);
    
    /**
     * 获取规则链统计信息
     */
    RuleChainStatistics getRuleChainStatistics();
    
    /**
     * 规则链统计信息
     */
    class RuleChainStatistics {
        private final long totalCount;
        private final long coreCount;
        private final long edgeCount;
        
        public RuleChainStatistics(long totalCount, long coreCount, long edgeCount) {
            this.totalCount = totalCount;
            this.coreCount = coreCount;
            this.edgeCount = edgeCount;
        }
        
        public long getTotalCount() { return totalCount; }
        public long getCoreCount() { return coreCount; }
        public long getEdgeCount() { return edgeCount; }
        
        @Override
        public String toString() {
            return String.format("RuleChainStatistics{total=%d, core=%d, edge=%d}",
                    totalCount, coreCount, edgeCount);
        }
    }
}
