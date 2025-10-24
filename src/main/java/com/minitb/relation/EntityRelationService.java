package com.minitb.relation;

import com.minitb.common.entity.TenantId;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 实体关系服务
 * 
 * 职责:
 * 1. 保存和删除实体关系
 * 2. 查询实体的关系
 * 3. 支持递归查询关系路径
 */
@Slf4j
public class EntityRelationService {
    
    // 关系存储: key = relationKey, value = EntityRelation
    private final Map<String, EntityRelation> relations = new ConcurrentHashMap<>();
    
    // 索引1: fromId -> List<EntityRelation> (出边索引)
    private final Map<UUID, List<EntityRelation>> fromIndex = new ConcurrentHashMap<>();
    
    // 索引2: toId -> List<EntityRelation> (入边索引)
    private final Map<UUID, List<EntityRelation>> toIndex = new ConcurrentHashMap<>();
    
    public EntityRelationService() {
        log.info("实体关系服务初始化完成");
    }
    
    /**
     * 保存关系
     */
    public EntityRelation saveRelation(TenantId tenantId, EntityRelation relation) {
        String key = relation.getKey();
        
        // 保存关系
        relations.put(key, relation);
        
        // 更新fromIndex
        fromIndex.computeIfAbsent(relation.getFromId(), k -> new ArrayList<>())
                 .add(relation);
        
        // 更新toIndex
        toIndex.computeIfAbsent(relation.getToId(), k -> new ArrayList<>())
               .add(relation);
        
        log.info("保存实体关系: {} [{}] --[{}]--> [{}] {}", 
                 relation.getFromType(), relation.getFromId(),
                 relation.getRelationType(),
                 relation.getToType(), relation.getToId());
        
        return relation;
    }
    
    /**
     * 批量保存关系
     */
    public void saveRelations(TenantId tenantId, List<EntityRelation> relationList) {
        for (EntityRelation relation : relationList) {
            saveRelation(tenantId, relation);
        }
        log.info("批量保存 {} 个实体关系", relationList.size());
    }
    
    /**
     * 删除关系
     */
    public boolean deleteRelation(TenantId tenantId, EntityRelation relation) {
        String key = relation.getKey();
        
        EntityRelation removed = relations.remove(key);
        if (removed == null) {
            log.warn("关系不存在: {}", key);
            return false;
        }
        
        // 更新索引
        removeFromIndex(fromIndex, relation.getFromId(), relation);
        removeFromIndex(toIndex, relation.getToId(), relation);
        
        log.info("删除实体关系: {} [{}] --[{}]--> [{}] {}", 
                 relation.getFromType(), relation.getFromId(),
                 relation.getRelationType(),
                 relation.getToType(), relation.getToId());
        
        return true;
    }
    
    /**
     * 删除关系（通过参数）
     */
    public boolean deleteRelation(TenantId tenantId, UUID fromId, String fromType, 
                                  UUID toId, String toType, String relationType, 
                                  RelationTypeGroup typeGroup) {
        EntityRelation relation = new EntityRelation(fromId, fromType, toId, toType, relationType, typeGroup);
        return deleteRelation(tenantId, relation);
    }
    
    /**
     * 删除实体的所有关系（出边和入边）
     */
    public void deleteEntityRelations(TenantId tenantId, UUID entityId) {
        // 删除所有出边
        List<EntityRelation> outbound = fromIndex.getOrDefault(entityId, new ArrayList<>());
        for (EntityRelation relation : new ArrayList<>(outbound)) {
            deleteRelation(tenantId, relation);
        }
        
        // 删除所有入边
        List<EntityRelation> inbound = toIndex.getOrDefault(entityId, new ArrayList<>());
        for (EntityRelation relation : new ArrayList<>(inbound)) {
            deleteRelation(tenantId, relation);
        }
        
        log.info("删除实体 {} 的所有关系", entityId);
    }
    
    /**
     * 查询从某实体出发的所有关系
     */
    public List<EntityRelation> findByFrom(TenantId tenantId, UUID fromId, RelationTypeGroup typeGroup) {
        List<EntityRelation> result = fromIndex.getOrDefault(fromId, new ArrayList<>());
        
        if (typeGroup != null) {
            result = result.stream()
                    .filter(r -> r.getTypeGroup() == typeGroup)
                    .collect(Collectors.toList());
        }
        
        log.debug("查询出边关系: fromId={}, typeGroup={}, 结果数={}", fromId, typeGroup, result.size());
        return new ArrayList<>(result);
    }
    
    /**
     * 查询指向某实体的所有关系
     */
    public List<EntityRelation> findByTo(TenantId tenantId, UUID toId, RelationTypeGroup typeGroup) {
        List<EntityRelation> result = toIndex.getOrDefault(toId, new ArrayList<>());
        
        if (typeGroup != null) {
            result = result.stream()
                    .filter(r -> r.getTypeGroup() == typeGroup)
                    .collect(Collectors.toList());
        }
        
        log.debug("查询入边关系: toId={}, typeGroup={}, 结果数={}", toId, typeGroup, result.size());
        return new ArrayList<>(result);
    }
    
