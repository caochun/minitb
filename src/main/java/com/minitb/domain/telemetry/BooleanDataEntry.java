package com.minitb.domain.telemetry;

import java.util.Objects;
import java.util.Optional;

/**
 * 布尔类型数据条目
 */
public class BooleanDataEntry extends BasicKvEntry {

    private final Boolean value;

    public BooleanDataEntry(String key, Boolean value) {
        super(key);
        this.value = value;
    }

    @Override
    public DataType getDataType() {
        return DataType.BOOLEAN;
    }

    @Override
    public Optional<Boolean> getBooleanValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public String getValueAsString() {
        return value != null ? Boolean.toString(value) : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanDataEntry)) return false;
        if (!super.equals(o)) return false;
        BooleanDataEntry that = (BooleanDataEntry) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }

    @Override
    public String toString() {
        return "BooleanDataEntry{" +
                "key='" + getKey() + '\'' +
                ", value=" + value +
                '}';
    }
}

