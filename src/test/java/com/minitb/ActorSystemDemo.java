package com.minitb;

import com.minitb.actor.MiniTbActorMailbox;
import com.minitb.actor.MiniTbActorSystem;
import com.minitb.actor.device.DeviceActor;
import com.minitb.actor.msg.TransportToDeviceMsg;
import com.minitb.actor.ruleengine.RuleEngineActor;
import com.minitb.common.entity.Device;
import com.minitb.common.entity.DeviceId;
import com.minitb.common.entity.TenantId;
import com.minitb.ruleengine.RuleChain;
import com.minitb.ruleengine.RuleEngineService;
import com.minitb.ruleengine.node.FilterNode;
import com.minitb.ruleengine.node.LogNode;
import com.minitb.ruleengine.node.SaveTelemetryNode;
import com.minitb.storage.TelemetryStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Actor 系统演示程序
 * 
 * 演示内容:
 * 1. 创建 Actor 系统
 * 2. 创建设备 Actor 和规则引擎 Actor
 * 3. 模拟设备发送遥测数据
 * 4. 通过 Actor 系统异步处理
 * 5. 查看处理结果
 */
@Slf4j
public class ActorSystemDemo {
    
    public static void main(String[] args) throws InterruptedException {
        log.info("========================================");
        log.info("   MiniTB Actor 系统演示   ");
        log.info("========================================");
        
        // 1. 创建 Actor 系统（5个线程）
        log.info("\n[1/6] 创建 Actor 系统...");
        MiniTbActorSystem actorSystem = new MiniTbActorSystem(5);
        
        // 2. 初始化存储和规则引擎
        log.info("\n[2/6] 初始化规则引擎...");
        TelemetryStorage storage = new TelemetryStorage(true);
        RuleEngineService ruleEngineService = new RuleEngineService();
        
        RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
        rootRuleChain
                .addNode(new LogNode("入口日志"))
                .addNode(new FilterNode("temperature", 20.0))
                .addNode(new LogNode("过滤后日志"))
                .addNode(new SaveTelemetryNode(storage))
                .addNode(new LogNode("保存完成"));
        
        ruleEngineService.setRootRuleChain(rootRuleChain);
        
        // 3. 创建规则引擎 Actor
        log.info("\n[3/6] 创建规则引擎 Actor...");
        RuleEngineActor ruleEngineActor = new RuleEngineActor(ruleEngineService);
        MiniTbActorMailbox ruleEngineMailbox = actorSystem.createActor("RuleEngineActor", ruleEngineActor);
        
        // 4. 创建多个设备 Actor
        log.info("\n[4/6] 创建设备 Actor...");
        
        // 设备 1
        DeviceId device1Id = DeviceId.fromUUID(UUID.randomUUID());
        Device device1 = new Device(device1Id, TenantId.random(), "温度传感器-1");
        DeviceActor deviceActor1 = new DeviceActor(device1Id, device1);
        actorSystem.createActor(deviceActor1.getActorId(), deviceActor1);
        
        // 设备 2
        DeviceId device2Id = DeviceId.fromUUID(UUID.randomUUID());
        Device device2 = new Device(device2Id, TenantId.random(), "温度传感器-2");
        DeviceActor deviceActor2 = new DeviceActor(device2Id, device2);
        actorSystem.createActor(deviceActor2.getActorId(), deviceActor2);
        
        // 设备 3
        DeviceId device3Id = DeviceId.fromUUID(UUID.randomUUID());
        Device device3 = new Device(device3Id, TenantId.random(), "湿度传感器-1");
        DeviceActor deviceActor3 = new DeviceActor(device3Id, device3);
        actorSystem.createActor(deviceActor3.getActorId(), deviceActor3);
        
        log.info("创建了 3 个设备 Actor");
        
        // 5. 模拟设备发送数据
        log.info("\n[5/6] 模拟设备发送遥测数据...");
        
        // 设备1 发送10条消息（温度 15-24°C，部分会被过滤）
        for (int i = 0; i < 10; i++) {
            double temp = 15.0 + i;
            String payload = String.format("{\"temperature\":%.1f,\"humidity\":60}", temp);
            
            TransportToDeviceMsg msg = new TransportToDeviceMsg(
                    device1Id,
                    "test-token-001",
                    payload,
                    System.currentTimeMillis()
            );
            
            actorSystem.tell(deviceActor1.getActorId(), msg);
            Thread.sleep(10); // 模拟网络延迟
        }
        log.info("设备1 发送了 10 条消息");
        
        // 设备2 发送5条消息
        for (int i = 0; i < 5; i++) {
            double temp = 25.0 + i;
            String payload = String.format("{\"temperature\":%.1f,\"status\":\"online\"}", temp);
            
            TransportToDeviceMsg msg = new TransportToDeviceMsg(
                    device2Id,
                    "test-token-002",
                    payload,
                    System.currentTimeMillis()
            );
            
            actorSystem.tell(deviceActor2.getActorId(), msg);
            Thread.sleep(10);
        }
        log.info("设备2 发送了 5 条消息");
        
        // 设备3 发送8条消息
        for (int i = 0; i < 8; i++) {
            int humidity = 50 + i * 5;
            String payload = String.format("{\"humidity\":%d}", humidity);
            
            TransportToDeviceMsg msg = new TransportToDeviceMsg(
                    device3Id,
                    "test-token-003",
                    payload,
                    System.currentTimeMillis()
            );
            
            actorSystem.tell(deviceActor3.getActorId(), msg);
            Thread.sleep(10);
        }
        log.info("设备3 发送了 8 条消息");
        
        // 6. 等待处理完成
        log.info("\n[6/6] 等待消息处理完成...");
        Thread.sleep(2000);
        
        // 7. 查看系统状态
        log.info("\n" + actorSystem.getSystemInfo());
        
        // 8. 查看存储的数据
        log.info("\n=== 存储的遥测数据 ===");
        storage.printAllData();
        
        // 9. 关闭系统
        log.info("\n关闭 Actor 系统...");
        actorSystem.shutdown();
        
        log.info("\n演示完成！");
        
        // 总结
        log.info("\n=== 演示总结 ===");
        log.info("1. 创建了 1 个规则引擎 Actor + 3 个设备 Actor");
        log.info("2. 总共发送了 23 条消息（设备1: 10, 设备2: 5, 设备3: 8）");
        log.info("3. 所有消息都通过 Actor 系统异步处理");
        log.info("4. 每个设备的消息在独立的队列中串行处理");
        log.info("5. 规则链过滤掉了 temperature <= 20 的数据");
        log.info("6. 演示了 Actor 模式的隔离性、异步性、并发控制");
    }
}

