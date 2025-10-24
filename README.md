# MiniTB - 轻量级物联网数据平台

MiniTB 是一个轻量级的物联网（IoT）数据采集与处理平台，专注于核心数据流的高效处理。采用消息驱动架构和强类型数据系统，支持多种数据源和灵活的规则引擎。

**核心特点**: 约 2000 行代码 | 强类型数据系统 | 灵活的规则引擎 | 支持多种数据源

## 🏗️ 总体架构

MiniTB 采用分层架构设计，各层职责明确、松耦合：

```
┌─────────────────────────────────────────────────────────────┐
│                     数据源层 (Data Sources)                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────────┐  │
│  │   MQTT   │  │   HTTP   │  │  Prometheus (拉取模式)    │  │
│  │  (推送)  │  │  (推送)  │  │   (定时拉取外部指标)      │  │
│  └─────┬────┘  └─────┬────┘  └────────────┬─────────────┘  │
└────────┼─────────────┼────────────────────┼────────────────┘
         │             │                    │
         └─────────────┴────────────────────┘
                       ↓
         ┌─────────────────────────────────┐
         │    传输服务层 (Transport Layer)   │
         │  • 设备认证                       │
         │  • 协议解析                       │
         │  • JSON → 强类型转换 (TsKvEntry) │
         │  • 消息封装 (TbMsg)              │
         └──────────────┬──────────────────┘
                        ↓
         ┌─────────────────────────────────┐
         │   规则引擎层 (Rule Engine Layer)  │
         │  • 责任链模式                     │
         │  • 数据过滤、转换、聚合            │
         │  • 异步处理 (线程池)              │
         │  ┌────┐  ┌────┐  ┌────┐         │
         │  │Log │→│Filter│→│Save│ ...     │
         │  └────┘  └────┘  └────┘         │
         └──────────────┬──────────────────┘
                        ↓
         ┌─────────────────────────────────┐
         │     存储层 (Storage Layer)        │
         │  • 按设备ID分类                   │
         │  • 按键名索引                     │
         │  • 按时间序列存储                 │
         │  • 支持按类型查询                 │
         └─────────────────────────────────┘
```

## 🔄 核心数据流程

### 流程概览

```
设备/数据源 → 协议层 → 认证 → 强类型转换 → 规则处理 → 数据存储
```

### 详细流程

**1. 数据接入**
```
MQTT设备: {"temperature":25.5, "humidity":60}
         ↓
Prometheus: PromQL查询 → {"cpu_usage":0.08, "memory_total":17179869184}
```

**2. 协议解析与认证**
```
TransportService
  ├─ authenticateDevice(accessToken)  → 验证设备身份
  ├─ checkRateLimit(device)          → 限流检查
  └─ parseJsonToKvEntries(json)      → JSON转强类型
```

**3. 强类型转换** ⭐核心创新
```
JSON: {"temperature":25.5, "humidity":60, "online":true, "status":"ok"}
  ↓ 自动类型识别
TsKvEntry[]:
  - BasicTsKvEntry(ts, DoubleDataEntry("temperature", 25.5))   ← DOUBLE
  - BasicTsKvEntry(ts, LongDataEntry("humidity", 60))          ← LONG
  - BasicTsKvEntry(ts, BooleanDataEntry("online", true))       ← BOOLEAN
  - BasicTsKvEntry(ts, StringDataEntry("status", "ok"))        ← STRING
```

**4. 消息封装**
```
TbMsg {
    id: UUID
    type: POST_TELEMETRY_REQUEST
    originator: DeviceId
    tsKvEntries: List<TsKvEntry>    ← 强类型数据
    data: String                     ← 原始JSON（兼容）
    timestamp: long
}
```

**5. 规则引擎处理**（责任链模式）
```
TbMsg → LogNode → FilterNode → SaveTelemetryNode → LogNode
         记录     过滤条件       持久化存储        记录结果
```

**6. 数据存储**
```
按设备分类 → 按键名索引 → 按时间排序
Map<DeviceId, Map<String, List<TsKvEntry>>>
```

## 🎯 核心实体与类型

### 1. 设备相关

#### **Device（设备）**
```java
Device {
    DeviceId id;              // 设备唯一标识
    TenantId tenantId;        // 租户ID（多租户隔离）
    String name;              // 设备名称
    String type;              // 设备类型
    String accessToken;       // 访问令牌（认证）
    String deviceProfileId;   // 设备配置文件ID
}
```

