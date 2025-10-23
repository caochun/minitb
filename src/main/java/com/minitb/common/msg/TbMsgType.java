package com.minitb.common.msg;

/**
 * 消息类型枚举
 */
public enum TbMsgType {
    /**
     * 上报遥测数据
     */
    POST_TELEMETRY_REQUEST,
    
    /**
     * 上报属性数据
     */
    POST_ATTRIBUTES_REQUEST,
    
    /**
     * RPC请求
     */
    TO_SERVER_RPC_REQUEST,
    
    /**
     * 实体创建事件
     */
    ENTITY_CREATED,
    
    /**
     * 实体更新事件
     */
    ENTITY_UPDATED,
    
    /**
     * 实体删除事件
     */
    ENTITY_DELETED
}


