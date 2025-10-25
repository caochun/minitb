package com.minitb.service;

/**
 * MiniTB错误代码枚举
 * 
 * 定义MiniTB系统中的各种错误代码
 */
public enum MiniTbErrorCode {
    
    // ==================== 通用错误 ====================
    GENERAL(1000, "通用错误"),
    INVALID_ARGUMENT(1001, "无效参数"),
    NOT_FOUND(1002, "未找到"),
    ITEM_NOT_FOUND(1002, "未找到"),  // 别名
    ALREADY_EXISTS(1003, "已存在"),
    UNAUTHORIZED(1004, "未授权"),
    FORBIDDEN(1005, "禁止访问"),
    BAD_REQUEST_PARAMS(1006, "请求参数错误"),
    INTERNAL_ERROR(1007, "内部错误"),
    
    // ==================== 设备相关错误 ====================
    DEVICE_NOT_FOUND(2001, "设备未找到"),
    DEVICE_ALREADY_EXISTS(2002, "设备已存在"),
    DEVICE_INVALID_STATE(2003, "设备状态无效"),
    DEVICE_ACCESS_TOKEN_INVALID(2004, "设备访问令牌无效"),
    
    // ==================== 资产相关错误 ====================
    ASSET_NOT_FOUND(3001, "资产未找到"),
    ASSET_ALREADY_EXISTS(3002, "资产已存在"),
    ASSET_INVALID_TYPE(3003, "资产类型无效"),
    
    // ==================== 设备配置相关错误 ====================
    DEVICE_PROFILE_NOT_FOUND(4001, "设备配置未找到"),
    DEVICE_PROFILE_ALREADY_EXISTS(4002, "设备配置已存在"),
    DEVICE_PROFILE_INVALID_CONFIG(4003, "设备配置无效"),
    
    // ==================== 规则链相关错误 ====================
    RULE_CHAIN_NOT_FOUND(5001, "规则链未找到"),
    RULE_CHAIN_ALREADY_EXISTS(5002, "规则链已存在"),
    RULE_CHAIN_INVALID_CONFIG(5003, "规则链配置无效"),
    RULE_NODE_NOT_FOUND(5004, "规则节点未找到"),
    
    // ==================== 关系相关错误 ====================
    RELATION_NOT_FOUND(6001, "关系未找到"),
    RELATION_ALREADY_EXISTS(6002, "关系已存在"),
    RELATION_INVALID_TYPE(6003, "关系类型无效"),
    
    // ==================== 数据库相关错误 ====================
    DATABASE_ERROR(7001, "数据库错误"),
    DATABASE_CONNECTION_ERROR(7002, "数据库连接错误"),
    DATABASE_QUERY_ERROR(7003, "数据库查询错误"),
    
    // ==================== 外部服务相关错误 ====================
    PROMETHEUS_ERROR(8001, "Prometheus服务错误"),
    MQTT_ERROR(8002, "MQTT服务错误"),
    HTTP_ERROR(8003, "HTTP服务错误");
    
    private final int code;
    private final String errorMsg;
    
    MiniTbErrorCode(int code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
    
    @Override
    public String toString() {
        return String.format("MiniTbErrorCode{code=%d, errorMsg='%s'}", code, errorMsg);
    }
}