#### **DeviceProfile（设备配置文件）**
```java
DeviceProfile {
    String id;
    String name;
    List<TelemetryDefinition> telemetryDefinitions;  // 遥测定义列表
    boolean strictMode;                               // 严格验证模式
    DataSourceType dataSourceType;                    // 数据源类型
}
```

#### **TelemetryDefinition（遥测定义）**
```java
TelemetryDefinition {
    String key;                    // 遥测键名
    String displayName;            // 显示名称
    DataType dataType;             // 数据类型
    String unit;                   // 单位
    ProtocolConfig protocolConfig; // 协议配置（多态）
}

// 协议配置示例
PrometheusConfig {
    String promQL;                 // PromQL查询表达式 ⭐
    boolean needsRateCalculation;  // 是否需要速率计算
    int rateWindow;                // 速率计算窗口
}
```

### 2. 数据类型系统 ⭐

#### **DataType（数据类型枚举）**
```java
enum DataType {
    BOOLEAN,    // 布尔值
    LONG,       // 长整型
    DOUBLE,     // 双精度浮点
    STRING,     // 字符串
    JSON        // JSON对象
}
```

#### **KvEntry（键值对）**
```java
interface KvEntry {
    String getKey();
    DataType getDataType();
    Optional<String> getStrValue();
    Optional<Long> getLongValue();
    Optional<Double> getDoubleValue();
    Optional<Boolean> getBooleanValue();
    Optional<String> getJsonValue();
}

// 具体实现
BasicKvEntry (抽象基类)
  ├─ StringDataEntry
  ├─ LongDataEntry
  ├─ DoubleDataEntry
  ├─ BooleanDataEntry
  └─ JsonDataEntry
```

#### **TsKvEntry（时间序列键值对）**
```java
TsKvEntry {
    long ts;           // 时间戳
    KvEntry kv;        // 键值对（组合模式）
}
```

**设计模式**: 组合模式 - `TsKvEntry` 组合 `KvEntry`，而不是继承

### 3. 消息系统

#### **TbMsg（核心消息对象）**
```java
TbMsg {
    UUID id;                        // 消息唯一ID
    TbMsgType type;                 // 消息类型
    DeviceId originator;            // 消息发起者
    Map<String,String> metaData;    // 元数据
    String data;                    // JSON数据（兼容）
    List<TsKvEntry> tsKvEntries;    // 强类型数据
    long timestamp;                 // 时间戳
}
```

TbMsg 是整个平台的数据载体，从传输层流向规则引擎再到存储层。

### 4. 实体关系

#### **Asset（资产）**
```java
Asset {
    AssetId id;
    TenantId tenantId;
    String name;
    String type;
}
```

#### **EntityRelation（实体关系）**
```java
EntityRelation {
    UUID fromId;        // 源实体ID
    String fromType;    // 源实体类型 (Device/Asset)
    UUID toId;          // 目标实体ID
    String toType;      // 目标实体类型
    String type;        // 关系类型 (Contains/Manages)
}
```

支持层级结构建模：
```
智能大厦(Asset)
  ├─ Contains → 1楼(Asset)
  │   ├─ Contains → 101房间(Asset)
  │   │   └─ Contains → 温度传感器(Device)
  │   └─ Contains → 102房间(Asset)
  └─ Contains → 2楼(Asset)
```

## ⚡ 支持的核心特性

### 1. 多数据源支持

#### **MQTT（设备推送）**
- 协议: MQTT 3.1.1
- 端口: 1883
- 认证: Access Token
- 主题: `v1/devices/me/telemetry`
- 格式: JSON

示例：
```bash
mosquitto_pub -h localhost -p 1883 -u test-token-001 \
  -t v1/devices/me/telemetry \
  -m '{"temperature":25.5,"humidity":60}'
```

#### **Prometheus（主动拉取）** ⭐特色功能
- 协议: HTTP/PromQL
- 模式: 定时拉取
- 配置: 通过 DeviceProfile 定义
- 支持: 完整的 PromQL 查询语法

**简单查询**:
```
process_cpu_seconds_total
node_memory_total_bytes
```

**速率计算**:
```
rate(http_requests_total[5m])
avg(rate(node_cpu_seconds_total{mode!="idle"}[1m]))
```

