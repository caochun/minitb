package com.minitb.actor;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actor 邮箱
 * 负责消息的接收、排队和处理
 * 
 * 核心设计:
 * 1. 双队列: 高优先级队列 + 普通队列
 * 2. 单线程处理: 保证同一 Actor 的消息串行处理
 * 3. 批量处理: 每次处理多个消息，提高吞吐量
 */
@Slf4j
public class MiniTbActorMailbox implements MiniTbActorContext {
    
    private final String actorId;
    private final MiniTbActor actor;
    private final ExecutorService executor;
    private final MiniTbActorSystem system;
    
    // 双队列
    private final ConcurrentLinkedQueue<MiniTbActorMsg> highPriorityQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<MiniTbActorMsg> normalQueue = new ConcurrentLinkedQueue<>();
    
    // 状态标记
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    
    // 批量处理配置
    private static final int BATCH_SIZE = 10; // 每次最多处理10个消息
    
    public MiniTbActorMailbox(String actorId, MiniTbActor actor, ExecutorService executor, MiniTbActorSystem system) {
        this.actorId = actorId;
        this.actor = actor;
        this.executor = executor;
        this.system = system;
    }
    
    /**
     * 初始化 Actor
     */
    public void init() {
        executor.execute(() -> {
            try {
                log.debug("[{}] 初始化 Actor...", actorId);
                actor.init(this);
                log.debug("[{}] Actor 初始化完成", actorId);
            } catch (Exception e) {
                log.error("[{}] Actor 初始化失败", actorId, e);
                destroy();
            }
        });
    }
    
    /**
     * 接收消息（普通优先级）
     */
    public void tell(MiniTbActorMsg msg) {
        enqueue(msg, false);
    }
    
    /**
     * 接收消息（高优先级）
     */
    public void tellWithHighPriority(MiniTbActorMsg msg) {
        enqueue(msg, true);
    }
    
    /**
     * 入队消息
     */
    private void enqueue(MiniTbActorMsg msg, boolean highPriority) {
        if (destroyed.get()) {
            log.warn("[{}] Actor 已销毁，消息被丢弃: {}", actorId, msg.getActorMsgType());
            msg.onActorStopped();
            return;
        }
        
        if (highPriority) {
            highPriorityQueue.offer(msg);
        } else {
            normalQueue.offer(msg);
        }
        
        // 触发处理
        tryProcess();
    }
    
    /**
     * 尝试处理消息
     * 使用 CAS 保证只有一个线程在处理
     */
    private void tryProcess() {
        if (processing.compareAndSet(false, true)) {
            executor.execute(this::processMessages);
        }
    }
    
    /**
     * 批量处理消息
     */
    private void processMessages() {
        try {
            int processed = 0;
            
            // 批量处理
            for (int i = 0; i < BATCH_SIZE; i++) {
                // 优先处理高优先级消息
                MiniTbActorMsg msg = highPriorityQueue.poll();
                if (msg == null) {
                    msg = normalQueue.poll();
                }
                
                if (msg == null) {
                    break; // 队列为空
                }
                
                try {
                    log.trace("[{}] 处理消息: {}", actorId, msg.getActorMsgType());
                    boolean handled = actor.process(msg);
                    if (!handled) {
                        log.warn("[{}] 未处理的消息类型: {}", actorId, msg.getActorMsgType());
                    }
                    processed++;
                } catch (Exception e) {
                    log.error("[{}] 处理消息异常: {}", actorId, msg.getActorMsgType(), e);
                }
            }
            
            if (processed > 0) {
                log.trace("[{}] 本批次处理了 {} 个消息", actorId, processed);
            }
            
        } finally {
            processing.set(false);
            
            // 如果队列还有消息，继续处理
            if (!highPriorityQueue.isEmpty() || !normalQueue.isEmpty()) {
                tryProcess();
            }
        }
    }
    
    /**
     * 销毁 Actor
     */
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    log.debug("[{}] 销毁 Actor...", actorId);
                    actor.destroy();
                    
                    // 清空队列，通知消息
                    MiniTbActorMsg msg;
                    while ((msg = highPriorityQueue.poll()) != null) {
                        msg.onActorStopped();
                    }
                    while ((msg = normalQueue.poll()) != null) {
                        msg.onActorStopped();
                    }
                    
                    log.debug("[{}] Actor 已销毁", actorId);
                } catch (Exception e) {
                    log.error("[{}] Actor 销毁失败", actorId, e);
                }
            });
        }
    }
    
    // ===== MiniTbActorContext 接口实现 =====
    
    @Override
    public void tell(String targetActorId, MiniTbActorMsg msg) {
        system.tell(targetActorId, msg);
    }
    
    @Override
    public void tellWithHighPriority(String targetActorId, MiniTbActorMsg msg) {
        system.tellWithHighPriority(targetActorId, msg);
    }
    
    @Override
    public String getSelf() {
        return actorId;
    }
    
    @Override
    public void stop(String targetActorId) {
        system.stop(targetActorId);
    }
    
    /**
     * 获取队列大小（用于监控）
     */
    public int getQueueSize() {
        return highPriorityQueue.size() + normalQueue.size();
    }
}

