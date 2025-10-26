package com.minitb.dao.asset;

import com.minitb.domain.asset.Asset;
import com.minitb.domain.id.AssetId;
import com.minitb.dao.common.exception.MiniTbException;

import java.util.List;
import java.util.Optional;

/**
 * MiniTB资产服务接口
 * 定义资产相关的业务逻辑
 */
public interface AssetService {

    /**
     * 保存资产
     */
    Asset save(Asset asset) throws MiniTbException;

    /**
     * 根据ID查找资产
     */
    Optional<Asset> findById(AssetId assetId);

    /**
     * 根据ID获取资产（不存在则抛出异常）
     */
    Asset getById(AssetId assetId) throws MiniTbException;

    /**
     * 根据名称查找资产
     */
    Optional<Asset> findByName(String name);

    /**
     * 删除资产
     */
    void delete(AssetId assetId) throws MiniTbException;

    /**
     * 获取所有资产
     */
    List<Asset> findAll();

    /**
     * 根据类型查找资产
     */
    List<Asset> findByType(String type);

    /**
     * 检查资产名称是否已存在
     */
    boolean existsByName(String name);

    /**
     * 更新资产类型
     */
    Asset updateType(AssetId assetId, String type) throws MiniTbException;

    /**
     * 更新资产标签
     */
    Asset updateLabel(AssetId assetId, String label) throws MiniTbException;
}
