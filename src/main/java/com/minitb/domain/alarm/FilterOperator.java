package com.minitb.domain.alarm;

/**
 * 条件过滤器操作符
 */
public enum FilterOperator {
    /**
     * 等于
     */
    EQUAL,
    
    /**
     * 不等于
     */
    NOT_EQUAL,
    
    /**
     * 大于
     */
    GREATER_THAN,
    
    /**
     * 大于等于
     */
    GREATER_OR_EQUAL,
    
    /**
     * 小于
     */
    LESS_THAN,
    
    /**
     * 小于等于
     */
    LESS_OR_EQUAL,
    
    /**
     * 包含（字符串）
     */
    CONTAINS,
    
    /**
     * 不包含（字符串）
     */
    NOT_CONTAINS,
    
    /**
     * 以...开始（字符串）
     */
    STARTS_WITH,
    
    /**
     * 以...结束（字符串）
     */
    ENDS_WITH
}