**复杂表达式**:
```
(1 - node_memory_free_bytes / node_memory_total_bytes) * 100
histogram_quantile(0.95, http_request_duration_seconds_bucket)
```

### 2. 强类型数据系统 ⭐核心优势

#### **自动类型识别**
无需预定义 schema，自动识别 JSON 数据类型：
```json
{
  "temperature": 25.5,    → DoubleDataEntry (识别为浮点数)
  "humidity": 60,         → LongDataEntry (识别为整数)
  "online": true,         → BooleanDataEntry (识别为布尔值)
  "status": "running",    → StringDataEntry (识别为字符串)
  "config": {...}         → JsonDataEntry (识别为JSON对象)
}
```

#### **类型安全操作**
```java
// 编译时类型检查
TsKvEntry entry = storage.getLatest(deviceId, "temperature");
if (entry.getDataType() == DataType.DOUBLE) {
    double value = entry.getDoubleValue().get();  // 类型安全
}

// 按类型过滤
List<TsKvEntry> allNumbers = storage.queryByType(
    deviceId, 
    DataType.DOUBLE, 
    startTs, 
    endTs
);
```

#### **高效存储**
```
Map<DeviceId, Map<String, List<TsKvEntry>>>
     设备ID      键名       时间序列数据
     
示例:
device-001
  ├─ "temperature" → [TsKvEntry(t1,25.5), TsKvEntry(t2,26.0), ...]
  ├─ "humidity"    → [TsKvEntry(t1,60), TsKvEntry(t2,61), ...]
  └─ "online"      → [TsKvEntry(t1,true), TsKvEntry(t2,true), ...]
```

### 3. 灵活的设备配置系统

#### **DeviceProfile（配置模板）**
一个配置可应用到多个设备，统一管理遥测定义。

**MQTT 传感器配置示例**:
```java
DeviceProfile sensorProfile = DeviceProfile.builder()
    .name("温湿度传感器")
    .dataSourceType(DataSourceType.MQTT)
    .strictMode(false)  // 允许设备发送额外的数据
    .build();

sensorProfile.addTelemetryDefinition(
    TelemetryDefinition.simple("temperature", DataType.DOUBLE)
        .toBuilder()
        .displayName("温度")
        .unit("°C")
        .build()
);
```

**Prometheus 监控配置示例**:
```java
DeviceProfile monitorProfile = DeviceProfile.builder()
    .name("系统性能监控")
    .dataSourceType(DataSourceType.PROMETHEUS)
    .strictMode(true)  // 只拉取定义的指标
    .build();

// 使用复杂 PromQL
monitorProfile.addTelemetryDefinition(
    TelemetryDefinition.prometheus(
        "cpu_usage",
        "avg(rate(node_cpu_seconds_total{mode!=\"idle\"}[1m]))"
    )
);

monitorProfile.addTelemetryDefinition(
    TelemetryDefinition.prometheus(
        "memory_usage_pct",
        "(1 - node_memory_free_bytes / node_memory_total_bytes) * 100"
    )
);
```

**混合协议配置** - 一个设备支持多种数据源:
```java
DeviceProfile gatewayProfile = ...;

// MQTT 推送的传感器数据
gatewayProfile.addTelemetryDefinition(
    TelemetryDefinition.mqtt("temperature", DataType.DOUBLE)
);

// Prometheus 拉取的系统指标
gatewayProfile.addTelemetryDefinition(
    TelemetryDefinition.prometheus("cpu_usage", "...")
);

// HTTP 获取的外部数据
gatewayProfile.addTelemetryDefinition(
    TelemetryDefinition.builder()
        .key("weather")
        .protocolConfig(HttpConfig.builder().jsonPath("$.temp").build())
        .build()
);
```

### 4. 规则引擎

#### **责任链模式**
规则节点顺序处理消息，每个节点专注单一职责：

```java
RuleChain chain = new RuleChain("数据处理链");
chain
  .addNode(new LogNode("入口日志"))           // 记录原始数据
  .addNode(new FilterNode("temperature", 30)) // 过滤温度>30的数据
  .addNode(new LogNode("过滤后"))             // 记录过滤结果
  .addNode(new SaveTelemetryNode(storage))   // 保存到存储
  .addNode(new LogNode("完成"));              // 记录完成
```

#### **内置节点**
- **LogNode**: 日志记录，支持强类型数据打印
- **FilterNode**: 数据过滤，支持数值比较
- **SaveTelemetryNode**: 数据持久化

