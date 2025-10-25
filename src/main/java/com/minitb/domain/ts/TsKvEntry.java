package com.minitb.domain.ts;

/**
 * 时间序列键值对条目接口
 * 在 KvEntry 基础上增加时间戳
 */
public interface TsKvEntry extends KvEntry {

    /**
     * 获取时间戳（毫秒）
     */
    long getTs();
}

