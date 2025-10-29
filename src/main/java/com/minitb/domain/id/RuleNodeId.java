package com.minitb.domain.id;

import java.util.UUID;

/**
 * 规则节点ID
 */
public class RuleNodeId extends EntityId {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造器：使用指定UUID
     */
    public RuleNodeId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static RuleNodeId fromUUID(UUID uuid) {
        return new RuleNodeId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static RuleNodeId fromString(String ruleNodeId) {
        return new RuleNodeId(UUID.fromString(ruleNodeId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static RuleNodeId random() {
        return new RuleNodeId(UUID.randomUUID());
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.RULE_CHAIN; // RuleNode属于RuleChain
    }
}