#### **自定义节点**
实现 `RuleNode` 接口即可：
```java
public class AlarmNode implements RuleNode {
    private RuleNode next;
    
    @Override
    public void onMsg(TbMsg msg) {
        // 检查告警条件
        for (TsKvEntry entry : msg.getTsKvEntries()) {
            if (entry.getKey().equals("temperature") && 
                entry.getDataType() == DataType.DOUBLE) {
                double temp = entry.getDoubleValue().get();
                if (temp > 35) {
                    createAlarm("高温告警", temp);
                }
            }
        }
        
        // 传递给下一个节点
        if (next != null) {
            next.onMsg(msg);
        }
    }
}
```

### 5. 实体关系管理

#### **支持的关系类型**
- **Contains**: 包含关系（如：楼层包含房间）
- **Manages**: 管理关系
- **Uses**: 使用关系

#### **查询功能**
```java
// 查询直接子级
List<EntityRelation> children = relationService.findByFrom(
    tenantId, buildingId, RelationTypeGroup.COMMON
);

// 递归查询所有层级
Set<UUID> allDescendants = relationService.findRelatedEntities(
    tenantId,
    buildingId,
    EntitySearchDirection.FROM,  // 向下查询
    10  // 最大深度
);

// 反向查询父级
List<EntityRelation> parents = relationService.findByTo(
    tenantId, deviceId, RelationTypeGroup.COMMON
);
```

## 🚀 快速开始

### 环境要求

```bash
# Java 17
java -version

# Maven 3.6+
mvn -version

# MQTT 客户端（可选）
brew install mosquitto

# Prometheus + node_exporter（可选）
# Prometheus: http://localhost:9090
# node_exporter: http://localhost:9100
```

### 启动平台

```bash
cd minitb
./run.sh
```

启动后会看到：
```
========================================
   MiniTB - 物联网数据平台
========================================

[1/8] 初始化数据存储层...
[2/8] 初始化设备配置文件服务...
  创建配置: MQTT传感器标准配置
  创建配置: Prometheus系统监控
  创建配置: 系统资源监控 (node_exporter)
[3/8] 初始化实体关系服务...
[4/8] 初始化规则引擎...
[5/8] 配置规则链...
[6/8] 初始化传输服务...
[7/8] 启动MQTT服务器... (端口 1883)
[8/8] 启动Prometheus数据拉取器...
  监控设备1: Prometheus 进程监控
    * cpu_seconds_total
    * memory_alloc_bytes
    * goroutines
  监控设备2: 系统资源监控 (node_exporter)
    * system_cpu_usage (速率计算)
    * memory_total_bytes
    * memory_free_bytes
    * memory_usage_percent (计算表达式)

MiniTB运行中，按Ctrl+C停止...
```

### 发送测试数据

#### **MQTT 推送**
```bash
# 多种数据类型测试
mosquitto_pub -h localhost -p 1883 -u test-token-001 \
  -t v1/devices/me/telemetry \
  -m '{"temperature":25.5,"humidity":60,"online":true,"status":"running"}'

# 观察日志输出
# [入口日志] 数据点: key=temperature, type=DOUBLE, value=25.5
# [入口日志] 数据点: key=humidity, type=LONG, value=60
# [入口日志] 数据点: key=online, type=BOOLEAN, value=true
# [入口日志] 数据点: key=status, type=STRING, value=running
```

#### **Prometheus 拉取**
无需手动操作，平台会自动：
- 每 10 秒从 Prometheus 拉取数据
- 监控 Prometheus 进程（CPU、内存、协程）
- 监控系统资源（通过 node_exporter）

### 查看数据

```bash
# 实时查看数据
tail -f minitb/data/telemetry_*.log

# 查看所有数据文件
ls -lh minitb/data/

# 查看特定设备的最新数据
cat minitb/data/telemetry_*.log | tail -20
```

## 📁 项目结构

