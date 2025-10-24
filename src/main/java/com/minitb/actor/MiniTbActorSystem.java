package com.minitb.actor;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 简化版 Actor 系统
 * 
 * 核心功能:
 * 1. Actor 注册与管理
 * 2. 消息路由
 * 3. 生命周期管理
 * 
 * 简化点（相比 ThingsBoard）:
 * - 单租户，无需租户层级
 * - 无 Dispatcher 抽象，统一使用一个线程池
 * - 无父子关系管理
 * - 无分布式支持
 */
@Slf4j
public class MiniTbActorSystem {
    
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, MiniTbActorMailbox> actors = new ConcurrentHashMap<>();
    private volatile boolean stopped = false;
    
    /**
     * 创建 Actor 系统
     * @param threadPoolSize 线程池大小
     */
    public MiniTbActorSystem(int threadPoolSize) {
        this.executorService = Executors.newFixedThreadPool(
                threadPoolSize,
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("minitb-actor-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                }
        );
        log.info("Actor 系统已创建，线程池大小: {}", threadPoolSize);
    }
    
    /**
     * 创建 Actor
     * @param actorId Actor ID
     * @param actor Actor 实例
     * @return Actor 邮箱引用
     */
    public MiniTbActorMailbox createActor(String actorId, MiniTbActor actor) {
        if (stopped) {
            throw new IllegalStateException("Actor 系统已停止");
        }
        
        MiniTbActorMailbox mailbox = actors.computeIfAbsent(actorId, id -> {
            log.info("创建 Actor: {}", actorId);
            MiniTbActorMailbox mb = new MiniTbActorMailbox(actorId, actor, executorService, this);
            mb.init();
            return mb;
        });
        
        if (mailbox == null) {
            log.debug("Actor [{}] 已存在", actorId);
        }
        
        return mailbox;
    }
    
    /**
     * 获取 Actor
     */
    public MiniTbActorMailbox getActor(String actorId) {
        return actors.get(actorId);
    }
    
    /**
     * 发送消息（普通优先级）
     */
    public void tell(String actorId, MiniTbActorMsg msg) {
        MiniTbActorMailbox mailbox = actors.get(actorId);
        if (mailbox == null) {
            log.warn("Actor [{}] 不存在，消息被丢弃: {}", actorId, msg.getActorMsgType());
            msg.onActorStopped();
            return;
        }
        mailbox.tell(msg);
    }
    
    /**
     * 发送消息（高优先级）
     */
    public void tellWithHighPriority(String actorId, MiniTbActorMsg msg) {
        MiniTbActorMailbox mailbox = actors.get(actorId);
        if (mailbox == null) {
            log.warn("Actor [{}] 不存在，消息被丢弃: {}", actorId, msg.getActorMsgType());
            msg.onActorStopped();
            return;
        }
        mailbox.tellWithHighPriority(msg);
    }
    
    /**
     * 停止指定 Actor
     */
    public void stop(String actorId) {
        MiniTbActorMailbox mailbox = actors.remove(actorId);
        if (mailbox != null) {
            log.info("停止 Actor: {}", actorId);
            mailbox.destroy();
        }
    }
    
    /**
     * 停止所有 Actor 和系统
     */
    public void shutdown() {
        if (stopped) {
            return;
        }
        stopped = true;
        
        log.info("正在关闭 Actor 系统...");
        
        // 停止所有 Actor
        actors.forEach((id, mailbox) -> {
            log.info("停止 Actor: {}", id);
            mailbox.destroy();
        });
        actors.clear();
        
        // 关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("线程池未能在10秒内关闭，强制关闭");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("等待线程池关闭时被中断", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Actor 系统已关闭");
    }
    
    /**
     * 获取系统状态信息
     */
    public String getSystemInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Actor 系统状态 ===\n");
        sb.append("总 Actor 数: ").append(actors.size()).append("\n");
        sb.append("已停止: ").append(stopped).append("\n");
        sb.append("\nActor 列表:\n");
        actors.forEach((id, mailbox) -> {
            sb.append("  - ").append(id)
              .append(" (队列: ").append(mailbox.getQueueSize()).append(")\n");
        });
        return sb.toString();
    }
}

