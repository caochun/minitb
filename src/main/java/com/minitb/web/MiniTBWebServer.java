package com.minitb.web;

import com.minitb.dao.common.exception.MiniTbException;
import com.minitb.dao.device.DeviceService;
import com.minitb.web.controller.DeviceController;
import com.minitb.web.handler.ExceptionHandler;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import lombok.extern.slf4j.Slf4j;

/**
 * MiniTB Web 服务器
 * 基于 Javalin 提供 HTTP API
 */
@Slf4j
public class MiniTBWebServer {
    
    private final Javalin app;
    private final int port;
    private final DeviceController deviceController;
    
    public MiniTBWebServer(int port, DeviceService deviceService) {
        this.port = port;
        this.deviceController = new DeviceController(deviceService);
        
        // 创建 Javalin 应用
        this.app = Javalin.create(config -> {
            // 启用 CORS
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
            
            // 启用开发日志
            config.plugins.enableDevLogging();
            
            // 静态文件（如果将来需要提供 Web UI）
            // config.staticFiles.add("/public", Location.CLASSPATH);
            
            log.info("Javalin 配置完成");
        });
        
        // 配置路由
        setupRoutes();
        
        // 配置异常处理
        setupExceptionHandlers();
    }
    
    /**
     * 配置路由
     */
    private void setupRoutes() {
        // 健康检查
        app.get("/api/health", ctx -> {
            ctx.json(new HealthResponse("ok", System.currentTimeMillis()));
        });
        
        // 设备管理 API
        app.get("/api/devices", deviceController::getAllDevices);
        app.get("/api/devices/{id}", deviceController::getDeviceById);
        app.post("/api/devices", deviceController::createDevice);
        app.put("/api/devices/{id}", deviceController::updateDevice);
        app.delete("/api/devices/{id}", deviceController::deleteDevice);
        
        log.info("路由配置完成");
    }
    
    /**
     * 配置异常处理
     */
    private void setupExceptionHandlers() {
        app.exception(MiniTbException.class, ExceptionHandler::handleMiniTbException);
        app.exception(Exception.class, ExceptionHandler::handleException);
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        app.start(port);
        log.info("MiniTB Web Server 已启动在端口: {}", port);
        log.info("API 文档: http://localhost:{}/api/health", port);
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        app.stop();
        log.info("MiniTB Web Server 已停止");
    }
    
    /**
     * 健康检查响应
     */
    private static class HealthResponse {
        public final String status;
        public final long timestamp;
        
        public HealthResponse(String status, long timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }
    }
}

