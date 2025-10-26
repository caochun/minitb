package com.minitb.dao.entity;

import com.minitb.domain.rule.RuleChain;
import com.minitb.domain.id.RuleChainId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则链实体类 - 数据库映射
 * 
 * 职责：
 * 1. 映射数据库表结构
 * 2. 处理类型转换（String ID）
 * 3. 提供 Domain ↔ Entity 转换方法
 * 
 * 设计理念：
 * - 使用原生类型便于数据库操作
 * - 与业务对象 RuleChain 分离，降低耦合
 * - Entity 层专注于持久化，Domain 层专注于业务逻辑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleChainEntity {
    
    /**
     * 规则链ID（数据库存储为字符串）
     */
    private String id;
    
    /**
     * 规则链名称
     */
    private String name;
    
    /**
     * 规则链类型
     */
    private String type;
    
    /**
     * 规则链配置（JSON格式存储节点信息）
     */
    private String configuration;
    
    /**
     * 创建时间（Unix 时间戳，毫秒）
     */
    private Long createdTime;
    
    // ==================== 转换方法 ====================
    
    /**
     * Domain → Entity 转换
     * 保存到数据库前调用
     * 
     * @param ruleChain 业务领域对象
     * @return 数据库实体对象
     */
    public static RuleChainEntity fromDomain(RuleChain ruleChain) {
        if (ruleChain == null) {
            return null;
        }
        
        return RuleChainEntity.builder()
                .id(ruleChain.getId().toString())
                .name(ruleChain.getName())
                .type("CORE") // 默认类型
                .configuration("{}") // 默认空配置，实际项目中可以序列化节点信息
                .createdTime(System.currentTimeMillis())
                .build();
    }
    
    /**
     * Entity → Domain 转换
     * 从数据库查询后调用
     * 
     * @return 业务领域对象
     */
    public RuleChain toDomain() {
        // 注意：这里只能创建基本的RuleChain，节点信息需要单独处理
        RuleChain ruleChain = new RuleChain(name);
        // 由于RuleChain的id是final的，我们需要通过反射或其他方式设置
        // 这里简化处理，实际项目中可能需要更复杂的转换逻辑
        // 暂时返回新创建的RuleChain，实际项目中需要更复杂的转换
        return ruleChain;
    }
}