```
minitb/
├── src/main/java/com/minitb/
│   ├── common/                          # 公共模块
│   │   ├── entity/                      # 实体定义
│   │   │   ├── Device.java              # 设备
│   │   │   ├── DeviceProfile.java       # 设备配置
│   │   │   ├── TelemetryDefinition.java # 遥测定义
│   │   │   ├── Asset.java               # 资产
│   │   │   ├── DeviceId / TenantId / AssetId
│   │   │   └── protocol/                # 协议配置（多态）
│   │   │       ├── ProtocolConfig.java  # 接口
│   │   │       ├── PrometheusConfig.java
│   │   │       ├── MqttConfig.java
│   │   │       └── HttpConfig.java
│   │   ├── kv/                          # 强类型数据系统
│   │   │   ├── DataType.java
│   │   │   ├── KvEntry.java             # 键值对接口
│   │   │   ├── BasicKvEntry.java        # 抽象实现
│   │   │   ├── StringDataEntry.java     # 5种具体类型
│   │   │   ├── LongDataEntry.java
│   │   │   ├── DoubleDataEntry.java
│   │   │   ├── BooleanDataEntry.java
│   │   │   ├── JsonDataEntry.java
│   │   │   ├── TsKvEntry.java           # 时间序列接口
│   │   │   └── BasicTsKvEntry.java      # 时间序列实现
│   │   └── msg/                         # 消息系统
│   │       ├── TbMsg.java
│   │       └── TbMsgType.java
│   ├── service/                         # 服务层
│   │   └── DeviceProfileService.java    # 配置管理
│   ├── relation/                        # 实体关系
│   │   ├── EntityRelation.java
│   │   ├── EntityRelationService.java
│   │   ├── EntitySearchDirection.java
│   │   └── RelationTypeGroup.java
│   ├── transport/                       # 传输层
│   │   ├── mqtt/                        # MQTT 实现
│   │   │   ├── MqttTransportHandler.java
│   │   │   └── MqttTransportService.java
│   │   └── service/
│   │       └── TransportService.java    # 传输服务
│   ├── datasource/                      # 数据源
│   │   └── prometheus/
│   │       ├── PrometheusDataPuller.java
│   │       └── DeviceMetricConfig.java
│   ├── ruleengine/                      # 规则引擎
│   │   ├── node/                        # 规则节点
│   │   │   ├── RuleNode.java
│   │   │   ├── LogNode.java
│   │   │   ├── FilterNode.java
│   │   │   └── SaveTelemetryNode.java
│   │   ├── RuleChain.java
│   │   └── RuleEngineService.java
│   ├── storage/                         # 存储层
│   │   └── TelemetryStorage.java
│   └── MiniTBApplication.java           # 主程序
├── pom.xml
└── run.sh
```

## 🔧 API 使用示例

### 存储 API

```java
TelemetryStorage storage = new TelemetryStorage(true);

// 保存单个数据点
TsKvEntry entry = new BasicTsKvEntry(
    System.currentTimeMillis(),
    new DoubleDataEntry("temperature", 25.5)
);
storage.save(deviceId, entry);

// 批量保存
storage.save(deviceId, List.of(entry1, entry2, entry3));

// 查询特定键的数据
List<TsKvEntry> temps = storage.query(
    deviceId, 
    "temperature", 
    startTs, 
    endTs
);

// 获取最新值
TsKvEntry latest = storage.getLatest(deviceId, "temperature");

// 获取所有键的最新值
Map<String, TsKvEntry> allLatest = storage.getLatestAll(deviceId);

// 按数据类型查询
List<TsKvEntry> doubles = storage.queryByType(
    deviceId, 
    DataType.DOUBLE, 
    startTs, 
    endTs
);

// 获取设备的所有键名
Set<String> keys = storage.getKeys(deviceId);
```

### 关系 API

```java
EntityRelationService relationService = new EntityRelationService();

// 创建关系
EntityRelation relation = new EntityRelation(
    buildingId, "Asset",
    roomId, "Asset",
    EntityRelation.CONTAINS_TYPE
);
relationService.saveRelation(tenantId, relation);

// 检查关系是否存在
boolean exists = relationService.checkRelation(
    tenantId, fromId, fromType, toId, toType, 
    EntityRelation.CONTAINS_TYPE, RelationTypeGroup.COMMON
);

// 查询直接关系
List<EntityRelation> children = relationService.findByFrom(
    tenantId, entityId, RelationTypeGroup.COMMON
);

// 递归查询（多层级）
Set<UUID> descendants = relationService.findRelatedEntities(
    tenantId,
    rootId,
    EntitySearchDirection.FROM,  // 向下
    10  // 最大深度
);

Set<UUID> ancestors = relationService.findRelatedEntities(
    tenantId,
    leafId,
    EntitySearchDirection.TO,    // 向上
    10
);
```

### 配置 API

