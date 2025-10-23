# MiniTB - ThingsBoard 核心数据流简化实现

这是ThingsBoard核心数据流的精简实现（~1000行代码 vs ThingsBoard的10万+行），用于理解物联网平台的核心架构。

## 🎯 核心数据流

```
设备 → MQTT传输层 → TransportService → TbMsg → Rule Engine → 数据存储
```

## 🚀 快速开始

```bash
# 1. 编译并启动
cd minitb
./run.sh

# 2. 发送测试数据（另一个终端）
./test-mqtt.sh

# 或手动发送：
mosquitto_pub -h localhost -p 1883 -u test-token-001 \
  -t v1/devices/me/telemetry -m '{"temperature":25,"humidity":60}'

# 3. 查看保存的数据
tail -f data/telemetry_*.log
```

## 📁 项目结构

```
minitb/
├── src/main/java/com/minitb/
│   ├── common/                          # 公共模块
│   │   ├── data/                        # Device, DeviceId, TenantId
│   │   └── msg/                         # TbMsg, TbMsgType
│   ├── transport/                       # 传输层
│   │   ├── mqtt/                        # MqttTransportHandler, MqttTransportService
│   │   └── service/                     # TransportService
│   ├── ruleengine/                      # 规则引擎
│   │   ├── node/                        # LogNode, FilterNode, SaveTelemetryNode
│   │   ├── RuleChain.java
│   │   └── RuleEngineService.java
│   └── storage/                         # TelemetryStorage
└── MiniTBApplication.java               # 主程序
```

## 🌊 数据流详解

### 完整流程
```
1. 设备发送MQTT消息
   Topic: v1/devices/me/telemetry
   Payload: {"temperature":25}
   Token: test-token-001
   
2. MqttTransportHandler 接收并解析
   
3. TransportService 处理
   • authenticateDevice()      认证设备
   • checkRateLimit()          限流检查
   • 创建TbMsg对象            🔥核心转换
   • sendToRuleEngine()        转发
   
4. RuleEngineService 路由
   • 选择规则链
   • 异步提交处理
   
5. RuleChain 执行规则链
   LogNode → FilterNode → SaveTelemetryNode → LogNode
   
6. TelemetryStorage 持久化
   • 内存Map存储
   • 文件备份
```

### 核心对象: TbMsg

```java
TbMsg {
    UUID id;                    // 消息唯一ID
    TbMsgType type;            // POST_TELEMETRY_REQUEST
    DeviceId originator;       // 设备ID
    Map<String,String> metaData; // 元数据
    String data;               // JSON数据
    long timestamp;            // 时间戳
}
```

**TbMsg是整个数据流的载体**，从传输层流向规则引擎再到存储层。

## 🎓 核心概念

### 1. 消息驱动架构
- 所有数据都转换为 TbMsg
- TbMsg 在各层之间流转
- 统一的处理流程

### 2. 责任链模式
```java
RuleChain rootChain = new RuleChain("Root")
    .addNode(new LogNode("入口"))
    .addNode(new FilterNode("temperature", 20.0))
    .addNode(new SaveTelemetryNode(storage));
```

### 3. 分层解耦
- **传输层**: 只管协议解析
- **服务层**: 只管认证和转换  
- **引擎层**: 只管业务逻辑
- **存储层**: 只管数据持久化

## 📊 与ThingsBoard对比

| 组件 | MiniTB | ThingsBoard |
|------|--------|-------------|
| 代码量 | ~1000行 | ~100,000行 |
| 传输协议 | MQTT | MQTT+HTTP+CoAP+LWM2M+SNMP |
| 消息队列 | 内存 | Kafka/RabbitMQ |
| Actor系统 | 线程池 | 完整Actor模型 |
| 存储 | 内存+文件 | Cassandra/PostgreSQL |
| 规则节点 | 3个 | 50+ |

**MiniTB保留了核心设计，去除了生产环境的复杂性。**

## 💡 学习路径

1. 运行项目，观察日志输出
2. 阅读 `MiniTBApplication.main()` 理解初始化
3. 跟踪 `TransportService.processTelemetry()` 理解数据转换
4. 查看 `RuleChain.process()` 理解规则执行
5. 对比ThingsBoard源码，理解区别

## 🔧 自定义规则节点

创建自己的规则节点：

```java
public class AlarmNode implements RuleNode {
    public TbMsg onMsg(TbMsg msg) {
        // 解析数据，检查告警条件
        // 创建告警
        return msg;
    }
}

// 在MiniTBApplication中添加：
rootRuleChain.addNode(new AlarmNode());
```

---

**16个Java文件，~1000行代码，完整实现ThingsBoard核心数据流！**