    /**
     * 查询从某实体出发的指定类型关系
     */
    public List<EntityRelation> findByFromAndType(TenantId tenantId, UUID fromId, 
                                                   String relationType, RelationTypeGroup typeGroup) {
        return findByFrom(tenantId, fromId, typeGroup).stream()
                .filter(r -> r.getRelationType().equals(relationType))
                .collect(Collectors.toList());
    }
    
    /**
     * 查询指向某实体的指定类型关系
     */
    public List<EntityRelation> findByToAndType(TenantId tenantId, UUID toId, 
                                                 String relationType, RelationTypeGroup typeGroup) {
        return findByTo(tenantId, toId, typeGroup).stream()
                .filter(r -> r.getRelationType().equals(relationType))
                .collect(Collectors.toList());
    }
    
    /**
     * 检查关系是否存在
     */
    public boolean checkRelation(TenantId tenantId, UUID fromId, String fromType,
                                 UUID toId, String toType, String relationType, 
                                 RelationTypeGroup typeGroup) {
        EntityRelation relation = new EntityRelation(fromId, fromType, toId, toType, relationType, typeGroup);
        return relations.containsKey(relation.getKey());
    }
    
    /**
     * 获取特定关系
     */
    public EntityRelation getRelation(TenantId tenantId, UUID fromId, String fromType,
                                      UUID toId, String toType, String relationType,
                                      RelationTypeGroup typeGroup) {
        EntityRelation relation = new EntityRelation(fromId, fromType, toId, toType, relationType, typeGroup);
        return relations.get(relation.getKey());
    }
    
    /**
     * 递归查找关系路径
     * @param fromId 起始实体ID
     * @param direction 搜索方向
     * @param maxLevel 最大层级
     * @return 所有找到的实体ID
     */
    public Set<UUID> findRelatedEntities(TenantId tenantId, UUID fromId, 
                                         EntitySearchDirection direction, int maxLevel) {
        Set<UUID> visited = new HashSet<>();
        Set<UUID> result = new HashSet<>();
        
        findRelatedEntitiesRecursive(tenantId, fromId, direction, maxLevel, 0, visited, result);
        
        log.info("递归查询关系: fromId={}, direction={}, maxLevel={}, 结果数={}", 
                 fromId, direction, maxLevel, result.size());
        
        return result;
    }
    
    /**
     * 递归查找关系（内部方法）
     */
    private void findRelatedEntitiesRecursive(TenantId tenantId, UUID entityId, 
                                              EntitySearchDirection direction, int maxLevel, 
                                              int currentLevel, Set<UUID> visited, Set<UUID> result) {
        if (currentLevel >= maxLevel || visited.contains(entityId)) {
            return;
        }
        
        visited.add(entityId);
        
        List<EntityRelation> relations;
        if (direction == EntitySearchDirection.FROM) {
            // 查找出边
            relations = fromIndex.getOrDefault(entityId, new ArrayList<>());
            for (EntityRelation relation : relations) {
                result.add(relation.getToId());
                findRelatedEntitiesRecursive(tenantId, relation.getToId(), direction, 
                                            maxLevel, currentLevel + 1, visited, result);
            }
        } else {
            // 查找入边
            relations = toIndex.getOrDefault(entityId, new ArrayList<>());
            for (EntityRelation relation : relations) {
                result.add(relation.getFromId());
                findRelatedEntitiesRecursive(tenantId, relation.getFromId(), direction, 
                                            maxLevel, currentLevel + 1, visited, result);
            }
        }
    }
    
    /**
     * 从索引中移除关系
     */
    private void removeFromIndex(Map<UUID, List<EntityRelation>> index, UUID key, EntityRelation relation) {
        List<EntityRelation> list = index.get(key);
        if (list != null) {
            list.removeIf(r -> r.getKey().equals(relation.getKey()));
            if (list.isEmpty()) {
                index.remove(key);
            }
        }
    }
    
    /**
     * 打印所有关系（调试用）
     */
    public void printAllRelations() {
        log.info("=== 所有实体关系 ===");
        log.info("总关系数: {}", relations.size());
        for (EntityRelation relation : relations.values()) {
            log.info("  {} [{}] --[{}]--> [{}] {}", 
                     relation.getFromType(), relation.getFromId(),
                     relation.getRelationType(),
                     relation.getToType(), relation.getToId());
        }
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRelations", relations.size());
        stats.put("relationTypes", relations.values().stream()
                .map(EntityRelation::getRelationType)
                .distinct()
                .collect(Collectors.toList()));
        stats.put("typeGroups", relations.values().stream()
                .map(EntityRelation::getTypeGroup)
                .distinct()
                .collect(Collectors.toList()));
        return stats;
    }
}