```java
DeviceProfileService profileService = new DeviceProfileService();

// 创建配置
DeviceProfile profile = DeviceProfile.builder()
    .id("my-profile")
    .name("自定义配置")
    .build();

profile.addTelemetryDefinition(...);

profileService.saveProfile(profile);

// 查询配置
Optional<DeviceProfile> found = profileService.findById("my-profile");

// 获取所有配置
Map<String, DeviceProfile> all = profileService.getAllProfiles();
```

## 🎨 设计模式应用

### 1. 组合模式
- `BasicTsKvEntry` 组合 `KvEntry`（而非继承）
- `TelemetryDefinition` 组合 `ProtocolConfig`（接口多态）

**优势**: 灵活性高，支持运行时组合

### 2. 策略模式
- `ProtocolConfig` 接口 + 多个实现类
- 不同协议有不同的配置策略

### 3. 责任链模式
- `RuleChain` 链接多个 `RuleNode`
- 消息顺序流经各节点

### 4. 建造者模式
- 大量使用 Lombok `@Builder`
- 流式 API，代码清晰

### 5. 工厂模式
- `TelemetryDefinition.simple()`
- `TelemetryDefinition.prometheus()`
- `TelemetryDefinition.prometheusRate()`

## 💡 核心设计理念

### 1. 为什么使用组合而非继承？

**继承方式的问题**:
```java
// ❌ 继承：类型混杂，难以处理
List<TelemetryDefinition> defs;
defs.add(new MqttTelemetryDefinition(...));
defs.add(new PrometheusTelemetryDefinition(...));

// 需要大量类型判断和转换
for (TelemetryDefinition def : defs) {
    if (def instanceof PrometheusTelemetryDefinition) {
        PrometheusTelemetryDefinition pDef = (PrometheusTelemetryDefinition) def;
        // ...
    }
}
```

**组合方式的优势**:
```java
// ✅ 组合：类型统一，多态通过接口
List<TelemetryDefinition> defs;
defs.add(TelemetryDefinition.mqtt(...));
defs.add(TelemetryDefinition.prometheus(...));

// 简洁的类型检查
for (TelemetryDefinition def : defs) {
    if (def.isPrometheus()) {
        PrometheusConfig config = def.getPrometheusConfig();
        // 类型安全访问
    }
}
```

### 2. 为什么需要强类型系统？

**无类型系统的问题**:
```java
// ❌ 字符串存储：每次都要解析
String data = "{\"temperature\":25.5,\"humidity\":60}";
JsonObject json = JsonParser.parseString(data);  // 重复解析
double temp = json.get("temperature").getAsDouble();

// ❌ 无法按类型查询
// ❌ 无法编译时检查
```

**强类型系统的优势**:
```java
// ✅ 一次解析，类型确定
List<TsKvEntry> entries = parseJsonToKvEntries(data);

// ✅ 类型安全访问
TsKvEntry temp = entries.get(0);
if (temp.getDataType() == DataType.DOUBLE) {
    double value = temp.getDoubleValue().get();  // 编译时检查
}

// ✅ 按类型查询
List<TsKvEntry> allDoubles = storage.queryByType(..., DataType.DOUBLE, ...);

// ✅ 无需重复解析JSON
```

### 3. 为什么 TsKvEntry 组合 KvEntry？

**设计**:
```java
public class BasicTsKvEntry implements TsKvEntry {
    private long ts;        // 时间戳
    private KvEntry kv;     // 组合（不是继承）
}
```

**优势**:
- ✅ 可以包装任意 `KvEntry` 实现
- ✅ 时间戳与数据分离
- ✅ `KvEntry` 可复用于属性（无时间戳）
- ✅ 符合单一职责原则

## 📊 性能特点

| 特性 | 说明 | 适用场景 |
|------|------|---------|
| **内存存储** | 高速读写 | 小规模部署、开发测试 |
| **异步处理** | 线程池异步执行规则 | 提高吞吐量 |
| **强类型缓存** | 避免重复JSON解析 | 降低CPU消耗 |
| **按键索引** | O(1)查询复杂度 | 快速检索特定指标 |
| **文件备份** | 可选的持久化 | 数据安全 |

## 🌐 配置选项

### 环境变量

```bash
# Prometheus 服务地址
export PROMETHEUS_URL=http://localhost:9090

# 数据拉取间隔（秒）
export PROMETHEUS_PULL_INTERVAL=10
```

