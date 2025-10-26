package com.minitb.domain.id;

import java.util.UUID;

/**
 * 规则链ID
 * 
 * 改进点：
 * 1. 不可变ID（继承自UUIDBased）
 * 2. 缓存hashCode
 * 3. 工厂方法
 * 4. 类型安全
 * 
 * 包结构说明：
 * - 移动到 domain/rule 包下，与 RuleChain 在同一包
 * - 符合领域驱动设计原则
 * - 规则相关的ID应该与规则实体放在一起
 */
public class RuleChainId extends UUIDBased {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造器：使用指定UUID
     */
    public RuleChainId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static RuleChainId fromUUID(UUID uuid) {
        return new RuleChainId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static RuleChainId fromString(String ruleChainId) {
        return new RuleChainId(UUID.fromString(ruleChainId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static RuleChainId random() {
        return new RuleChainId(UUID.randomUUID());
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.RULE_CHAIN;
    }
}
