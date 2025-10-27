package com.minitb.configuration;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.infrastructure.rule.FilterNode;
import com.minitb.infrastructure.rule.LogNode;
import com.minitb.domain.rule.RuleChain;
import com.minitb.infrastructure.rule.SaveTelemetryNode;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.storage.TelemetryStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MiniTB 核心配置类
 * 
 * 职责：
 * - 配置非 Spring 管理的组件为 Spring Bean
 * - 初始化 Actor 系统、规则引擎、存储层
 */
@Configuration
@Slf4j
public class MiniTBConfiguration {
    
    /**
     * 遥测数据存储
     */
    @Bean
    public TelemetryStorage telemetryStorage() {
        log.info("初始化遥测数据存储...");
        return new TelemetryStorage(true);
    }
    
    /**
     * 规则引擎服务
     */
    @Bean
    public RuleEngineService ruleEngineService(TelemetryStorage storage) {
        log.info("初始化规则引擎服务...");
        RuleEngineService service = new RuleEngineService();
        
        // 创建根规则链
        RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
        rootRuleChain
                .addNode(new LogNode("入口日志"))
                .addNode(new FilterNode("temperature", 20.0))
                .addNode(new LogNode("过滤后日志"))
                .addNode(new SaveTelemetryNode(storage))
                .addNode(new LogNode("保存完成"));
        
        service.setRootRuleChain(rootRuleChain);
        service.printRuleChains();
        
        return service;
    }
    
    /**
     * Actor 系统
     */
    @Bean
    public MiniTbActorSystem actorSystem() {
        log.info("初始化 Actor 系统（线程池大小: 5）...");
        return new MiniTbActorSystem(5);
    }
}