### 代码配置

```java
// 修改 MiniTBApplication.java

// 1. 调整拉取间隔
int pullInterval = 30;  // 改为30秒

// 2. 添加新的监控配置
DeviceProfile customProfile = DeviceProfile.builder()
    .name("自定义监控")
    .build();

// 3. 调整规则链
rootChain
    .addNode(new FilterNode("temperature", 35))  // 改阈值
    .addNode(new CustomNode())                   // 自定义节点
    .addNode(new SaveTelemetryNode(storage));
```

## 🔬 技术细节

### 数据类型自动识别算法

```java
private KvEntry parseValue(String key, JsonElement element) {
    if (element.isJsonPrimitive()) {
        if (element.getAsJsonPrimitive().isBoolean()) {
            return new BooleanDataEntry(key, element.getAsBoolean());
        } else if (element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            // 判断整数 vs 浮点数
            if (value == Math.floor(value) && !Double.isInfinite(value)) {
                return new LongDataEntry(key, element.getAsLong());
            } else {
                return new DoubleDataEntry(key, value);
            }
        } else {
            return new StringDataEntry(key, element.getAsString());
        }
    } else if (element.isJsonObject() || element.isJsonArray()) {
        return new JsonDataEntry(key, element.toString());
    }
}
```

### PromQL 查询执行

```java
// 1. 构造查询URL
String url = prometheusUrl + "/api/v1/query?query=" + 
             URLEncoder.encode(promQL, UTF_8);

// 2. HTTP 请求
HttpResponse response = httpClient.send(request);

// 3. 解析 Prometheus 响应
JsonObject data = JsonParser.parseString(response.body());
JsonArray results = data.get("data").getAsJsonObject()
                        .get("result").getAsJsonArray();

// 4. 提取数值
double value = results.get(0).getAsJsonObject()
                      .get("value").getAsJsonArray()
                      .get(1).getAsDouble();

// 5. 转换为 TsKvEntry
```

## 🎓 扩展开发

### 添加新的数据源

```java
// 1. 创建协议配置
@Data
@Builder
public class SnmpConfig implements ProtocolConfig {
    private String oid;
    private String community;
    
    @Override
    public String getProtocolType() {
        return "SNMP";
    }
}

// 2. 扩展 TelemetryDefinition
public static TelemetryDefinition snmp(String key, String oid) {
    return TelemetryDefinition.builder()
        .key(key)
        .protocolConfig(SnmpConfig.builder().oid(oid).build())
        .build();
}

// 3. 创建数据拉取器
public class SnmpDataPuller {
    public void pullData(DeviceProfile profile) {
        for (TelemetryDefinition def : profile.getTelemetryDefinitions()) {
            if (def.getProtocolType().equals("SNMP")) {
                SnmpConfig config = (SnmpConfig) def.getProtocolConfig();
                // SNMP 查询逻辑
            }
        }
    }
}
```

### 添加新的规则节点

```java
public class TransformNode implements RuleNode {
    private RuleNode next;
    
    @Override
    public void onMsg(TbMsg msg) {
        // 数据转换逻辑
        List<TsKvEntry> transformed = new ArrayList<>();
        
        for (TsKvEntry entry : msg.getTsKvEntries()) {
            // 温度：摄氏度 → 华氏度
            if (entry.getKey().equals("temperature")) {
                double celsius = entry.getDoubleValue().get();
                double fahrenheit = celsius * 9/5 + 32;
                transformed.add(new BasicTsKvEntry(
                    entry.getTs(),
                    new DoubleDataEntry("temperature_f", fahrenheit)
                ));
            }
        }
        
        // 添加转换后的数据
        msg.getTsKvEntries().addAll(transformed);
        
        if (next != null) {
            next.onMsg(msg);
        }
    }
}
```

## 📈 实际监控示例

### 示例1: 监控 Prometheus 自身

**配置**:
```java
DeviceProfile.builder()
    .name("Prometheus 进程监控")
    .addTelemetryDefinition(prometheus("cpu", "process_cpu_seconds_total"))
    .addTelemetryDefinition(prometheus("memory", "go_memstats_alloc_bytes"))
    .addTelemetryDefinition(prometheus("goroutines", "go_goroutines"));
```

