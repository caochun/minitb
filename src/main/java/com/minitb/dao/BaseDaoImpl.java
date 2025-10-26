package com.minitb.dao;

import com.minitb.domain.id.EntityId;
import com.minitb.domain.id.UUIDBased;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MiniTB基础DAO实现
 * 提供通用的数据访问操作实现
 */
@Slf4j
public abstract class BaseDaoImpl<T, ID extends EntityId> implements BaseDao<T, ID> {
    
    protected final Connection connection;
    protected final String tableName;
    protected final String idColumn;
    
    public BaseDaoImpl(Connection connection, String tableName, String idColumn) {
        this.connection = connection;
        this.tableName = tableName;
        this.idColumn = idColumn;
    }
    
    @Override
    public T save(T entity) {
        try {
            if (existsById(getEntityId(entity))) {
                return update(entity);
            } else {
                return insert(entity);
            }
        } catch (SQLException e) {
            log.error("保存实体失败: {}", entity, e);
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "保存实体失败: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<T> findById(ID id) {
        try {
            String sql = String.format("SELECT * FROM %s WHERE %s = ?", tableName, idColumn);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToEntity(rs));
                    }
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            log.error("根据ID查找实体失败: {}", id, e);
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "查找实体失败: " + e.getMessage());
        }
    }
    
    @Override
    public T getById(ID id) throws MiniTbException {
        return findById(id).orElseThrow(() -> 
            new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, "实体不存在: " + id));
    }
    
    @Override
    public void deleteById(ID id) {
        try {
            String sql = String.format("DELETE FROM %s WHERE %s = ?", tableName, idColumn);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id.toString());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, "实体不存在: " + id);
                }
            }
        } catch (SQLException e) {
            log.error("删除实体失败: {}", id, e);
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "删除实体失败: " + e.getMessage());
        }
    }
    
    @Override
    public void delete(T entity) {
        deleteById(getEntityId(entity));
    }
    
    @Override
    public boolean existsById(ID id) {
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", tableName, idColumn);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            log.error("检查实体是否存在失败: {}", id, e);
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "检查实体是否存在失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<T> findAll() {
        try {
            String sql = String.format("SELECT * FROM %s ORDER BY created_time DESC", tableName);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    List<T> entities = new ArrayList<>();
                    while (rs.next()) {
                        entities.add(mapRowToEntity(rs));
                    }
                    return entities;
                }
            }
        } catch (SQLException e) {
            log.error("查找所有实体失败", e);
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "查找所有实体失败: " + e.getMessage());
        }
    }
    
    
    @Override
    public long count() {
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            }
        } catch (SQLException e) {
            log.error("统计实体数量失败", e);
            throw new MiniTbException(MiniTbErrorCode.GENERAL, "统计实体数量失败: " + e.getMessage());
        }
    }
    
    // 抽象方法，由子类实现
    protected abstract T insert(T entity) throws SQLException;
    protected abstract T update(T entity) throws SQLException;
    protected abstract T mapRowToEntity(ResultSet rs) throws SQLException;
    protected abstract ID getEntityId(T entity);
}
