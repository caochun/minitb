package com.minitb.dao;

import com.minitb.domain.entity.EntityId;
import com.minitb.service.MiniTbException;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB基础DAO接口
 * 定义所有DAO的通用操作
 */
public interface BaseDao<T, ID extends EntityId> {
    
    /**
     * 保存实体
     */
    T save(T entity);
    
    /**
     * 根据ID查找实体
     */
    Optional<T> findById(ID id);
    
    /**
     * 根据ID获取实体（不存在则抛出异常）
     */
    T getById(ID id) throws MiniTbException;
    
    /**
     * 删除实体
     */
    void deleteById(ID id);
    
    /**
     * 删除实体
     */
    void delete(T entity);
    
    /**
     * 检查实体是否存在
     */
    boolean existsById(ID id);
    
    /**
     * 获取所有实体
     */
    List<T> findAll();
    
    /**
     * 统计实体数量
     */
    long count();
    
}
