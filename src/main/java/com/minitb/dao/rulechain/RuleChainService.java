package com.minitb.dao.rulechain;

import com.minitb.domain.rule.RuleChain;
import com.minitb.domain.rule.RuleChainId;
import com.minitb.domain.rule.node.RuleNode;
import com.minitb.dao.common.exception.MiniTbException;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB规则链服务接口
 * 定义规则链相关的业务逻辑
 */
public interface RuleChainService {

    /**
     * 保存规则链
     */
    RuleChain save(RuleChain ruleChain) throws MiniTbException;

    /**
     * 根据ID查找规则链
     */
    Optional<RuleChain> findById(RuleChainId ruleChainId);

    /**
     * 根据ID获取规则链（不存在则抛出异常）
     */
    RuleChain getById(RuleChainId ruleChainId) throws MiniTbException;

    /**
     * 根据名称查找规则链
     */
    Optional<RuleChain> findByName(String name);

    /**
     * 删除规则链
     */
    void delete(RuleChainId ruleChainId) throws MiniTbException;

    /**
     * 获取所有规则链
     */
    List<RuleChain> findAll();

    /**
     * 检查规则链名称是否已存在
     */
    boolean existsByName(String name);

    /**
     * 添加规则节点
     */
    RuleChain addRuleNode(RuleChainId ruleChainId, RuleNode ruleNode) throws MiniTbException;

    /**
     * 移除规则节点
     */
    RuleChain removeRuleNode(RuleChainId ruleChainId, String nodeId) throws MiniTbException;

    /**
     * 更新规则节点
     */
    RuleChain updateRuleNode(RuleChainId ruleChainId, RuleNode ruleNode) throws MiniTbException;

    /**
     * 获取规则节点列表
     */
    List<RuleNode> getRuleNodes(RuleChainId ruleChainId) throws MiniTbException;

    /**
     * 获取根规则链
     */
    Optional<RuleChain> getRootRuleChain();

    /**
     * 设置根规则链
     */
    RuleChain setRootRuleChain(RuleChainId ruleChainId) throws MiniTbException;

    /**
     * 执行规则链
     */
    void executeRuleChain(RuleChainId ruleChainId, Object message) throws MiniTbException;
}
