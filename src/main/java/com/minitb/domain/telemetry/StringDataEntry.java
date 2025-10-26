package com.minitb.domain.telemetry;

import java.util.Objects;
import java.util.Optional;

/**
 * 字符串类型数据条目
 */
public class StringDataEntry extends BasicKvEntry {

    private final String value;

    public StringDataEntry(String key, String value) {
        super(key);
        this.value = value;
    }

    @Override
    public DataType getDataType() {
        return DataType.STRING;
    }

    @Override
    public Optional<String> getStrValue() {
        return Optional.ofNullable(value);
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public String getValueAsString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringDataEntry)) return false;
        if (!super.equals(o)) return false;
        StringDataEntry that = (StringDataEntry) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }

    @Override
    public String toString() {
        return "StringDataEntry{" +
                "key='" + getKey() + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}

