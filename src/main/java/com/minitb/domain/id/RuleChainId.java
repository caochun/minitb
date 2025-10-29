package com.minitb.domain.id;

import java.util.UUID;

/**
 * 规则链ID
 */
public class RuleChainId extends EntityId {
    
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
