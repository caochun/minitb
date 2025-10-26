package com.minitb.domain.telemetry;

import java.util.Objects;
import java.util.Optional;

/**
 * 基础键值对实现
 * 提供键名存储和默认的空值返回
 * 具体子类覆盖相应的 getXxxValue() 方法
 */
public abstract class BasicKvEntry implements KvEntry {

    private final String key;

    protected BasicKvEntry(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Optional<String> getStrValue() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> getLongValue() {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBooleanValue() {
        return Optional.empty();
    }

    @Override
    public Optional<Double> getDoubleValue() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getJsonValue() {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BasicKvEntry)) return false;
        BasicKvEntry that = (BasicKvEntry) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "BasicKvEntry{" +
                "key='" + key + '\'' +
                '}';
    }
}

