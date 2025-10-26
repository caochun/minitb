package com.minitb.dao.asset.impl;

import com.minitb.dao.asset.AssetService;
import com.minitb.dao.AssetDao;
import com.minitb.domain.entity.Asset;
import com.minitb.domain.entity.AssetId;
import com.minitb.dao.common.AbstractEntityService;
import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.common.exception.MiniTbErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB资产服务默认实现
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultAssetService extends AbstractEntityService implements AssetService {

    private final AssetDao assetDao;

    @Override
    public Asset save(Asset asset) throws MiniTbException {
        log.info("保存资产: {}", asset.getName());
        
        // 1. 验证资产数据
        validateAsset(asset);
        
        // 2. 检查名称冲突
        if (asset.getId() == null) {
            // 新资产，检查名称是否已存在
            if (existsByName(asset.getName())) {
                throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                    "Asset with name '" + asset.getName() + "' already exists");
            }
        } else {
            // 更新资产，检查名称冲突（排除自己）
            Optional<Asset> existingAsset = findById(asset.getId());
            if (existingAsset.isPresent() && !existingAsset.get().getName().equals(asset.getName())) {
                if (existsByName(asset.getName())) {
                    throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, 
                        "Asset with name '" + asset.getName() + "' already exists");
                }
            }
        }
        
        // 3. 保存资产
        Asset savedAsset = assetDao.save(asset);
        
        // 4. 记录操作日志
        logEntityAction(savedAsset.getId(), "ASSET_SAVED", "Asset saved: " + savedAsset.getName());
        
        log.info("资产保存成功: {} (ID: {})", savedAsset.getName(), savedAsset.getId());
        return savedAsset;
    }

    @Override
    public Optional<Asset> findById(AssetId assetId) {
        validateEntityId(assetId);
        return assetDao.findById(assetId);
    }

    @Override
    public Asset getById(AssetId assetId) throws MiniTbException {
        return checkNotNull(findById(assetId), "Asset not found with ID: " + assetId);
    }

    @Override
    public Optional<Asset> findByName(String name) {
        validateEntityName(name);
        return assetDao.findByName(name);
    }

    @Override
    public void delete(AssetId assetId) throws MiniTbException {
        log.info("删除资产: {}", assetId);
        
        // 1. 检查资产是否存在
        Asset asset = getById(assetId);
        
        // 2. 删除资产
        assetDao.delete(asset);
        
        // 3. 记录操作日志
        logEntityAction(assetId, "ASSET_DELETED", "Asset deleted: " + asset.getName());
        
        log.info("资产删除成功: {} (ID: {})", asset.getName(), assetId);
    }

    @Override
    public List<Asset> findAll() {
        return assetDao.findAll();
    }

    @Override
    public List<Asset> findByType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return List.of();
        }
        return assetDao.findByType(type);
    }

    @Override
    public boolean existsByName(String name) {
        validateEntityName(name);
        return assetDao.existsByName(name);
    }

    @Override
    public Asset updateType(AssetId assetId, String type) throws MiniTbException {
        log.info("更新资产类型: {} -> {}", assetId, type);
        
        // 1. 验证类型
        if (type == null || type.trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Asset type cannot be null or empty");
        }
        
        // 2. 获取资产
        Asset asset = getById(assetId);
        
        // 3. 更新资产类型
        asset.setType(type);
        Asset updatedAsset = assetDao.save(asset);
        
        // 4. 记录操作日志
        logEntityAction(assetId, "ASSET_TYPE_UPDATED", "Asset type updated to: " + type);
        
        log.info("资产类型更新成功: {} -> {}", assetId, type);
        return updatedAsset;
    }

    @Override
    public Asset updateLabel(AssetId assetId, String label) throws MiniTbException {
        log.info("更新资产标签: {} -> {}", assetId, label);
        
        // 1. 获取资产
        Asset asset = getById(assetId);
        
        // 2. 更新资产标签
        asset.setLabel(label);
        Asset updatedAsset = assetDao.save(asset);
        
        // 3. 记录操作日志
        logEntityAction(assetId, "ASSET_LABEL_UPDATED", "Asset label updated to: " + label);
        
        log.info("资产标签更新成功: {} -> {}", assetId, label);
        return updatedAsset;
    }

    /**
     * 验证资产数据
     */
    private void validateAsset(Asset asset) throws MiniTbException {
        if (asset == null) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Asset cannot be null");
        }
        
        validateEntityName(asset.getName());
        
        if (asset.getType() == null || asset.getType().trim().isEmpty()) {
            throw new MiniTbException(MiniTbErrorCode.BAD_REQUEST_PARAMS, "Asset type cannot be null or empty");
        }
        
        if (asset.getCreatedTime() <= 0) {
            asset.setCreatedTime(System.currentTimeMillis());
        }
    }
}
