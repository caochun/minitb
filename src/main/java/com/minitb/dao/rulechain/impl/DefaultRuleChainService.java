package com.minitb.dao.rulechain.impl;

import com.minitb.dao.rulechain.RuleChainService;
import com.minitb.dao.RuleChainDao;
import com.minitb.domain.rule.RuleChain;
import com.minitb.domain.rule.RuleChainId;
import com.minitb.domain.rule.node.RuleNode;
import com.minitb.domain.entity.RuleNodeId;
import com.minitb.dao.common.AbstractEntityService;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB规则链服务默认实现
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultRuleChainService extends AbstractEntityService implements RuleChainService {

    private final RuleChainDao ruleChainDao;

    @Override
    public RuleChain save(RuleChain ruleChain) throws MiniTbException {
        log.info("保存规则链: {}", ruleChain.getName());
        
        // 1. 验证规则链数据
        validateRuleChain(ruleChain);
        
        // 2. 检查名称冲突
        if (ruleChain.getId() == null) {
            // 新规则链，检查名称是否已存在
            if (existsByName(ruleChain.getName())) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                    "Rule chain with name '" + ruleChain.getName() + "' already exists");
            }
        } else {
            // 更新规则链，检查名称冲突（排除自己）
            Optional<RuleChain> existingRuleChain = findById(ruleChain.getId());
            if (existingRuleChain.isPresent() && !existingRuleChain.get().getName().equals(ruleChain.getName())) {
                if (existsByName(ruleChain.getName())) {
                    throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                        "Rule chain with name '" + ruleChain.getName() + "' already exists");
                }
            }
        }
        
        // 3. 保存规则链
        RuleChain savedRuleChain = ruleChainDao.save(ruleChain);
        
        // 4. 记录操作日志
        logEntityAction(savedRuleChain.getId(), "RULE_CHAIN_SAVED", "Rule chain saved: " + savedRuleChain.getName());
        
        log.info("规则链保存成功: {} (ID: {})", savedRuleChain.getName(), savedRuleChain.getId());
        return savedRuleChain;
    }

    @Override
    public Optional<RuleChain> findById(RuleChainId ruleChainId) {
        validateEntityId(ruleChainId);
        return ruleChainDao.findById(ruleChainId);
    }

    @Override
    public RuleChain getById(RuleChainId ruleChainId) throws MiniTbException {
        return checkNotNull(findById(ruleChainId), "Rule chain not found with ID: " + ruleChainId);
    }

    @Override
    public Optional<RuleChain> findByName(String name) {
        validateEntityName(name);
        return ruleChainDao.findByName(name);
    }

    @Override
    public void delete(RuleChainId ruleChainId) throws MiniTbException {
        log.info("删除规则链: {}", ruleChainId);
        
        // 1. 检查规则链是否存在
        RuleChain ruleChain = getById(ruleChainId);
        
        // 2. 检查是否为根规则链
        if (ruleChain.isRoot()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Root rule chain cannot be deleted");
        }
        
        // 3. 删除规则链
        ruleChainDao.delete(ruleChain);
        
        // 4. 记录操作日志
        logEntityAction(ruleChainId, "RULE_CHAIN_DELETED", "Rule chain deleted: " + ruleChain.getName());
        
        log.info("规则链删除成功: {} (ID: {})", ruleChain.getName(), ruleChainId);
    }

    @Override
    public List<RuleChain> findAll() {
        return ruleChainDao.findAll();
    }

    @Override
    public boolean existsByName(String name) {
        validateEntityName(name);
        return ruleChainDao.existsByName(name);
    }

    @Override
    public RuleChain addRuleNode(RuleChainId ruleChainId, RuleNode ruleNode) throws MiniTbException {
        log.info("添加规则节点: {} -> {}", ruleChainId, ruleNode.getId());
        
        // 1. 验证规则节点
        validateRuleNode(ruleNode);
        
        // 2. 获取规则链
        RuleChain ruleChain = getById(ruleChainId);
        
        // 3. 检查规则节点是否已存在
        if (ruleChain.hasRuleNode(ruleNode.getId())) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                "Rule node with ID '" + ruleNode.getId() + "' already exists");
        }
        
        // 4. 添加规则节点
        ruleChain.addRuleNode(ruleNode);
        RuleChain updatedRuleChain = ruleChainDao.save(ruleChain);
        
        // 5. 记录操作日志
        logEntityAction(ruleChainId, "RULE_NODE_ADDED", "Rule node added: " + ruleNode.getId());
        
        log.info("规则节点添加成功: {} -> {}", ruleChainId, ruleNode.getId());
        return updatedRuleChain;
    }

    @Override
    public RuleChain removeRuleNode(RuleChainId ruleChainId, String nodeId) throws MiniTbException {
        log.info("移除规则节点: {} -> {}", ruleChainId, nodeId);
        
        // 1. 验证节点ID
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Rule node ID cannot be null or empty");
        }
        
        // 2. 获取规则链
        RuleChain ruleChain = getById(ruleChainId);
        
        // 3. 检查规则节点是否存在
        RuleNodeId ruleNodeId = RuleNodeId.fromString(nodeId);
        if (!ruleChain.hasRuleNode(ruleNodeId)) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, 
                "Rule node with ID '" + nodeId + "' not found");
        }
        
        // 4. 移除规则节点
        ruleChain.removeRuleNode(ruleNodeId);
        RuleChain updatedRuleChain = ruleChainDao.save(ruleChain);
        
        // 5. 记录操作日志
        logEntityAction(ruleChainId, "RULE_NODE_REMOVED", "Rule node removed: " + nodeId);
        
        log.info("规则节点移除成功: {} -> {}", ruleChainId, nodeId);
        return updatedRuleChain;
    }

    @Override
    public RuleChain updateRuleNode(RuleChainId ruleChainId, RuleNode ruleNode) throws MiniTbException {
        log.info("更新规则节点: {} -> {}", ruleChainId, ruleNode.getId());
        
        // 1. 验证规则节点
        validateRuleNode(ruleNode);
        
        // 2. 获取规则链
        RuleChain ruleChain = getById(ruleChainId);
        
        // 3. 检查规则节点是否存在
        if (!ruleChain.hasRuleNode(ruleNode.getId())) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, 
                "Rule node with ID '" + ruleNode.getId() + "' not found");
        }
        
        // 4. 更新规则节点
        ruleChain.updateRuleNode(ruleNode);
        RuleChain updatedRuleChain = ruleChainDao.save(ruleChain);
        
        // 5. 记录操作日志
        logEntityAction(ruleChainId, "RULE_NODE_UPDATED", "Rule node updated: " + ruleNode.getId());
        
        log.info("规则节点更新成功: {} -> {}", ruleChainId, ruleNode.getId());
        return updatedRuleChain;
    }

    @Override
    public List<RuleNode> getRuleNodes(RuleChainId ruleChainId) throws MiniTbException {
        RuleChain ruleChain = getById(ruleChainId);
        return ruleChain.getRuleNodes();
    }

    @Override
    public Optional<RuleChain> getRootRuleChain() {
        return ruleChainDao.findRootRuleChain();
    }

    @Override
    public RuleChain setRootRuleChain(RuleChainId ruleChainId) throws MiniTbException {
        log.info("设置根规则链: {}", ruleChainId);
        
        // 1. 获取规则链
        RuleChain ruleChain = getById(ruleChainId);
        
        // 2. 取消当前根规则链
        Optional<RuleChain> currentRoot = getRootRuleChain();
        if (currentRoot.isPresent()) {
            currentRoot.get().setRoot(false);
            ruleChainDao.save(currentRoot.get());
        }
        
        // 3. 设置新的根规则链
        ruleChain.setRoot(true);
        RuleChain updatedRuleChain = ruleChainDao.save(ruleChain);
        
        // 4. 记录操作日志
        logEntityAction(ruleChainId, "ROOT_RULE_CHAIN_SET", "Root rule chain set: " + ruleChain.getName());
        
        log.info("根规则链设置成功: {}", ruleChainId);
        return updatedRuleChain;
    }

    @Override
    public void executeRuleChain(RuleChainId ruleChainId, Object message) throws MiniTbException {
        log.debug("执行规则链: {} -> {}", ruleChainId, message.getClass().getSimpleName());
        
        // 1. 获取规则链
        RuleChain ruleChain = getById(ruleChainId);
        
        // 2. 执行规则链
        try {
            ruleChain.processMessage(message);
            log.debug("规则链执行成功: {}", ruleChainId);
        } catch (Exception e) {
            log.error("规则链执行失败: {}", ruleChainId, e);
            throw new MiniTbException(MiniTbErrorCode.INTERNAL_ERROR, "Failed to execute rule chain", e);
        }
    }

    /**
     * 验证规则链数据
     */
    private void validateRuleChain(RuleChain ruleChain) throws MiniTbException {
        if (ruleChain == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Rule chain cannot be null");
        }
        
        validateEntityName(ruleChain.getName());
        
        // 注意：createdTime是final字段，无法修改
        // 如果需要设置创建时间，应该在创建RuleChain时设置
    }

    /**
     * 验证规则节点数据
     */
    private void validateRuleNode(RuleNode ruleNode) throws MiniTbException {
        if (ruleNode == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Rule node cannot be null");
        }
        
        if (ruleNode.getId() == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Rule node ID cannot be null");
        }
        
        if (ruleNode.getType() == null || ruleNode.getType().trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Rule node type cannot be null or empty");
        }
    }
}
