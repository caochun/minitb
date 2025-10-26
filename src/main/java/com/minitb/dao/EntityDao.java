package com.minitb.dao;

import com.minitb.domain.id.EntityId;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB实体DAO接口
 * 定义实体相关的数据访问操作
 */
public interface EntityDao<T, ID extends EntityId> extends BaseDao<T, ID> {
    
    /**
     * 根据名称查找实体
     */
    Optional<T> findByName(String name);
    
    /**
     * 检查名称是否已存在
     */
    boolean existsByName(String name);
}
