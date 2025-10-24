package com.minitb.common.kv;

import java.util.Objects;
import java.util.Optional;

/**
 * 双精度浮点数据条目
 */
public class DoubleDataEntry extends BasicKvEntry {

    private final Double value;

    public DoubleDataEntry(String key, Double value) {
        super(key);
        this.value = value;
    }

    @Override
    public DataType getDataType() {
        return DataType.DOUBLE;
    }

    @Override
    public Optional<Double> getDoubleValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public String getValueAsString() {
        return value != null ? Double.toString(value) : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleDataEntry)) return false;
        if (!super.equals(o)) return false;
        DoubleDataEntry that = (DoubleDataEntry) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }

    @Override
    public String toString() {
        return "DoubleDataEntry{" +
                "key='" + getKey() + '\'' +
                ", value=" + value +
                '}';
    }
}

