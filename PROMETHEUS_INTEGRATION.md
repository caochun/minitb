# Prometheus集成说明

## 功能概述

MiniTB现在支持从Prometheus**拉取**设备数据，而不仅仅是通过MQTT**推送**数据。这个功能适用于以下场景：

- 设备本身暴露Prometheus格式的metrics端点
- 已有Prometheus服务器收集设备数据
- 需要从监控系统导入历史数据
- 混合数据源场景（实时MQTT + 定时Prometheus）

## 架构设计

### 数据流对比

**MQTT推送模式（现有）**:
```
Device → MQTT(1883) → MqttTransportHandler → TransportService → RuleEngine → Storage
触发: 设备主动上报
特点: 实时性高（毫秒级）
```

**Prometheus拉取模式（新增）**:
```
Device → Prometheus(/metrics) ← Prometheus Server(scrape)
            ↓
    PrometheusDataPuller (定时查询 /api/v1/query)
            ↓
    TransportService → RuleEngine → Storage
触发: MiniTB定时拉取
特点: 适合批量数据、历史数据
```

### 核心组件

#### 1. PrometheusDataPuller
- **职责**: 定时从Prometheus查询设备指标数据
- **配置**: Prometheus URL、拉取间隔、设备列表
- **输出**: 将Prometheus数据转换为JSON格式，注入到TransportService

#### 2. DeviceMetricConfig
- **职责**: 设备指标配置
- **字段**:
  - `deviceId`: Prometheus中的设备标识
  - `accessToken`: MiniTB中的设备令牌
  - `metrics`: 需要拉取的指标列表

#### 3. TransportService（改进）
- **新增**: 注册Prometheus数据源设备
- **兼容**: 同时支持MQTT和Prometheus设备

## 使用指南

### 前置条件

#### 方式A: 使用Mock服务器（快速测试）

```bash
# 启动Mock Prometheus服务器
cd minitb
./test-prometheus-mock.sh
```

Mock服务器提供：
- `/api/v1/query` - Prometheus查询API
- `/metrics` - Prometheus格式的指标端点
- 自动生成随机的温度和湿度数据

#### 方式B: 使用真实Prometheus

1. 部署Prometheus服务器
2. 配置设备暴露metrics端点
3. 配置Prometheus scrape设备数据

