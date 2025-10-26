package com.minitb.domain.telemetry;

import java.io.Serializable;
import java.util.Optional;

/**
 * 键值对条目接口
 * 代表一个键值对数据项，支持多种数据类型
 */
public interface KvEntry extends Serializable {

    /**
     * 获取键名
     */
    String getKey();

    /**
     * 获取数据类型
     */
    DataType getDataType();

    /**
     * 获取字符串值
     */
    Optional<String> getStrValue();

    /**
     * 获取长整型值
     */
    Optional<Long> getLongValue();

    /**
     * 获取布尔值
     */
    Optional<Boolean> getBooleanValue();

    /**
     * 获取双精度浮点值
     */
    Optional<Double> getDoubleValue();

    /**
     * 获取JSON值
     */
    Optional<String> getJsonValue();

    /**
     * 获取值的字符串表示
     */
    String getValueAsString();

    /**
     * 获取原始值对象
     */
    Object getValue();
}