**结果**:
```
[2025-10-24 15:47:13] cpu_seconds_total=1.013719 (DOUBLE)
[2025-10-24 15:47:13] memory_alloc_bytes=15994384 (LONG)
[2025-10-24 15:47:13] goroutines=39 (LONG)
```

### 示例2: 监控系统资源（node_exporter）

**配置**:
```java
DeviceProfile.builder()
    .name("系统资源监控")
    .addTelemetryDefinition(prometheus(
        "system_cpu_usage",
        "avg(rate(node_cpu_seconds_total{mode!=\"idle\"}[1m]))"  // 复杂PromQL
    ))
    .addTelemetryDefinition(prometheus(
        "memory_usage_percent",
        "(1 - node_memory_free_bytes / node_memory_total_bytes) * 100"  // 计算表达式
    ));
```

**结果**:
```
[2025-10-24 15:47:23] system_cpu_usage=0.08718 (DOUBLE)         - 8.7% CPU使用
[2025-10-24 15:47:23] memory_total_bytes=17179869184 (LONG)     - 16GB 总内存
[2025-10-24 15:47:23] memory_free_bytes=445841408 (LONG)        - 445MB 空闲
[2025-10-24 15:47:23] memory_usage_percent=97.40 (DOUBLE)       - 97.4% 使用率
```

### 示例3: MQTT 温湿度传感器

**配置**:
```java
DeviceProfile.builder()
    .name("温湿度传感器")
    .dataSourceType(DataSourceType.MQTT)
    .strictMode(false);
```

**发送**:
```bash
mosquitto_pub -h localhost -p 1883 -u token-001 \
  -t v1/devices/me/telemetry \
  -m '{"temperature":25.5,"humidity":60}'
```

**结果**:
```
[2025-10-24 15:47:30] temperature=25.5 (DOUBLE)
[2025-10-24 15:47:30] humidity=60 (LONG)
```

## 🔍 故障排查

### 查看日志
```bash
# 查看完整日志
cat /tmp/minitb_test.log

# 实时日志
tail -f /tmp/minitb_test.log
```

### 常见问题

**Q: MQTT 连接失败？**
```bash
# 检查端口是否被占用
lsof -i :1883

# 停止旧进程
pkill -f "com.minitb.MiniTBApplication"
```

**Q: Prometheus 拉取失败？**
```bash
# 检查 Prometheus 是否运行
curl http://localhost:9090/api/v1/query?query=up

# 检查 node_exporter 是否运行
curl http://localhost:9100/metrics | head
```

**Q: 数据没有保存？**
```bash
# 检查数据目录
ls -lh minitb/data/

# 查看存储日志
grep "保存遥测数据" /tmp/minitb_test.log
```

## 🚀 生产部署建议

虽然 MiniTB 主要用于学习和轻量级部署，但如需生产使用可考虑：

1. **存储层升级**: 替换为 PostgreSQL/TimescaleDB
2. **消息队列**: 引入 Kafka/RabbitMQ 解耦
3. **分布式**: 支持多实例部署
4. **持久化配置**: DeviceProfile 存储到数据库
5. **安全增强**: TLS/SSL、OAuth2 认证
6. **监控告警**: 集成 Grafana 可视化

## 📚 技术栈

- **Java**: 17
- **构建工具**: Maven
- **网络库**: Netty (MQTT)、Java HTTP Client (Prometheus)
- **JSON解析**: Gson
- **日志**: SLF4J + Logback
- **代码简化**: Lombok

## 🎯 使用场景

- ✅ **IoT 数据采集**: MQTT 设备数据实时采集
- ✅ **系统监控**: Prometheus 指标拉取与处理
- ✅ **数据处理**: 规则引擎实现业务逻辑
- ✅ **多租户管理**: 租户隔离、设备管理
- ✅ **层级建模**: 资产-设备关系管理
- ✅ **学习研究**: 理解 IoT 平台核心架构
- ✅ **快速原型**: 小规模 IoT 项目验证

## 📦 项目统计

- **总文件数**: 41 个 Java 文件
- **代码行数**: ~2000 行
- **核心模块**: 7 个（entity, kv, msg, transport, rule, storage, relation）
- **支持协议**: MQTT, Prometheus（HTTP 扩展中）
- **数据类型**: 5 种（BOOLEAN, LONG, DOUBLE, STRING, JSON）
- **规则节点**: 3 种内置（可扩展）

---

**MiniTB - 小而美的物联网数据平台，专注核心功能，易于理解和扩展！**
