# 从本地Prometheus拉取CPU使用率演示

## 功能说明

MiniTB现在配置为从本地Prometheus服务拉取CPU使用率数据，展示如何将监控系统的数据集成到IoT平台。

## 数据流

```
Prometheus自身 → 暴露 /metrics 端点
    ↓ (Prometheus自我监控)
Prometheus存储 (process_cpu_seconds_total指标)
    ↓ (MiniTB每10秒查询)
PrometheusDataPuller查询 /api/v1/query
    ↓ (转换为JSON)
TransportService → RuleEngine → TelemetryStorage
```

## 配置详情

### 监控设备
- **设备ID**: `localhost:9090` (Prometheus instance)
- **MiniTB Token**: `test-token-prom`
- **指标**: `process_cpu_seconds_total` (CPU使用累计时间)

### 查询PromQL
```
process_cpu_seconds_total{instance="localhost:9090"}
```

### 拉取间隔
- 默认: 10秒
- 可通过环境变量 `PROMETHEUS_PULL_INTERVAL` 调整

## 启动步骤

### 1. 确保Prometheus运行
```bash
# 检查Prometheus是否运行
curl http://localhost:9090/api/v1/query?query=process_cpu_seconds_total

# 应该返回类似:
# {"status":"success","data":{"result":[{"value":[timestamp,"0.365507"]}]}}
```

### 2. 启动MiniTB
```bash
cd minitb
./run.sh

# 或者直接运行
mvn exec:java -Dexec.mainClass="com.minitb.MiniTBApplication"
```

### 3. 观察日志
MiniTB启动后会显示:
```
[6/6] 启动Prometheus数据拉取器...
Prometheus数据拉取器已启动:
  - 目标地址: http://localhost:9090
  - 拉取间隔: 10秒
  - 监控设备: Prometheus自身 (localhost:9090)
  - 拉取指标: process_cpu_seconds_total (CPU使用时间)
```

每10秒会看到拉取日志:
```
从Prometheus拉取到设备数据: deviceId=localhost:9090, data={"process_cpu_seconds_total":0.365507}
接收到遥测数据: token=test-token-prom, data={"process_cpu_seconds_total":0.365507}
...
保存遥测数据成功: deviceId=xxx, ts=1761227413126
```

### 4. 查看存储的数据
```bash
# 实时监控
tail -f minitb/data/telemetry_*.log

# 查看特定设备文件
cat minitb/data/telemetry_7405b92a-ec6f-47b3-8bf8-f370bbb9a4a9.log
```

输出示例:
```
[2025-10-23 21:50:13] {"process_cpu_seconds_total":0.365507}
[2025-10-23 21:50:23] {"process_cpu_seconds_total":0.365507}
[2025-10-23 21:50:33] {"process_cpu_seconds_total":0.37638}
[2025-10-23 21:50:43] {"process_cpu_seconds_total":0.387253}
```

## 数据解读

### process_cpu_seconds_total
- **含义**: Prometheus进程累计使用的CPU时间（秒）
- **类型**: Counter（递增计数器）
- **计算CPU使用率**: 
  ```
  rate(process_cpu_seconds_total[1m]) * 100  # 转换为百分比
  ```

### 示例分析
从日志中可以看到：
- 第1次: 0.365507秒
- 第2次: 0.365507秒 (10秒内CPU使用未增加)
- 第3次: 0.37638秒 (增加0.010873秒)
- 第4次: 0.387253秒 (增加0.010873秒)

**CPU使用率计算**:
```
(0.387253 - 0.37638) / 10秒 = 0.00108730秒/秒 = 0.1087%
```
说明Prometheus在这10秒内平均使用了约0.1%的CPU。

## 技术实现

### 关键代码

#### 1. 设备注册 (MiniTBApplication.java)
```java
promPuller.registerDevice(
    "localhost:9090",              // Prometheus instance
    "test-token-prom",             // MiniTB设备token
    Arrays.asList("process_cpu_seconds_total")
);
```

#### 2. PromQL查询 (PrometheusDataPuller.java)
```java
String promQL;
if (config.getDeviceId().contains(":")) {
    // 使用instance标签（Prometheus标准格式）
    promQL = String.format(
        "%s{instance=\"%s\"}", 
        metricName, 
        config.getDeviceId()
    );
}
```

#### 3. 数据流转
```java
// PrometheusDataPuller拉取数据后
transportService.processTelemetry(
    config.getAccessToken(),  // test-token-prom
    telemetryJson             // {"process_cpu_seconds_total":0.365507}
);
```

## 扩展场景

### 监控其他指标

修改`MiniTBApplication.java`可以拉取更多指标:

```java
// 监控Go内存使用
promPuller.registerDevice(
    "localhost:9090",
    "test-token-prom",
    Arrays.asList(
        "process_cpu_seconds_total",
        "go_memstats_alloc_bytes",
        "go_goroutines"
    )
);
```

### 监控外部设备

如果Prometheus监控了Node Exporter:
```java
promPuller.registerDevice(
    "192.168.1.100:9100",      // Node Exporter地址
    "test-token-node",
    Arrays.asList(
        "node_cpu_seconds_total",
        "node_memory_MemAvailable_bytes",
        "node_disk_io_time_seconds_total"
    )
);
```

### 调整拉取间隔

```bash
# 5秒拉取一次
export PROMETHEUS_PULL_INTERVAL=5
./run.sh

# 60秒拉取一次
export PROMETHEUS_PULL_INTERVAL=60
./run.sh
```

## 过滤规则说明

当前规则链包含 `FilterNode("temperature", 20.0)`:
- MQTT推送的temperature数据会被过滤（只保留>20的）
- Prometheus的CPU数据**不包含temperature字段**，直接通过过滤器
- 所有CPU数据都会被保存

日志中可以看到:
```
WARN  c.minitb.ruleengine.node.FilterNode - [FilterNode[temperature > 20.0]] 数据中不包含字段: temperature
```
这是正常的，表示CPU数据跳过了temperature过滤。

## 对比MQTT推送

### MQTT方式（设备主动上报）
```bash
mosquitto_pub -h localhost -p 1883 -u test-token-001 \
  -t v1/devices/me/telemetry \
  -m '{"temperature":25}'
```
- 特点: 实时、低延迟、需要设备联网
- 触发: 设备主动
- 频率: 任意

### Prometheus拉取（平台主动查询）
```
MiniTB → 定时查询 → Prometheus → 返回数据
```
- 特点: 批量、定时、利用现有监控
- 触发: 平台主动
- 频率: 固定间隔

## 总结

✅ **成功从本地Prometheus拉取CPU数据**  
✅ **数据每10秒自动更新**  
✅ **完整走完MiniTB数据流程**  
✅ **存储在本地文件中**  

这演示了如何将监控系统（Prometheus）与IoT平台（MiniTB）集成，实现统一的数据处理和存储。



