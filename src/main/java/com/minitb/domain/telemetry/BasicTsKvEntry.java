package com.minitb.domain.telemetry;

import lombok.Data;

import java.util.Optional;

/**
 * 时间序列键值对实现
 * 使用组合模式：包含一个 KvEntry 对象 + 时间戳
 */
@Data
public class BasicTsKvEntry implements TsKvEntry {

    protected final long ts;
    private final KvEntry kv;

    public BasicTsKvEntry(long ts, KvEntry kv) {
        this.ts = ts;
        this.kv = kv;
    }

    @Override
    public long getTs() {
        return ts;
    }

    @Override
    public String getKey() {
        return kv.getKey();
    }

    @Override
    public DataType getDataType() {
        return kv.getDataType();
    }

    @Override
    public Optional<String> getStrValue() {
        return kv.getStrValue();
    }

    @Override
    public Optional<Long> getLongValue() {
        return kv.getLongValue();
    }

    @Override
    public Optional<Boolean> getBooleanValue() {
        return kv.getBooleanValue();
    }

    @Override
    public Optional<Double> getDoubleValue() {
        return kv.getDoubleValue();
    }

    @Override
    public Optional<String> getJsonValue() {
        return kv.getJsonValue();
    }

    @Override
    public String getValueAsString() {
        return kv.getValueAsString();
    }

    @Override
    public Object getValue() {
        return kv.getValue();
    }

    @Override
    public String toString() {
        return "BasicTsKvEntry{" +
                "ts=" + ts +
                ", key='" + getKey() + '\'' +
                ", type=" + getDataType() +
                ", value=" + getValueAsString() +
                '}';
    }
}