**设备metrics示例** (http://device-ip:9100/metrics):
```prometheus
# HELP temperature Device temperature in celsius
# TYPE temperature gauge
temperature{device_id="prom-device-001",device_name="温度传感器"} 25.5

# HELP humidity Device humidity percentage  
# TYPE humidity gauge
humidity{device_id="prom-device-001",device_name="温度传感器"} 62.0
```

**Prometheus配置** (prometheus.yml):
```yaml
scrape_configs:
  - job_name: 'iot-devices'
    scrape_interval: 15s
    static_configs:
      - targets: ['device-ip:9100']
```

### 启动MiniTB

```bash
# 默认配置（连接localhost:9090，每30秒拉取一次）
cd minitb
./run.sh

# 自定义配置
export PROMETHEUS_URL=http://your-prometheus:9090
export PROMETHEUS_PULL_INTERVAL=10  # 10秒拉取一次
./run.sh
```

### 查看日志

启动日志会显示：
```
[6/6] 启动Prometheus数据拉取器...
Prometheus数据拉取器初始化完成，目标: http://localhost:9090
注册Prometheus数据源设备: deviceId=prom-device-001, token=test-token-prom, 指标=[temperature, humidity]
启动Prometheus数据拉取任务，间隔: 30秒
Prometheus数据拉取器已启动:
  - 目标地址: http://localhost:9090
  - 拉取间隔: 30秒
  - 注册设备: 1 个
```

拉取数据日志：
```
从Prometheus拉取到设备数据: deviceId=prom-device-001, data={"temperature":25.3,"humidity":62.1}
接收到遥测数据: token=test-token-prom, data={"temperature":25.3,"humidity":62.1}
设备认证成功: 温度传感器-Prom
创建TbMsg: TbMsg(id=xxx, type=POST_TELEMETRY_REQUEST, ...)
发送消息到规则引擎: xxx
```

### 查看存储的数据

```bash
# 实时查看
tail -f minitb/data/telemetry_*.log

# 示例输出
[2025-10-23 21:45:00] {"temperature":25.3,"humidity":62.1}
[2025-10-23 21:45:30] {"temperature":26.1,"humidity":58.7}
[2025-10-23 21:46:00] {"temperature":24.8,"humidity":63.2}
```

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 | 示例 |
|--------|------|--------|------|
| `PROMETHEUS_URL` | Prometheus服务地址 | `http://localhost:9090` | `http://192.168.1.100:9090` |
| `PROMETHEUS_PULL_INTERVAL` | 拉取间隔（秒） | `30` | `10`, `60`, `300` |

### 代码配置

在`MiniTBApplication.java`中修改：

```java
// 注册需要拉取的设备
promPuller.registerDevice(
    "your-device-id",           // Prometheus中的device_id标签值
    "your-device-token",        // MiniTB中的设备token
    Arrays.asList("metric1", "metric2", "metric3")  // 指标列表
);
```

## API说明

### PrometheusDataPuller

#### 构造函数
```java
public PrometheusDataPuller(String prometheusUrl, TransportService transportService)
```

#### 注册设备
```java
public void registerDevice(String deviceId, String accessToken, List<String> metrics)
```

#### 启动拉取
```java
public void start(int intervalSeconds)
```

#### 关闭
```java
public void shutdown()
```

### Prometheus查询格式

PrometheusDataPuller使用以下PromQL格式查询：
```
metric_name{device_id="device-id-value"}
```

例如:
```
temperature{device_id="prom-device-001"}
humidity{device_id="prom-device-001"}
```

## 数据处理流程

1. **定时触发**: ScheduledExecutorService每N秒触发一次
2. **查询Prometheus**: 对每个注册设备的每个指标调用`/api/v1/query`
3. **解析响应**: 提取JSON响应中的数值
4. **组装数据**: 将多个指标组合为一个JSON对象
5. **注入TransportService**: 调用`processTelemetry(token, json)`
6. **后续流程**: 与MQTT数据完全相同，进入规则引擎

### 数据格式转换

**Prometheus响应**:
```json
{
  "status": "success",
  "data": {
    "result": [{
      "metric": {"device_id": "prom-device-001"},
      "value": [1698765432, "25.5"]
    }]
  }
}
```

**转换为MiniTB格式**:
```json
{
  "temperature": 25.5,
  "humidity": 62.0
}
```

## 故障排查

### 问题1: 连接Prometheus失败

**错误日志**:
```
HTTP请求失败: Connection refused
```

**解决方案**:
1. 检查Prometheus是否运行: `curl http://localhost:9090/api/v1/query`
2. 检查防火墙设置
3. 验证PROMETHEUS_URL配置

### 问题2: 查询无数据

**错误日志**:
```
设备 prom-device-001 无最新数据
指标 temperature 查询结果为空
```

**解决方案**:
1. 在Prometheus UI中验证指标存在: http://localhost:9090/graph
2. 检查PromQL查询: `temperature{device_id="prom-device-001"}`
3. 确认device_id标签值正确

### 问题3: 设备认证失败

**错误日志**:
```
设备认证失败: token=xxx
```

**解决方案**:
1. 确认在TransportService中注册了该token的设备
2. 检查registerDevice()的accessToken参数

## 性能考虑

### 拉取间隔选择

| 场景 | 推荐间隔 | 说明 |
|------|----------|------|
| 实时监控 | 10-30秒 | 平衡实时性和负载 |
| 常规采集 | 30-60秒 | 常规场景 |
| 批量分析 | 5-15分钟 | 减少Prometheus负载 |

### 设备数量

- **单设备**: 无需特殊优化
- **10-100设备**: 增加拉取间隔或使用多线程
- **100+设备**: 考虑分片部署多个MiniTB实例

### 指标数量

每次拉取会对每个指标发起一次HTTP请求：
- 2个指标 → 2次请求/设备/周期
- 10个指标 → 10次请求/设备/周期

可以优化为批量查询（需修改PromQL）。

## 扩展建议

### 1. 批量查询优化

修改PromQL使用OR操作符：
```
{device_id="xxx", __name__=~"temperature|humidity|pressure"}
```

### 2. 错误重试机制

添加指数退避重试：
```java
@Retry(maxAttempts = 3, delay = 1000)
private String httpGet(String url) { ... }
```

### 3. 指标缓存

避免重复查询不变的数据：
```java
private final Map<String, CachedValue> metricsCache;
```

### 4. 动态设备注册

从配置文件或API动态加载设备列表。

## 与ThingsBoard对比

| 特性 | ThingsBoard | MiniTB |
|------|-------------|--------|
| 数据源 | MQTT, HTTP, CoAP, LWM2M | MQTT, Prometheus |
| Prometheus集成 | 作为数据导出目标 | 作为数据源拉取 |
| 复杂度 | 生产级 | 教学简化版 |
| 配置方式 | UI + 配置文件 | 代码 + 环境变量 |

## 总结

通过添加Prometheus数据拉取功能，MiniTB现在支持：

✅ **双数据源**: MQTT推送 + Prometheus拉取  
✅ **统一处理**: 所有数据都进入相同的RuleEngine处理流程  
✅ **灵活配置**: 环境变量控制Prometheus地址和拉取间隔  
✅ **易于测试**: 提供Mock服务器快速验证  

这展示了现代IoT平台如何集成多种数据源，统一数据处理流程的架构设计。

