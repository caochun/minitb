package com.minitb.web.handler;

import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.web.dto.ApiResponse;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器
 */
@Slf4j
public class ExceptionHandler {
    
    /**
     * 处理 MiniTB 业务异常
     */
    public static void handleMiniTbException(MiniTbException e, Context ctx) {
        log.error("业务异常: {}", e.getMessage());
        ctx.status(400).json(ApiResponse.error(e.getMessage()));
    }
    
    /**
     * 处理未知异常
     */
    public static void handleException(Exception e, Context ctx) {
        log.error("未知异常", e);
        ctx.status(500).json(ApiResponse.error("服务器内部错误: " + e.getMessage()));
    }
}

