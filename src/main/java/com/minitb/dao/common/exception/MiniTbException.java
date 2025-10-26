package com.minitb.dao.common.exception;

/**
 * MiniTB自定义异常
 * 
 * 用于MiniTB业务逻辑中的异常处理
 */
public class MiniTbException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final MiniTbErrorCode errorCode;
    
    public MiniTbException(MiniTbErrorCode errorCode) {
        super(errorCode.getErrorMsg());
        this.errorCode = errorCode;
    }
    
    public MiniTbException(MiniTbErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public MiniTbException(MiniTbErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public MiniTbErrorCode getErrorCode() {
        return errorCode;
    }
}