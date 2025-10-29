# MiniTB - 轻量级物联网数据平台

MiniTB 是一个基于 **Spring Boot + Actor 模型 + 六边形架构** 的轻量级物联网（IoT）数据采集与处理平台，采用 **DDD（领域驱动设计）**，实现了 ThingsBoard 的核心数据流。

**核心特点**: Spring Boot 3.2 | Actor 异步架构 | 六边形架构 | 强类型数据系统 | 告警引擎 | Prometheus/IPMI 数据源 | 规则链路由 | 完整测试覆盖

---

## 📋 目录

- [快速开始](#-快速开始)
- [核心概念](#-核心概念)
  - [领域模型](#1-领域模型)
  - [核心组件](#2-核心组件)
  - [数据流程](#3-数据流程)
- [完整示例](#-完整示例prometheus-监控流程)
- [多设备场景](#-多设备场景核心组件协作)
- [项目结构](#-项目结构)
- [测试](#-测试)
- [技术栈](#-技术栈)

---

## 🚀 快速开始

### 环境要求

```bash
# Java 17（必须）
java -version

# Maven 3.6+
mvn -version
```

### 启动应用

```bash
# 1. 克隆并编译
cd minitb
mvn clean install

# 2. 启动
mvn spring-boot:run

# 或使用启动脚本
./start-gpu-monitor.sh
```

### 访问

- **Web 界面**: http://localhost:8080
- **设备 API**: http://localhost:8080/api/devices
- **遥测 API**: http://localhost:8080/api/telemetry/{deviceId}/latest
- **告警 API**: http://localhost:8080/api/alarms/device/{deviceId}

---

## 🧩 核心概念

### 1. 领域模型

MiniTB 的领域模型遵循 DDD 设计，核心实体和它们的关系如下：

```
┌─────────────────────────────────────────────────────────────────┐
│                      领域模型关系图                               │
└─────────────────────────────────────────────────────────────────┘

DeviceProfile (设备配置模板)
├── id: DeviceProfileId
├── name: String
├── telemetryDefinitions: List<TelemetryDefinition>  ──┐
├── alarmRules: List<AlarmRule>                        │
├── defaultRuleChainId: RuleChainId                    │ 定义
├── defaultQueueName: String                           │
└── dataSourceType: PROMETHEUS | MQTT | HTTP           │
                                                       │
                                                       ↓
Device (设备实例)                              TelemetryDefinition
├── id: DeviceId                               ├── key: String
├── name: String                               ├── displayName: String
├── type: String                               ├── dataType: DataType
├── deviceProfileId: DeviceProfileId ─────┐    ├── unit: String
├── accessToken: String (MQTT/HTTP)       │    └── protocolConfig: ProtocolConfig
└── configuration: DeviceConfiguration    │            ├── PrometheusConfig
         ├── PrometheusDeviceConfiguration│            │    └── promQL: String
         └── IpmiDeviceConfiguration      │            ├── MqttConfig
                                          │            └── HttpConfig
                                          │
                    关联                   │
                                          │
RuleChain (规则链)                          │
├── id: RuleChainId                        │
├── name: String                           │
└── nodes: List<RuleNode> ─────────────────┤
         ├── LogNode                       │
         ├── FilterNode                    │
         ├── SaveTelemetryNode             │
         └── AlarmEvaluatorNode            │
                                          │
Message (消息)                              │
├── id: UUID                               │
├── type: MessageType                      │
├── originator: DeviceId ──────────────────┘
├── tsKvEntries: List<TsKvEntry>
├── ruleChainId: String
└── queueName: String

TsKvEntry (时间序列数据点)
├── key: String
├── ts: long (timestamp)
├── dataType: DataType
└── value: Boolean | Long | Double | String | JSON

Alarm (告警)
├── id: AlarmId
├── originator: DeviceId
├── type: String
├── severity: CRITICAL | MAJOR | MINOR | WARNING
├── status: ACTIVE_UNACK | ACTIVE_ACK | CLEARED_UNACK | CLEARED_ACK
└── propagate: boolean
```

#### 关键关系说明

1. **DeviceProfile → Device** (1:N)
   - 一个 `DeviceProfile` 可以被多个 `Device` 使用
   - `Device` 通过 `deviceProfileId` 关联到 `DeviceProfile`
   - `DeviceProfile` 定义了设备的通用配置（遥测指标、告警规则、规则链）

2. **DeviceProfile → TelemetryDefinition** (1:N)
   - `DeviceProfile` 包含多个 `TelemetryDefinition`
   - 每个 `TelemetryDefinition` 定义一个监控指标（如 CPU 使用率、温度）
   - `ProtocolConfig` 定义了如何获取该指标（Prometheus PromQL、MQTT Topic 等）

3. **DeviceProfile → RuleChain** (N:1)
   - `DeviceProfile` 通过 `defaultRuleChainId` 指定默认规则链
   - 来自该 Profile 设备的消息会路由到指定的规则链
   - 如果为 null，则使用 Root Rule Chain

4. **Device → Message** (1:N)
   - 每个设备会产生多个消息
   - `Message.originator` 指向设备 ID
   - `Message.ruleChainId` 来自设备的 `DeviceProfile.defaultRuleChainId`

5. **Message → TsKvEntry** (1:N)
   - 一个消息包含多个时间序列数据点
   - 每个 `TsKvEntry` 是一个强类型的键值对（key + timestamp + typed value）

6. **DeviceProfile → AlarmRule** (1:N)
   - `DeviceProfile` 定义多个告警规则
   - `AlarmRule` 指定触发条件（简单/持续/重复）和告警级别
   - 告警评估器根据这些规则生成 `Alarm`

7. **Device → Alarm** (1:N)
   - 一个设备可以产生多个告警
   - `Alarm.originator` 指向设备 ID
   - 告警有状态机（未确认 → 已确认 → 已清除）

### 2. 核心组件

MiniTB 的核心组件及其职责：

```
┌─────────────────────────────────────────────────────────────────┐
│                      核心组件架构                                 │
└─────────────────────────────────────────────────────────────────┘

                    应用层 (Application Layer)
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  DeviceService                    AlarmService                  │
│  ├── 设备 CRUD                     ├── 告警管理                  │
│  ├── DeviceProfile CRUD           ├── 确认/清除                 │
│  └── 设备查询                      └── 统计查询                  │
│                                                                 │
│  DataInitializer                  AlarmEvaluator                │
│  └── 初始化示例数据                 └── 告警条件评估               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 调用
┌─────────────────────────────────────────────────────────────────┐
│                    Actor 系统 (Concurrency)                      │
│                                                                 │
│  MiniTbActorSystem (Actor 容器)                                 │
│  └── actorMap: Map<ActorId, MiniTbActor>                       │
│                                                                 │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │  DeviceActor    │  │ RuleEngineActor  │  │ RuleChainActor│ │
│  │  (每设备一个)    │  │  (全局单例)       │  │ (每规则链一个) │ │
│  ├─────────────────┤  ├──────────────────┤  ├───────────────┤ │
│  │ • 接收遥测数据   │  │ • 接收消息        │  │ • 执行规则链   │ │
│  │ • JSON→TsKvEntry│  │ • 路由到RuleChain │  │ • 处理消息     │ │
│  │ • 发送到规则引擎 │  │ • 负载均衡        │  │ • 并行处理     │ │
│  └─────────────────┘  └──────────────────┘  └───────────────┘ │
│         ↑                     ↑                      ↑         │
│         │                     │                      │         │
└─────────┼─────────────────────┼──────────────────────┼─────────┘
          │                     │                      │
          │ TransportToDeviceMsg│ ToRuleEngineMsg      │ ToRuleChainMsg
          │                     │                      │
┌─────────┴─────────────────────┴──────────────────────┴─────────┐
│                    传输层 (Transport)                            │
│                                                                 │
│  TransportService                 PrometheusDataPuller         │
│  ├── MQTT 数据接收                 ├── 定时拉取 (@Scheduled)     │
│  ├── 根据 token 查找设备           ├── 根据 DeviceProfile 配置   │
│  └── 创建 DeviceActor              └── 调用 TransportService    │
│                                                                 │
│  MqttTransportService            IpmiDataPuller                │
│  └── Netty MQTT 服务器            └── IPMI 传感器数据拉取        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   规则引擎 (Rule Engine)                          │
│                                                                 │
│  RuleEngineService                                              │
│  ├── 管理所有 RuleChain                                          │
│  ├── 根据 Message.ruleChainId 路由                              │
│  └── 创建 RuleChainActor                                        │
│                                                                 │
│  RuleChain (规则链)                                              │
│  └── nodes: RuleNode (责任链模式)                                │
│       ├── LogNode (日志)                                        │
│       ├── FilterNode (过滤)                                     │
│       ├── SaveTelemetryNode (保存遥测)                          │
│       └── AlarmEvaluatorNode (告警评估)                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    存储层 (Storage)                              │
│                                                                 │
│  TelemetryStorage (内存时序数据库)                               │
│  └── Map<DeviceId, Map<Key, List<TsKvEntry>>>                  │
│                                                                 │
│  DeviceRepository (SQLite/JPA)                                  │
│  └── Device 持久化                                              │
│                                                                 │
│  AlarmRepository (SQLite)                                       │
│  └── Alarm 持久化                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 组件职责详解

##### **1. DeviceService (设备服务)**
- **职责**: 管理设备和设备配置模板的生命周期
- **主要方法**:
  - `save(Device)` / `findById(DeviceId)`: 设备 CRUD
  - `saveProfile(DeviceProfile)` / `findProfileById(DeviceProfileId)`: 配置模板 CRUD
  - `findByAccessToken(String)`: 根据访问令牌查找设备（用于 MQTT/HTTP 认证）
- **调用关系**: 被 TransportService、DataInitializer、REST API 调用

##### **2. MiniTbActorSystem (Actor 系统)**
- **职责**: 管理所有 Actor 的生命周期和消息传递
- **特点**:
  - 每个 Actor 有独立的消息队列（Mailbox）
  - 串行处理消息，避免并发问题
  - 线程池执行，支持高并发
- **Actor 类型**:
  - `DeviceActor`: 一个设备对应一个 Actor，负责该设备的数据处理
  - `RuleEngineActor`: 全局单例，协调消息路由到不同的 RuleChain
  - `RuleChainActor`: 一个规则链对应一个 Actor，隔离不同规则链的执行

##### **3. TransportService (传输服务)**
- **职责**: 接收外部数据并转换为内部消息
- **数据来源**:
  - **MQTT**: 设备通过 MQTT 推送（使用 accessToken 认证）
  - **Prometheus**: 定时拉取（PrometheusDataPuller）
  - **IPMI**: 定时拉取（IpmiDataPuller）
- **处理流程**:
  1. 接收数据（JSON 格式）
  2. 根据 accessToken 或设备配置查找 Device
  3. 创建 `TransportToDeviceMsg`
  4. 发送到对应的 `DeviceActor`

##### **4. RuleEngineService (规则引擎服务)**
- **职责**: 管理规则链，路由消息
- **关键逻辑**:
  ```java
  // 根据 DeviceProfile 的 defaultRuleChainId 路由消息
  RuleChain selectRuleChain(Message msg) {
      if (msg.getRuleChainId() != null) {
          return ruleChains.get(msg.getRuleChainId());
      }
      return rootRuleChain; // 默认根规则链
  }
  ```
- **RuleChainActor 创建**:
  - 每个 RuleChain 对应一个 RuleChainActor
  - 实现规则链之间的并行处理和隔离

##### **5. RuleChain (规则链)**
- **职责**: 定义数据处理流程（责任链模式）
- **节点类型**:
  - `LogNode`: 记录日志
  - `FilterNode`: 根据条件过滤消息
  - `SaveTelemetryNode`: 保存遥测数据到 TelemetryStorage
  - `AlarmEvaluatorNode`: 评估告警规则，生成告警
- **执行方式**: 串行执行，节点通过 `next` 指针连接

##### **6. TelemetryStorage (遥测存储)**
- **职责**: 内存中的时间序列数据库
- **数据结构**:
  ```
  Map<DeviceId, Map<String, List<TsKvEntry>>>
       设备ID      键名      时间序列数据点
  ```
- **查询能力**: 最新值、范围查询、聚合统计

### 3. 数据流程

从数据采集到存储的完整流程：

```
┌─────────────────────────────────────────────────────────────────┐
│                     数据流完整时序图                              │
└─────────────────────────────────────────────────────────────────┘

外部数据源                                     MiniTB 系统
───────────                                    ─────────────

Prometheus Server                              
(GPU 指标)                                      PrometheusDataPuller
    │                                                 │
    │ PromQL 查询                                     │ @Scheduled(2秒)
    │ DCGM_FI_DEV_GPU_UTIL                           │
    ├────────────────────────────────────────────────┤
    │                                                 │
    │ 返回: {gpu="0", value=100}                      │
    │       {gpu="1", value=98}                       │
    │                                                 ↓
    │                                           1. 根据 DeviceProfile
    │                                              查找遥测定义
    │                                           2. 执行 PromQL 查询
    │                                           3. 根据 prometheusLabel
    │                                              过滤结果
    │                                           4. 构造 JSON
    │                                              {"gpu_utilization": 100}
    │                                                 ↓
    │                                           TransportService
    │                                                 │
    │                                                 │ processTelemetry()
    │                                                 ↓
    │                                           根据 accessToken
    │                                           查找 Device
    │                                                 ↓
    │                                           创建 TransportToDeviceMsg
    │                                                 │
    │                                                 │ tell()
    │                                                 ↓
    │                                           DeviceActor (异步)
    │                                           ┌─────────────────┐
    │                                           │ 消息队列 (Mailbox)│
    │                                           └────────┬────────┘
    │                                                    │ 串行处理
    │                                                    ↓
    │                                           1. 解析 JSON
    │                                              → List<TsKvEntry>
    │                                           2. 创建 Message
    │                                              (带 ruleChainId)
    │                                           3. 发送到 RuleEngineActor
    │                                                    ↓
    │                                           RuleEngineActor
    │                                                    │
    │                                                    │ 根据 Message.ruleChainId
    │                                                    │ 选择 RuleChain
    │                                                    ↓
    │                                           创建 ToRuleChainMsg
    │                                                    │
    │                                                    │ tell()
    │                                                    ↓
    │                                           RuleChainActor (异步)
    │                                           ┌─────────────────┐
    │                                           │ 规则链: GPU Chain│
    │                                           └────────┬────────┘
    │                                                    │
    │                                                    ↓
    │                                           执行规则链节点
    │                                           ┌─────────────────┐
    │                                           │ 1. LogNode      │
    │                                           │    记录入口日志  │
    │                                           └────────┬────────┘
    │                                                    ↓
    │                                           ┌─────────────────┐
    │                                           │ 2. FilterNode   │
    │                                           │    过滤低值数据  │
    │                                           └────────┬────────┘
    │                                                    ↓
    │                                           ┌─────────────────┐
    │                                           │ 3. AlarmEvaluatorNode│
    │                                           │    评估告警规则  │
    │                                           │    (温度>80°C?)  │
    │                                           └────────┬────────┘
    │                                                    ↓
    │                                           ┌─────────────────┐
    │                                           │ 4. SaveTelemetryNode│
    │                                           │    保存到 Storage│
    │                                           └────────┬────────┘
    │                                                    ↓
    │                                           TelemetryStorage
    │                                           (内存时序数据库)
    │                                           ┌─────────────────┐
    │                                           │ Map<DeviceId,   │
    │                                           │   Map<Key,      │
    │                                           │     List<Entry>>>│
    │                                           └─────────────────┘
                                                        ↓
                                                REST API 查询
                                                /api/telemetry/{id}/latest
                                                        ↓
                                                Web 前端 (Chart.js)
                                                实时图表更新
```

---

## 📖 完整示例：Prometheus 监控流程

以监控一块 GPU 为例，展示从配置到数据展示的完整流程。

### 场景描述

- **设备**: NVIDIA GPU
- **数据源**: Prometheus + DCGM Exporter
- **监控指标**: GPU 利用率、温度、功耗
- **采集频率**: 每 2 秒
- **告警规则**: 温度 > 80°C 触发告警

### 第 1 步：定义 DeviceProfile

```java
// DataInitializer.java
DeviceProfile gpuProfile = DeviceProfile.builder()
    .id(DeviceProfileId.random())
    .name("GPU 监控配置")
    .description("NVIDIA GPU 监控 (DCGM)")
    
    // 数据源类型
    .dataSourceType(DataSourceType.PROMETHEUS)
    
    // Prometheus 设备标签 key（用于区分不同 GPU）
    .prometheusDeviceLabelKey("gpu")
    
    // 定义 3 个遥测指标
    .telemetryDefinitions(Arrays.asList(
        // 指标 1: GPU 利用率
        TelemetryDefinition.builder()
            .key("gpu_utilization")
            .displayName("GPU利用率")
            .dataType(DataType.LONG)
            .unit("%")
            .protocolConfig(PrometheusConfig.builder()
                .promQL("DCGM_FI_DEV_GPU_UTIL")  // PromQL 查询
                .build())
            .build(),
        
        // 指标 2: GPU 温度
        TelemetryDefinition.builder()
            .key("gpu_temperature")
            .displayName("GPU温度")
            .dataType(DataType.LONG)
            .unit("°C")
            .protocolConfig(PrometheusConfig.builder()
                .promQL("DCGM_FI_DEV_GPU_TEMP")
                .build())
            .build(),
        
        // 指标 3: 功耗
        TelemetryDefinition.builder()
            .key("power_usage")
            .displayName("功耗")
            .dataType(DataType.DOUBLE)
            .unit("W")
            .protocolConfig(PrometheusConfig.builder()
                .promQL("DCGM_FI_DEV_POWER_USAGE")
                .build())
            .build()
    ))
    
    // 定义告警规则
    .alarmRules(Arrays.asList(
        AlarmRule.builder()
            .alarmType("高温告警")
            // 创建条件: 温度 > 80
            .createConditions(Map.of(
                AlarmSeverity.MAJOR,
                AlarmCondition.builder()
                    .condition(Arrays.asList(
                        AlarmConditionFilter.builder()
                            .key("gpu_temperature")
                            .operator(FilterOperator.GREATER_THAN)
                            .value("80")
                            .build()
                    ))
                    .spec(AlarmConditionSpec.builder()
                        .type(AlarmConditionType.SIMPLE)
                        .build())
                    .build()
            ))
            // 清除条件: 温度 <= 75
            .clearCondition(AlarmCondition.builder()
                .condition(Arrays.asList(
                    AlarmConditionFilter.builder()
                        .key("gpu_temperature")
                        .operator(FilterOperator.LESS_OR_EQUAL)
                        .value("75")
                        .build()
                ))
                .spec(AlarmConditionSpec.builder()
                    .type(AlarmConditionType.SIMPLE)
                    .build())
                .build())
            .build()
    ))
    
    // 规则链配置（如果不指定，使用 Root Rule Chain）
    .defaultRuleChainId(null)
    .defaultQueueName("Main")
    
    .build();

// 保存到数据库
deviceService.saveProfile(gpuProfile);
```

**关键配置说明**:
- `prometheusDeviceLabelKey = "gpu"`: 表示 Prometheus 结果中的 `gpu` 标签用于区分设备
- `TelemetryDefinition`: 每个指标定义了如何查询（PromQL）和如何存储（key, dataType）
- `AlarmRule`: 定义触发条件和清除条件

### 第 2 步：创建 Device 实例

```java
// 创建 GPU 0
Device gpu0 = Device.builder()
    .id(DeviceId.random())
    .name("NVIDIA GPU 0")
    .type("NVIDIA_GPU")
    .deviceProfileId(gpuProfile.getId())  // 关联配置模板
    
    // 访问令牌（用于 MQTT/HTTP）
    .accessToken("gpu-0-token")
    
    // Prometheus 连接配置
    .configuration(PrometheusDeviceConfiguration.builder()
        .endpoint("http://192.168.30.134:9090")  // Prometheus 地址
        .label("gpu=0")  // 标签过滤：只接收 gpu="0" 的数据
        .build())
    
    .createdTime(System.currentTimeMillis())
    .build();

deviceService.save(gpu0);
```

**Prometheus 标签映射原理**:
```
Prometheus 查询结果:
DCGM_FI_DEV_GPU_UTIL{gpu="0", instance="..."}  = 100
DCGM_FI_DEV_GPU_UTIL{gpu="1", instance="..."}  = 98

MiniTB 过滤:
- GPU 0 (label="gpu=0") → 只接收 gpu="0" 的数据 (100)
- GPU 1 (label="gpu=1") → 只接收 gpu="1" 的数据 (98)
```

### 第 3 步：自动数据采集

系统启动后，`PrometheusDataPuller` 会自动定时拉取数据：

```java
@Component
public class PrometheusDataPuller {
    
    @Scheduled(fixedRate = 2000, initialDelay = 5000)  // 每 2 秒
    public void pullAllPrometheusDevices() {
        // 1. 查找所有 Prometheus 类型的设备
        List<Device> prometheusDevices = deviceService.findAll().stream()
            .filter(d -> d.getConfiguration() instanceof PrometheusDeviceConfiguration)
            .collect(Collectors.toList());
        
        // 2. 对每个设备
        for (Device device : prometheusDevices) {
            PrometheusDeviceConfiguration config = 
                (PrometheusDeviceConfiguration) device.getConfiguration();
            
            // 3. 获取设备的 DeviceProfile
            DeviceProfile profile = deviceService
                .findProfileById(device.getDeviceProfileId())
                .orElseThrow();
            
            // 4. 对每个遥测指标执行查询
            for (TelemetryDefinition telemetryDef : profile.getTelemetryDefinitions()) {
                PrometheusConfig promConfig = 
                    (PrometheusConfig) telemetryDef.getProtocolConfig();
                
                // 5. 执行 PromQL 查询
                String promQL = promConfig.getPromQL();  // "DCGM_FI_DEV_GPU_UTIL"
                List<PrometheusQueryResult> results = 
                    queryPrometheus(config.getEndpoint(), promQL);
                
                // 6. 根据标签过滤结果
                String labelFilter = config.getLabel();  // "gpu=0"
                Optional<PrometheusQueryResult> matchedResult = results.stream()
                    .filter(r -> matchesLabel(r.getMetric(), labelFilter))
                    .findFirst();
                
                // 7. 构造 JSON 遥测数据
                if (matchedResult.isPresent()) {
                    Map<String, Object> telemetryData = new HashMap<>();
                    telemetryData.put(
                        telemetryDef.getKey(),  // "gpu_utilization"
                        matchedResult.get().getValue()  // 100
                    );
                    
                    String json = objectMapper.writeValueAsString(telemetryData);
                    // {"gpu_utilization": 100}
                    
                    // 8. 发送到 TransportService（进入正常数据流）
                    transportService.processTelemetry(
                        device.getAccessToken(),  // "gpu-0-token"
                        json
                    );
                }
            }
        }
    }
}
```

**数据流转**:
```
Prometheus 查询
  ↓
PrometheusDataPuller (定时任务)
  ↓
TransportService.processTelemetry(token, json)
  ↓
DeviceActor (异步处理)
  ↓
RuleEngineActor
  ↓
RuleChainActor (执行规则链)
  ↓
SaveTelemetryNode
  ↓
TelemetryStorage (内存时序数据库)
```

### 第 4 步：Actor 系统处理

#### 4.1 TransportService 接收数据

```java
@Component
public class TransportService {
    
    public void processTelemetry(String accessToken, String payload) {
        // 1. 根据 token 查找设备
        Device device = deviceService.findByAccessToken(accessToken)
            .orElseThrow(() -> new RuntimeException("Device not found"));
        
        log.info("接收到遥测数据: device={}, payload={}", device.getName(), payload);
        
        // 2. 创建消息
        TransportToDeviceMsg msg = TransportToDeviceMsg.builder()
            .deviceId(device.getId())
            .payload(payload)  // {"gpu_utilization": 100}
            .build();
        
        // 3. 发送到 DeviceActor（异步）
        String actorId = DeviceActor.actorIdFor(device.getId());
        actorSystem.tell(actorId, msg);
        
        log.debug("消息已发送到 DeviceActor: {}", actorId);
    }
}
```

#### 4.2 DeviceActor 处理

```java
public class DeviceActor implements MiniTbActor {
    
    private final DeviceId deviceId;
    private final Device device;
    private final DeviceProfile deviceProfile;
    
    @Override
    public boolean process(MiniTbActorMsg msg) {
        if (msg.getActorMsgType() == ActorMsgType.TRANSPORT_TO_DEVICE_MSG) {
            onTransportMsg((TransportToDeviceMsg) msg);
            return true;
        }
        return false;
    }
    
    private void onTransportMsg(TransportToDeviceMsg msg) {
        // 1. 解析 JSON → List<TsKvEntry>
        String payload = msg.getPayload();  // {"gpu_utilization": 100}
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        
        List<TsKvEntry> tsKvEntries = new ArrayList<>();
        long ts = System.currentTimeMillis();
        
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();  // "gpu_utilization"
            JsonElement value = entry.getValue();  // 100
            
            // 推断数据类型并创建 TsKvEntry
            TsKvEntry tsKvEntry = createTsKvEntry(key, value, ts);
            tsKvEntries.add(tsKvEntry);
        }
        
        log.debug("[{}] 解析遥测数据: {} 个数据点", deviceId, tsKvEntries.size());
        
        // 2. 创建 Message
        Message.MessageBuilder builder = Message.builder()
            .id(UUID.randomUUID())
            .type(MessageType.POST_TELEMETRY_REQUEST)
            .originator(deviceId)
            .tsKvEntries(tsKvEntries)
            .timestamp(System.currentTimeMillis());
        
        // 3. 从 DeviceProfile 获取规则链配置
        if (deviceProfile != null) {
            if (deviceProfile.getDefaultRuleChainId() != null) {
                builder.ruleChainId(deviceProfile.getDefaultRuleChainId().toString());
            }
            if (deviceProfile.getDefaultQueueName() != null) {
                builder.queueName(deviceProfile.getDefaultQueueName());
            }
        }
        
        Message tbMsg = builder.build();
        
        // 4. 发送到 RuleEngineActor
        ctx.tell("RuleEngineActor", new ToRuleEngineMsg(tbMsg));
        
        log.debug("[{}] 消息已发送到规则引擎", deviceId);
    }
    
    private TsKvEntry createTsKvEntry(String key, JsonElement value, long ts) {
        // 根据 JSON 类型推断数据类型
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return new BasicTsKvEntry(ts, new BooleanDataEntry(key, primitive.getAsBoolean()));
            } else if (primitive.isNumber()) {
                double d = primitive.getAsDouble();
                if (d == Math.floor(d)) {
                    return new BasicTsKvEntry(ts, new LongDataEntry(key, (long) d));
                } else {
                    return new BasicTsKvEntry(ts, new DoubleDataEntry(key, d));
                }
            } else {
                return new BasicTsKvEntry(ts, new StringDataEntry(key, primitive.getAsString()));
            }
        }
        return new BasicTsKvEntry(ts, new JsonDataEntry(key, value.toString()));
    }
}
```

**关键点**:
- **异步处理**: 消息进入 DeviceActor 的 Mailbox，串行处理
- **类型推断**: 自动将 JSON 转换为强类型 TsKvEntry（Long/Double/String）
- **规则链路由**: 从 DeviceProfile 读取 `defaultRuleChainId`，设置到 Message

#### 4.3 RuleEngineActor 路由

```java
public class RuleEngineActor implements MiniTbActor {
    
    private final RuleEngineService ruleEngineService;
    
    @Override
    public boolean process(MiniTbActorMsg msg) {
        if (msg.getActorMsgType() == ActorMsgType.TO_RULE_ENGINE_MSG) {
            onToRuleEngineMsg((ToRuleEngineMsg) msg);
            return true;
        }
        return false;
    }
    
    private void onToRuleEngineMsg(ToRuleEngineMsg msg) {
        log.debug("规则引擎收到消息: originator={}, type={}", 
            msg.getMessage().getOriginator(), msg.getMessage().getType());
        
        // 委托给 RuleEngineService 处理（异步）
        ruleEngineService.processMessage(msg.getMessage());
    }
}
```

#### 4.4 RuleEngineService 选择 RuleChain

```java
@Slf4j
public class RuleEngineService {
    
    private final Map<String, RuleChain> ruleChains = new ConcurrentHashMap<>();
    private RuleChain rootRuleChain;
    private RuleChainId rootRuleChainId;
    private MiniTbActorSystem actorSystem;
    
    public void processMessage(Message msg) {
        // 1. 选择规则链
        RuleChain targetRuleChain = selectRuleChain(msg);
        
        if (targetRuleChain == null) {
            log.warn("未找到合适的规则链: {}", msg.getId());
            return;
        }
        
        // 2. 获取规则链 ID
        RuleChainId targetRuleChainId = getRuleChainId(msg, targetRuleChain);
        
        // 3. 路由到 RuleChainActor
        String actorId = RuleChainActor.actorIdFor(targetRuleChainId);
        actorSystem.tell(actorId, new ToRuleChainMsg(msg));
        
        log.debug("消息已路由到 RuleChain: {} [{}]", 
            targetRuleChain.getName(), actorId);
    }
    
    private RuleChain selectRuleChain(Message msg) {
        // 优先使用消息中指定的规则链
        if (msg.getRuleChainId() != null && !msg.getRuleChainId().isEmpty()) {
            RuleChain chain = ruleChains.get(msg.getRuleChainId());
            if (chain != null) {
                return chain;
            }
        }
        
        // 否则使用根规则链
        return rootRuleChain;
    }
}
```

**规则链路由逻辑**:
```
如果 Message.ruleChainId 不为空
  ├─ 使用指定的规则链
  └─ 如果不存在，fallback 到 Root Rule Chain
否则
  └─ 使用 Root Rule Chain
```

### 第 5 步：规则链执行

#### 5.1 RuleChainActor 处理

```java
public class RuleChainActor implements MiniTbActor {
    
    private final RuleChainId ruleChainId;
    private final RuleChain ruleChain;
    
    @Override
    public boolean process(MiniTbActorMsg msg) {
        if (msg.getActorMsgType() == ActorMsgType.TO_RULE_CHAIN_MSG) {
            onToRuleChainMsg((ToRuleChainMsg) msg);
            return true;
        }
        return false;
    }
    
    private void onToRuleChainMsg(ToRuleChainMsg msg) {
        log.debug("[{}] 收到消息，开始处理: deviceId={}", 
            ruleChainId, msg.getMessage().getOriginator());
        
        try {
            // 执行规则链
            ruleChain.process(msg.getMessage());
        } catch (Exception e) {
            log.error("[{}] 规则链处理失败", ruleChainId, e);
        }
    }
}
```

#### 5.2 RuleChain 节点执行

```java
public class RuleChain {
    
    private final String name;
    private final List<RuleNode> nodes;
    
    public void process(Message msg) {
        log.debug("[{}] 开始处理消息: originator={}", 
            name, msg.getOriginator());
        
        // 依次执行节点（责任链模式）
        for (RuleNode node : nodes) {
            try {
                node.onMsg(msg, context);
            } catch (Exception e) {
                log.error("[{}] 节点执行失败: {}", name, node.getName(), e);
            }
        }
        
        log.debug("[{}] 消息处理完成", name);
    }
}
```

**典型的规则链配置**:
```java
RuleChain gpuRuleChain = new RuleChain("GPU Rule Chain");
gpuRuleChain
    .addNode(new LogNode("GPU 数据入口"))
    .addNode(new AlarmEvaluatorNode(alarmService, deviceService))
    .addNode(new SaveTelemetryNode(telemetryStorage))
    .addNode(new LogNode("GPU 数据保存完成"));
```

#### 5.3 AlarmEvaluatorNode 评估告警

```java
public class AlarmEvaluatorNode implements RuleNode {
    
    private final AlarmService alarmService;
    private final DeviceService deviceService;
    
    @Override
    public void onMsg(Message msg, RuleNodeContext ctx) {
        // 1. 获取设备的 DeviceProfile
        DeviceId deviceId = msg.getOriginator();
        Device device = deviceService.findById(deviceId).orElse(null);
        if (device == null) return;
        
        DeviceProfile profile = deviceService
            .findProfileById(device.getDeviceProfileId())
            .orElse(null);
        if (profile == null || profile.getAlarmRules().isEmpty()) {
            return;  // 无告警规则
        }
        
        // 2. 对每个告警规则进行评估
        for (AlarmRule alarmRule : profile.getAlarmRules()) {
            evaluateAlarmRule(deviceId, alarmRule, msg.getTsKvEntries());
        }
    }
    
    private void evaluateAlarmRule(DeviceId deviceId, AlarmRule alarmRule, 
                                    List<TsKvEntry> telemetry) {
        // 3. 检查创建条件（按严重级别从高到低）
        for (Map.Entry<AlarmSeverity, AlarmCondition> entry : 
                alarmRule.getCreateConditions().entrySet()) {
            
            AlarmSeverity severity = entry.getKey();
            AlarmCondition condition = entry.getValue();
            
            // 评估条件
            boolean shouldCreate = alarmEvaluator.evaluate(condition, telemetry);
            
            if (shouldCreate) {
                // 创建或更新告警
                Alarm alarm = Alarm.builder()
                    .id(AlarmId.random())
                    .originator(deviceId)
                    .type(alarmRule.getAlarmType())
                    .severity(severity)
                    .status(AlarmStatus.ACTIVE_UNACK)
                    .startTs(System.currentTimeMillis())
                    .build();
                
                alarmService.createOrUpdate(alarm);
                log.warn("[{}] 告警触发: {} - {}", 
                    deviceId, alarmRule.getAlarmType(), severity);
                return;
            }
        }
        
        // 4. 检查清除条件
        AlarmCondition clearCondition = alarmRule.getClearCondition();
        if (clearCondition != null) {
            boolean shouldClear = alarmEvaluator.evaluate(clearCondition, telemetry);
            
            if (shouldClear) {
                // 查找活跃告警并清除
                List<Alarm> activeAlarms = alarmService
                    .findByOriginatorAndType(deviceId, alarmRule.getAlarmType());
                
                for (Alarm alarm : activeAlarms) {
                    if (alarm.getStatus() == AlarmStatus.ACTIVE_UNACK ||
                        alarm.getStatus() == AlarmStatus.ACTIVE_ACK) {
                        
                        alarmService.clear(alarm.getId());
                        log.info("[{}] 告警清除: {}", 
                            deviceId, alarmRule.getAlarmType());
                    }
                }
            }
        }
    }
}
```

**告警评估示例**:
```
当前遥测数据: {"gpu_temperature": 85}

告警规则:
- 创建条件: gpu_temperature > 80
- 清除条件: gpu_temperature <= 75

评估结果:
- 85 > 80 ✓ → 创建告警 (MAJOR)
- 85 <= 75 ✗ → 不清除

告警状态变更:
NULL → ACTIVE_UNACK (新告警，未确认)
```

#### 5.4 SaveTelemetryNode 保存数据

```java
public class SaveTelemetryNode implements RuleNode {
    
    private final TelemetryStorage storage;
    
    @Override
    public void onMsg(Message msg, RuleNodeContext ctx) {
        DeviceId deviceId = msg.getOriginator();
        List<TsKvEntry> telemetry = msg.getTsKvEntries();
        
        // 批量保存到内存存储
        storage.save(deviceId, telemetry);
        
        log.debug("[{}] 保存遥测数据: {} 个数据点", 
            deviceId, telemetry.size());
    }
}
```

### 第 6 步：数据查询和展示

#### 6.1 REST API

```java
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {
    
    private final TelemetryStorage telemetryStorage;
    private final DeviceService deviceService;
    
    @GetMapping("/{deviceId}/latest")
    public LatestTelemetryDto getLatestTelemetry(@PathVariable String deviceId) {
        DeviceId id = DeviceId.fromString(deviceId);
        Device device = deviceService.findById(id).orElseThrow();
        
        // 查询所有 key 的最新值
        Map<String, TsKvEntry> latestData = telemetryStorage.getLatest(id);
        
        // 转换为 DTO
        Map<String, TelemetryDataPointDto> dataMap = latestData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> TelemetryDataPointDto.fromTsKvEntry(e.getValue())
            ));
        
        return new LatestTelemetryDto(deviceId, device.getName(), dataMap);
    }
}
```

**API 响应示例**:
```json
{
  "deviceId": "33661981-9aa4-4bb9-907c-a34e64aef8ed",
  "deviceName": "NVIDIA GPU 0",
  "data": {
    "gpu_utilization": {
      "timestamp": 1730038841918,
      "value": 100
    },
    "gpu_temperature": {
      "timestamp": 1730038841918,
      "value": 85
    },
    "power_usage": {
      "timestamp": 1730038841918,
      "value": 152.7
    }
  }
}
```

#### 6.2 Web 前端

```javascript
// GPU 监控前端 (Chart.js)
let currentDeviceId = null;
let charts = {};

// 1. 加载设备列表
async function loadDevices() {
    const response = await fetch('/api/devices');
    const devices = await response.json();
    renderDeviceTabs(devices);
    if (devices.length > 0) {
        selectDevice(devices[0].id);
    }
}

// 2. 更新数据 (每 2 秒)
async function updateData() {
    if (!currentDeviceId) return;
    
    // 获取最新遥测数据
    const response = await fetch(`/api/telemetry/${currentDeviceId}/latest`);
    const latest = await response.json();
    
    // 更新指标卡片
    document.getElementById('gpu-utilization').textContent = 
        latest.data.gpu_utilization?.value ?? '--';
    document.getElementById('gpu-temperature').textContent = 
        latest.data.gpu_temperature?.value ?? '--';
    document.getElementById('power-usage').textContent = 
        latest.data.power_usage?.value ?? '--';
    
    // 获取历史数据并更新图表
    const historyTemp = await fetch(
        `/api/telemetry/${currentDeviceId}/history/gpu_temperature?limit=50`
    );
    const tempData = await historyTemp.json();
    updateChart(charts.temperature, tempData);
}

// 3. 启动自动刷新
setInterval(updateData, 2000);
loadDevices();
```

### 完整流程总结

```
外部数据源 (Prometheus)
    ↓ 每 2 秒
PrometheusDataPuller.pullAllPrometheusDevices()
    ├─ 查询 PromQL: DCGM_FI_DEV_GPU_UTIL
    ├─ 过滤标签: gpu="0"
    └─ 构造 JSON: {"gpu_utilization": 100}
    ↓
TransportService.processTelemetry("gpu-0-token", json)
    ├─ 根据 token 查找 Device
    └─ 创建 TransportToDeviceMsg
    ↓ actorSystem.tell()
DeviceActor (异步，独立消息队列)
    ├─ 解析 JSON → List<TsKvEntry>
    ├─ 读取 DeviceProfile.defaultRuleChainId
    └─ 创建 Message (带 ruleChainId)
    ↓ actorSystem.tell()
RuleEngineActor (全局单例)
    ├─ 根据 Message.ruleChainId 选择 RuleChain
    └─ 创建 ToRuleChainMsg
    ↓ actorSystem.tell()
RuleChainActor (每规则链一个)
    └─ 执行规则链节点 (责任链)
        ├─ LogNode: 记录日志
        ├─ AlarmEvaluatorNode: 评估告警
        │   ├─ 读取 DeviceProfile.alarmRules
        │   ├─ 评估创建条件 (gpu_temperature > 80)
        │   └─ 创建/清除告警
        └─ SaveTelemetryNode: 保存到 TelemetryStorage
    ↓
TelemetryStorage (内存时序数据库)
    └─ Map<DeviceId, Map<Key, List<TsKvEntry>>>
    ↓
REST API (/api/telemetry/{id}/latest)
    ├─ 查询最新数据
    └─ 返回 JSON
    ↓
Web 前端 (Chart.js)
    ├─ 更新指标卡片
    └─ 更新趋势图表
```

---

## 🏢 多设备场景：核心组件协作

在实际生产环境中，通常有多个设备、多个设备类型（DeviceProfile）。以下展示核心组件如何协作处理复杂场景。

### 场景设定

```
系统中有 4 个设备，分为 2 类：

1. GPU 设备 (2 个)
   - DeviceProfile: "GPU 监控配置"
   - RuleChain: "GPU Rule Chain"
   - Queue: "GPU-Queue"
   - 设备: GPU-0, GPU-1

2. BMC 设备 (2 个)
   - DeviceProfile: "BMC 监控配置"
   - RuleChain: "BMC Rule Chain"  
   - Queue: "BMC-Queue"
   - 设备: BMC-0, BMC-1
```

### 系统初始化

#### 1. DeviceService 加载设备

```java
@Service
public class DeviceServiceImpl implements DeviceService {
    
    private final DeviceRepository deviceRepository;
    private final DeviceProfileRepository profileRepository;
    
    @Override
    public List<Device> findAll() {
        // 从数据库加载所有设备
        return deviceRepository.findAll();
    }
    
    @Override
    public Optional<DeviceProfile> findProfileById(DeviceProfileId id) {
        // 加载设备配置模板
        return profileRepository.findById(id);
    }
}
```

**初始化时的数据加载**:
```
┌─────────────────────────────────────────────────────────┐
│                   设备和配置加载                           │
└─────────────────────────────────────────────────────────┘

数据库 (SQLite)
├── device_profile 表
│   ├── GPU Profile (id=gpu-profile-001)
│   │   ├── telemetryDefinitions: 7 个指标
│   │   ├── alarmRules: 2 条规则
│   │   ├── defaultRuleChainId: "gpu-chain-001"
│   │   └── defaultQueueName: "GPU-Queue"
│   │
│   └── BMC Profile (id=bmc-profile-001)
│       ├── telemetryDefinitions: 5 个指标
│       ├── alarmRules: 3 条规则
│       ├── defaultRuleChainId: "bmc-chain-001"
│       └── defaultQueueName: "BMC-Queue"
│
└── device 表
    ├── GPU-0 (deviceProfileId=gpu-profile-001, token=gpu-0-token)
    ├── GPU-1 (deviceProfileId=gpu-profile-001, token=gpu-1-token)
    ├── BMC-0 (deviceProfileId=bmc-profile-001, token=bmc-0-token)
    └── BMC-1 (deviceProfileId=bmc-profile-001, token=bmc-1-token)

            ↓ DeviceService.findAll()

内存缓存
├── Device 对象 (4 个)
└── DeviceProfile 对象 (2 个)
```

#### 2. MiniTbActorSystem 创建 Actor

```java
@Bean
public MiniTbActorSystem actorSystem() {
    return new MiniTbActorSystem(5);  // 5 个工作线程
}
```

```java
@Component
public class TransportService {
    
    public void setActorSystem(MiniTbActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        
        // 1. 创建 RuleEngineActor (全局单例)
        RuleEngineActor ruleEngineActor = new RuleEngineActor(ruleEngineService);
        actorSystem.createActor("RuleEngineActor", ruleEngineActor);
        
        // 2. 为每个设备创建 DeviceActor
        List<Device> devices = deviceService.findAll();
        for (Device device : devices) {
            createDeviceActor(device);
        }
        
        log.info("为 {} 个设备创建了 DeviceActor", devices.size());
    }
    
    private void createDeviceActor(Device device) {
        DeviceProfile profile = deviceService
            .findProfileById(device.getDeviceProfileId())
            .orElse(null);
        
        DeviceActor actor = new DeviceActor(device.getId(), device, profile);
        actorSystem.createActor(actor.getActorId(), actor);
    }
}
```

**Actor 创建结果**:
```
MiniTbActorSystem
├── actorMap: ConcurrentHashMap<ActorId, MiniTbActor>
│   ├── "RuleEngineActor" → RuleEngineActor (全局单例)
│   ├── "Device:gpu-0-id" → DeviceActor (GPU-0)
│   ├── "Device:gpu-1-id" → DeviceActor (GPU-1)
│   ├── "Device:bmc-0-id" → DeviceActor (BMC-0)
│   └── "Device:bmc-1-id" → DeviceActor (BMC-1)
│
└── executorService: ThreadPoolExecutor (5 threads)
```

#### 3. RuleEngineService 注册 RuleChain

```java
@Bean
public RuleEngineService ruleEngineService(TelemetryStorage storage, 
                                           MiniTbActorSystem actorSystem) {
    RuleEngineService service = new RuleEngineService();
    service.setActorSystem(actorSystem);
    
    // 1. 创建 Root Rule Chain
    RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
    rootRuleChain
        .addNode(new LogNode("入口日志"))
        .addNode(new SaveTelemetryNode(storage))
        .addNode(new LogNode("保存完成"));
    service.setRootRuleChain(rootRuleChain);
    
    // 2. 创建 GPU Rule Chain
    RuleChain gpuRuleChain = new RuleChain("GPU Rule Chain");
    gpuRuleChain
        .addNode(new LogNode("GPU 数据入口"))
        .addNode(new AlarmEvaluatorNode(alarmService, deviceService))
        .addNode(new SaveTelemetryNode(storage))
        .addNode(new LogNode("GPU 数据保存完成"));
    service.registerRuleChain("gpu-chain-001", gpuRuleChain);
    
    // 3. 创建 BMC Rule Chain
    RuleChain bmcRuleChain = new RuleChain("BMC Rule Chain");
    bmcRuleChain
        .addNode(new LogNode("BMC 数据入口"))
        .addNode(new FilterNode("cpu_temp", 70.0))
        .addNode(new AlarmEvaluatorNode(alarmService, deviceService))
        .addNode(new SaveTelemetryNode(storage))
        .addNode(new LogNode("BMC 数据保存完成"));
    service.registerRuleChain("bmc-chain-001", bmcRuleChain);
    
    return service;
}
```

**RuleChainActor 创建**:
```
RuleEngineService.registerRuleChain()
    ↓
actorSystem.createActor()
    ↓
MiniTbActorSystem
├── "RuleChain:root-chain-id" → RuleChainActor (Root)
├── "RuleChain:gpu-chain-001" → RuleChainActor (GPU)
└── "RuleChain:bmc-chain-001" → RuleChainActor (BMC)
```

### 并发数据处理

当多个设备同时发送数据时，系统如何并发处理：

```
时刻 T=0: 4 个设备同时发送数据
───────────────────────────────────────────────────────────

GPU-0 (Prometheus)      BMC-0 (IPMI)
    ↓                       ↓
PrometheusDataPuller   IpmiDataPuller
    ↓                       ↓
TransportService       TransportService
    ↓                       ↓
actorSystem.tell()     actorSystem.tell()

GPU-1 (Prometheus)      BMC-1 (MQTT)
    ↓                       ↓
PrometheusDataPuller   MqttTransportService
    ↓                       ↓
TransportService       TransportService
    ↓                       ↓
actorSystem.tell()     actorSystem.tell()

───────────────────────────────────────────────────────────
MiniTbActorSystem (5 个工作线程并发处理)
───────────────────────────────────────────────────────────

Thread-1               Thread-2
    ↓                       ↓
DeviceActor (GPU-0)    DeviceActor (BMC-0)
    ├─ 消息队列 (Mailbox)   ├─ 消息队列 (Mailbox)
    ├─ JSON → TsKvEntry    ├─ JSON → TsKvEntry
    └─ → RuleEngineActor   └─ → RuleEngineActor

Thread-3               Thread-4
    ↓                       ↓
DeviceActor (GPU-1)    DeviceActor (BMC-1)
    ├─ 消息队列 (Mailbox)   ├─ 消息队列 (Mailbox)
    ├─ JSON → TsKvEntry    ├─ JSON → TsKvEntry
    └─ → RuleEngineActor   └─ → RuleEngineActor

───────────────────────────────────────────────────────────
Thread-5: RuleEngineActor (协调路由)
───────────────────────────────────────────────────────────

RuleEngineActor
    ├─ 接收 4 个 ToRuleEngineMsg
    ├─ 读取 Message.ruleChainId
    ├─ GPU-0 → "gpu-chain-001"
    ├─ GPU-1 → "gpu-chain-001"
    ├─ BMC-0 → "bmc-chain-001"
    └─ BMC-1 → "bmc-chain-001"
    ↓ actorSystem.tell()

───────────────────────────────────────────────────────────
RuleChainActor (并行处理不同规则链)
───────────────────────────────────────────────────────────

Thread-1                      Thread-2
    ↓                             ↓
RuleChainActor (GPU)         RuleChainActor (BMC)
    ├─ GPU-0 消息                ├─ BMC-0 消息
    ├─ GPU-1 消息                ├─ BMC-1 消息
    ↓                             ↓
执行 GPU Rule Chain          执行 BMC Rule Chain
    ├─ LogNode                   ├─ LogNode
    ├─ AlarmEvaluatorNode        ├─ FilterNode
    ├─ SaveTelemetryNode         ├─ AlarmEvaluatorNode
    └─ LogNode                   ├─ SaveTelemetryNode
                                 └─ LogNode
```

**关键并发特性**:

1. **设备级隔离**: 每个设备有独立的 DeviceActor 和消息队列
2. **规则链并行**: 不同 RuleChain 在不同的 RuleChainActor 中并行执行
3. **线程池调度**: 5 个工作线程动态调度 Actor 任务
4. **无锁设计**: Actor 内部消息串行处理，避免锁竞争

### 消息路由示例

#### GPU 设备消息路由

```
GPU-0 发送数据: {"gpu_temperature": 85}
    ↓
TransportService.processTelemetry("gpu-0-token", json)
    ↓
DeviceActor (GPU-0)
    ├─ 读取 DeviceProfile (GPU Profile)
    ├─ profile.defaultRuleChainId = "gpu-chain-001"
    ├─ profile.defaultQueueName = "GPU-Queue"
    └─ 创建 Message
        ├─ originator = gpu-0-id
        ├─ ruleChainId = "gpu-chain-001"  ← 来自 DeviceProfile
        ├─ queueName = "GPU-Queue"        ← 来自 DeviceProfile
        └─ tsKvEntries = [{"gpu_temperature": 85}]
    ↓
RuleEngineActor
    ├─ selectRuleChain(message)
    ├─ 读取 message.ruleChainId = "gpu-chain-001"
    └─ 返回 GPU Rule Chain
    ↓
RuleChainActor (GPU)
    └─ 执行 GPU Rule Chain
        ├─ LogNode: "GPU 数据入口"
        ├─ AlarmEvaluatorNode
        │   ├─ 读取 GPU Profile.alarmRules
        │   ├─ 评估: gpu_temperature > 80? ✓
        │   └─ 创建 MAJOR 级别告警
        ├─ SaveTelemetryNode: 保存到 TelemetryStorage
        └─ LogNode: "GPU 数据保存完成"
```

#### BMC 设备消息路由

```
BMC-0 发送数据: {"cpu_temp": 75, "fan_speed": 8000}
    ↓
TransportService.processTelemetry("bmc-0-token", json)
    ↓
DeviceActor (BMC-0)
    ├─ 读取 DeviceProfile (BMC Profile)
    ├─ profile.defaultRuleChainId = "bmc-chain-001"
    ├─ profile.defaultQueueName = "BMC-Queue"
    └─ 创建 Message
        ├─ originator = bmc-0-id
        ├─ ruleChainId = "bmc-chain-001"  ← 来自 DeviceProfile
        ├─ queueName = "BMC-Queue"        ← 来自 DeviceProfile
        └─ tsKvEntries = [
            {"cpu_temp": 75},
            {"fan_speed": 8000}
        ]
    ↓
RuleEngineActor
    ├─ selectRuleChain(message)
    ├─ 读取 message.ruleChainId = "bmc-chain-001"
    └─ 返回 BMC Rule Chain
    ↓
RuleChainActor (BMC)
    └─ 执行 BMC Rule Chain
        ├─ LogNode: "BMC 数据入口"
        ├─ FilterNode: cpu_temp > 70? ✓ (通过)
        ├─ AlarmEvaluatorNode
        │   ├─ 读取 BMC Profile.alarmRules
        │   ├─ 评估: cpu_temp > 80? ✗ (不触发)
        │   └─ 无告警
        ├─ SaveTelemetryNode: 保存到 TelemetryStorage
        └─ LogNode: "BMC 数据保存完成"
```

### 组件协作总结

```
┌─────────────────────────────────────────────────────────────────┐
│               多设备场景下的组件协作                              │
└─────────────────────────────────────────────────────────────────┘

1. DeviceService
   ├─ 管理 4 个 Device 实例
   ├─ 管理 2 个 DeviceProfile 模板
   └─ 提供设备查询服务

2. MiniTbActorSystem (5 个工作线程)
   ├─ 管理 1 个 RuleEngineActor (全局)
   ├─ 管理 4 个 DeviceActor (每设备一个)
   └─ 管理 3 个 RuleChainActor (每规则链一个)

3. TransportService
   ├─ 接收 Prometheus 数据 (GPU-0, GPU-1)
   ├─ 接收 IPMI 数据 (BMC-0)
   ├─ 接收 MQTT 数据 (BMC-1)
   └─ 路由到对应的 DeviceActor

4. RuleEngineService
   ├─ 管理 3 个 RuleChain (Root, GPU, BMC)
   ├─ 根据 Message.ruleChainId 路由
   └─ 为每个 RuleChain 创建 RuleChainActor

5. TelemetryStorage
   ├─ 存储 4 个设备的遥测数据
   └─ 支持并发读写 (ConcurrentHashMap)

6. AlarmService
   ├─ 管理所有设备的告警
   ├─ 评估不同 DeviceProfile 的告警规则
   └─ 持久化到 SQLite

协作特点:
├─ 配置驱动: DeviceProfile 定义遥测、告警、路由
├─ 异步处理: Actor 模型实现高并发
├─ 规则隔离: 不同设备类型使用不同规则链
├─ 并行执行: 多个 RuleChainActor 并行处理
└─ 故障隔离: 一个 Actor 崩溃不影响其他 Actor
```

---

## 📁 项目结构

```
minitb/
├── data/
│   └── minitb.db                        # SQLite 数据库
├── src/
│   ├── main/java/com/minitb/
│   │   ├── actor/                       # Actor 系统
│   │   │   ├── MiniTbActor.java         # Actor 接口
│   │   │   ├── MiniTbActorSystem.java   # Actor 容器
│   │   │   ├── MiniTbActorMailbox.java  # 消息队列
│   │   │   ├── device/
│   │   │   │   └── DeviceActor.java     # 设备 Actor
│   │   │   ├── ruleengine/
│   │   │   │   └── RuleEngineActor.java # 规则引擎 Actor
│   │   │   ├── rulechain/
│   │   │   │   └── RuleChainActor.java  # 规则链 Actor
│   │   │   └── msg/
│   │   │       ├── TransportToDeviceMsg.java
│   │   │       ├── ToRuleEngineMsg.java
│   │   │       └── ToRuleChainMsg.java
│   │   │
│   │   ├── application/                 # 应用层
│   │   │   └── service/
│   │   │       ├── DeviceService.java
│   │   │       ├── impl/
│   │   │       │   └── DeviceServiceImpl.java
│   │   │       ├── alarm/
│   │   │       │   ├── AlarmService.java
│   │   │       │   ├── AlarmServiceImpl.java
│   │   │       │   └── AlarmEvaluator.java
│   │   │       └── DataInitializer.java
│   │   │
│   │   ├── configuration/
│   │   │   └── MiniTBConfiguration.java
│   │   │
│   │   ├── datasource/                  # 数据源
│   │   │   ├── prometheus/
│   │   │   │   ├── PrometheusDataPuller.java
│   │   │   │   └── PrometheusQueryResult.java
│   │   │   └── ipmi/
│   │   │       └── IpmiDataPuller.java
│   │   │
│   │   ├── domain/                      # 领域层
│   │   │   ├── id/                      # 强类型 ID
│   │   │   │   ├── EntityId.java
│   │   │   │   ├── DeviceId.java
│   │   │   │   ├── DeviceProfileId.java
│   │   │   │   ├── AlarmId.java
│   │   │   │   └── RuleChainId.java
│   │   │   ├── device/                  # 设备聚合
│   │   │   │   ├── Device.java
│   │   │   │   ├── DeviceProfile.java
│   │   │   │   ├── TelemetryDefinition.java
│   │   │   │   ├── DeviceConfiguration.java
│   │   │   │   ├── PrometheusDeviceConfiguration.java
│   │   │   │   ├── IpmiDeviceConfiguration.java
│   │   │   │   ├── DeviceRepository.java
│   │   │   │   └── DeviceProfileRepository.java
│   │   │   ├── telemetry/               # 遥测值对象
│   │   │   │   ├── DataType.java
│   │   │   │   ├── TsKvEntry.java
│   │   │   │   ├── BasicTsKvEntry.java
│   │   │   │   ├── LongDataEntry.java
│   │   │   │   ├── DoubleDataEntry.java
│   │   │   │   ├── BooleanDataEntry.java
│   │   │   │   └── StringDataEntry.java
│   │   │   ├── messaging/               # 消息值对象
│   │   │   │   ├── Message.java
│   │   │   │   └── MessageType.java
│   │   │   ├── protocol/                # 协议配置
│   │   │   │   ├── ProtocolConfig.java
│   │   │   │   ├── PrometheusConfig.java
│   │   │   │   ├── MqttConfig.java
│   │   │   │   └── HttpConfig.java
│   │   │   ├── rule/                    # 规则模型
│   │   │   │   ├── RuleNode.java
│   │   │   │   ├── RuleNodeContext.java
│   │   │   │   └── RuleChain.java
│   │   │   └── alarm/                   # 告警领域
│   │   │       ├── Alarm.java
│   │   │       ├── AlarmSeverity.java
│   │   │       ├── AlarmStatus.java
│   │   │       ├── AlarmRule.java
│   │   │       ├── AlarmCondition.java
│   │   │       ├── AlarmConditionFilter.java
│   │   │       ├── FilterOperator.java
│   │   │       └── AlarmRepository.java
│   │   │
│   │   ├── infrastructure/              # 基础设施层
│   │   │   ├── persistence/
│   │   │   │   ├── jpa/                 # JPA 适配器
│   │   │   │   │   └── ...
│   │   │   │   └── sqlite/              # SQLite 适配器
│   │   │   │       ├── SqliteConnectionManager.java
│   │   │   │       ├── SqliteDeviceRepositoryAdapter.java
│   │   │   │       ├── SqliteDeviceProfileRepositoryAdapter.java
│   │   │   │       ├── alarm/
│   │   │   │       │   └── SqliteAlarmRepositoryAdapter.java
│   │   │   │       └── mapper/
│   │   │   │           ├── DeviceRowMapper.java
│   │   │   │           └── DeviceProfileRowMapper.java
│   │   │   ├── transport/               # 传输层
│   │   │   │   ├── service/
│   │   │   │   │   └── TransportService.java
│   │   │   │   └── mqtt/
│   │   │   │       ├── MqttTransportService.java
│   │   │   │       └── MqttTransportHandler.java
│   │   │   ├── web/                     # Web 层
│   │   │   │   ├── controller/
│   │   │   │   │   ├── DeviceController.java
│   │   │   │   │   ├── TelemetryController.java
│   │   │   │   │   └── AlarmController.java
│   │   │   │   └── dto/
│   │   │   │       ├── DeviceDto.java
│   │   │   │       ├── LatestTelemetryDto.java
│   │   │   │       └── alarm/
│   │   │   │           ├── AlarmDto.java
│   │   │   │           └── AlarmStatsDto.java
│   │   │   └── rule/                    # 规则节点实现
│   │   │       ├── LogNode.java
│   │   │       ├── FilterNode.java
│   │   │       ├── SaveTelemetryNode.java
│   │   │       ├── AlarmEvaluatorNode.java
│   │   │       └── DefaultRuleNodeContext.java
│   │   │
│   │   ├── ruleengine/
│   │   │   └── RuleEngineService.java
│   │   │
│   │   ├── storage/
│   │   │   └── TelemetryStorage.java
│   │   │
│   │   └── MiniTBSpringBootApplication.java
│   │
│   ├── main/resources/
│   │   ├── application.yml
│   │   ├── application-sqlite.yml
│   │   ├── logback.xml
│   │   └── static/
│   │       ├── index.html
│   │       └── gpu-monitor.js
│   │
│   └── test/java/com/minitb/
│       ├── domain/
│       ├── integration/
│       │   ├── AlarmEndToEndTest.java
│       │   ├── RuleChainRoutingTest.java
│       │   ├── PrometheusDeviceIntegrationTest.java
│       │   └── ...
│       └── service/
│           └── ...
│
├── README.md
├── pom.xml
└── start-gpu-monitor.sh
```

---

## 🧪 测试

### 运行所有测试

```bash
mvn test
```

### 端到端测试

```bash
# 告警系统测试
mvn test -Dtest=AlarmEndToEndTest

# 规则链路由测试
mvn test -Dtest=RuleChainRoutingTest

# Prometheus 集成测试
mvn test -Dtest=PrometheusDeviceIntegrationTest

# GPU 监控测试
mvn test -Dtest=GpuMonitoringEndToEndTest
```

### 测试覆盖

- **领域模型测试**: 验证领域逻辑
- **服务层测试**: 验证业务逻辑
- **持久化测试**: 验证 SQLite/JPA 适配器
- **集成测试**: 验证完整数据流
- **端到端测试**: 验证实际场景

---

## 📚 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **核心框架** | Spring Boot | 3.2.1 | 应用框架 |
| **Web** | Spring MVC | 6.1.2 | REST API |
| **持久化** | Spring Data JPA | 3.2.1 | JPA 持久化 (可选) |
| **数据库** | SQLite | 3.44.1.0 | 文件数据库 |
| **JSON** | Jackson | 2.15.3 | JSON 序列化 |
| **网络** | Netty | 4.1.100 | MQTT 服务器 |
| **日志** | SLF4J + Logback | 2.0.9 | 日志框架 |
| **构建** | Maven | 3.6+ | 依赖管理 |
| **Java** | OpenJDK | 17 | 运行环境 |
| **工具** | Lombok | 1.18.36 | 减少样板代码 |
| **测试** | JUnit 5 | 5.10.1 | 单元测试 |
| **Mock** | Mockito | 5.7.0 | Mock 框架 |

---

## 📄 许可证

MIT License

---

**MiniTB - 基于 Spring Boot + Actor 模型 + 六边形架构的高性能物联网数据平台**

**关键特性**: 配置驱动 | 异步处理 | 规则引擎 | 告警系统 | 多数据源支持
