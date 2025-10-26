package com.minitb.infrastructure.transport.mqtt;

import com.minitb.infrastructure.transport.service.TransportService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * MQTT传输服务
 * 使用Netty实现MQTT服务器
 */
@Slf4j
public class MqttTransportService {
    
    private final int port;
    private final TransportService transportService;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    public MqttTransportService(int port, TransportService transportService) {
        this.port = port;
        this.transportService = transportService;
    }

    /**
     * 启动MQTT服务器
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // MQTT编解码器
                            pipeline.addLast("decoder", new MqttDecoder());
                            pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                            
                            // 空闲检测
                            pipeline.addLast("idleStateHandler", 
                                    new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));
                            
                            // MQTT业务处理器
                            pipeline.addLast("handler", 
                                    new MqttTransportHandler(transportService));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            
            // 绑定端口
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            log.info("MQTT服务器启动成功，监听端口: {}", port);
            log.info("设备可以使用以下方式连接:");
            log.info("  mosquitto_pub -h localhost -p {} -u test-token-001 -t v1/devices/me/telemetry -m '{{\"temperature\":25}}'", port);
            
        } catch (Exception e) {
            log.error("MQTT服务器启动失败", e);
            shutdown();
            throw e;
        }
    }

    /**
     * 关闭MQTT服务器
     */
    public void shutdown() {
        log.info("MQTT服务器关闭中...");
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        log.info("MQTT服务器已关闭");
    }
}




