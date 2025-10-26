package com.minitb.dao.common;

import com.minitb.domain.id.EntityId;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;
import com.minitb.domain.id.UUIDBased;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * MiniTB实体服务抽象基类
 * 提供通用的实体服务功能
 */
@Slf4j
public abstract class AbstractEntityService {
    
    protected static final Logger log = LoggerFactory.getLogger(AbstractEntityService.class);

    /**
     * 检查对象是否为空，为空则抛出异常
     */
    protected <T> T checkNotNull(T reference) throws MiniTbException {
        return checkNotNull(reference, "Requested item wasn't found!");
    }

    /**
     * 检查对象是否为空，为空则抛出异常
     */
    protected <T> T checkNotNull(T reference, String notFoundMessage) throws MiniTbException {
        if (reference == null) {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, notFoundMessage);
        }
        return reference;
    }

    /**
     * 检查Optional是否为空，为空则抛出异常
     */
    protected <T> T checkNotNull(Optional<T> reference) throws MiniTbException {
        return checkNotNull(reference, "Requested item wasn't found!");
    }

    /**
     * 检查Optional是否为空，为空则抛出异常
     */
    protected <T> T checkNotNull(Optional<T> reference, String notFoundMessage) throws MiniTbException {
        if (reference.isPresent()) {
            return reference.get();
        } else {
            throw new MiniTbException(MiniTbErrorCode.ITEM_NOT_FOUND, notFoundMessage);
        }
    }

    /**
     * 创建空ID
     */
    protected <I extends UUIDBased> I emptyId(Class<I> idClass) {
        try {
            return idClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("Failed to create empty ID for class: {}", idClass.getSimpleName(), e);
            throw new RuntimeException("Failed to create empty ID", e);
        }
    }

    /**
     * 记录实体操作日志
     */
    protected void logEntityAction(EntityId entityId, String action, String details) {
        log.info("Entity action: {} - {} - {}", action, entityId, details);
    }

    /**
     * 验证实体ID
     */
    protected void validateEntityId(EntityId entityId) throws MiniTbException {
        if (entityId == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Entity ID cannot be null");
        }
    }

    /**
     * 验证实体名称
     */
    protected void validateEntityName(String name) throws MiniTbException {
        if (name == null || name.trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Entity name cannot be null or empty");
        }
        if (name.length() > 255) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Entity name too long");
        }
    }
}
