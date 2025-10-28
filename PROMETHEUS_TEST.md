# Prometheus 集成测试指南

## 📋 概述

MiniTB 支持从 Prometheus 拉取遥测数据，通过标签映射将数据关联到具体设备。

## 🎯 核心机制

### Push 模式 vs Pull 模式

| 模式 | 数据源 | 设备识别 | 关联方式 |
|------|--------|---------|---------|
| **Push** (MQTT/HTTP) | 设备主动推送 | AccessToken (设备连接时提供) | 直接关联 |
| **Pull** (Prometheus) | MiniTB 主动拉取 | prometheusLabel (标签映射) | AccessToken 虚拟化 |

### Prometheus 标签映射原理

```
1. DeviceProfile 配置:
   - prometheusEndpoint: "http://localhost:9090"
   - prometheusDeviceLabelKey: "instance"  ← 使用哪个标签识别设备

2. Device 配置:
   - accessToken: "prometheus-server-01"           ← 内部唯一标识
   - prometheusLabel: "instance=server-01:9100"    ← Prometheus 标签映射

3. PrometheusDataPuller 拉取流程:
   PromQL 查询 → 多个时间序列 → 标签过滤 → accessToken 关联 → processTelemetry()
```

## 🚀 快速开始

### 步骤 1: 搭建 Prometheus 环境

```bash
# 自动搭建（推荐）
./setup-prometheus-env.sh

# 或手动搭建
docker run -d -p 9100:9100 --name node-exporter prom/node-exporter
docker run -d -p 9090:9090 --name prometheus prom/prometheus
```

### 步骤 2: 配置 Prometheus 抓取

编辑 Prometheus 配置 `/tmp/prometheus/prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'node'
    static_configs:
      - targets: ['host.docker.internal:9100']  # Mac/Windows
      # 或 targets: ['172.17.0.1:9100']         # Linux
```

### 步骤 3: 运行测试

```bash
# 运行 Prometheus 集成测试（需要真实 Prometheus）
./test-prometheus-integration.sh

# 或使用 Maven（需要设置环境变量）
export PROMETHEUS_ENABLED=true
mvn test -Dtest=PrometheusDataPullerIntegrationTest
```

## 📊 测试用例

### 单元测试（不需要 Prometheus）

`PrometheusDataPullerTest` - **5 个测试**
- ✅ Prometheus 标签解析
- ✅ 标签匹配逻辑
- ✅ 设备类型过滤
- ✅ PrometheusQueryResult 创建
- ✅ TelemetryDefinition 检查

### 集成测试（需要 Prometheus）

`PrometheusDataPullerIntegrationTest` - **4 个测试**
- ✅ 从本地 Prometheus 拉取 CPU/内存/磁盘数据
- ✅ 单个 Prometheus 查询验证
- ✅ 多次拉取周期测试
- ✅ 标签映射正确性验证

**注意**: 集成测试需要设置 `PROMETHEUS_ENABLED=true` 才会执行

## 🔧 配置示例

### 创建 Prometheus 监控设备

```java
// 1. 创建 DeviceProfile
DeviceProfile profile = DeviceProfile.builder()
    .name("Server Monitor")
    .dataSourceType(DataSourceType.PROMETHEUS)
    .prometheusEndpoint("http://localhost:9090")       // Prometheus 地址
    .prometheusDeviceLabelKey("instance")              // 使用 instance 标签识别设备
    .telemetryDefinitions(Arrays.asList(
        TelemetryDefinition.builder()
            .key("cpu_usage_percent")
            .protocolConfig(PrometheusConfig.builder()
                .promQL("100 - (avg by (instance) (rate(node_cpu_seconds_total{mode='idle'}[5m])) * 100)")
                .build())
            .build()
    ))
    .build();

// 2. 创建 Device
Device device = Device.builder()
    .name("Server-01")
    .type("SERVER_MONITOR")
    .deviceProfileId(profile.getId())
    .accessToken("prometheus-server-01")              // 内部标识
    .prometheusLabel("instance=server-01:9100")       // Prometheus 标签映射
    .build();
```

### PrometheusDataPuller 工作流程

```
定时任务 (每30秒)
  ↓
1. 查询所有 Prometheus 设备
  ↓
2. 对每个设备:
   - 读取 DeviceProfile.telemetryDefinitions
   - 执行 PromQL 查询
   - Prometheus 返回:
     [
       {metric: {instance: "server-01:9100"}, value: 45.2},
       {metric: {instance: "server-02:9100"}, value: 32.1}
     ]
  ↓
3. 标签过滤:
   device.prometheusLabel = "instance=server-01:9100"
   → 匹配第一条数据: 45.2
  ↓
4. 调用统一入口:
   transportService.processTelemetry(
     "prometheus-server-01",  ← accessToken
     '{"cpu_usage_percent": 45.2}'
   )
  ↓
5. TransportService 认证:
   authenticateDevice("prometheus-server-01")
   → 找到 Device: Server-01
  ↓
6. 发送到 DeviceActor → RuleEngine → Storage
   最终保存: device_id = Server-01, key = cpu_usage_percent, value = 45.2
```

## 🧪 验证测试

### 1. 单元测试（始终运行）

```bash
mvn test -Dtest=PrometheusDataPullerTest
```

输出:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

### 2. 集成测试（需要 Prometheus 环境）

```bash
# 方式 1: 使用脚本
./test-prometheus-integration.sh

# 方式 2: 手动设置环境变量
export PROMETHEUS_ENABLED=true
mvn test -Dtest=PrometheusDataPullerIntegrationTest
```

输出示例:
```
========================================
✅ 测试环境初始化完成
========================================
📊 Prometheus: http://localhost:9090
📡 Node Exporter: localhost:9100
🖥️  设备: 本机服务器
🏷️  标签映射: instance=localhost:9100
========================================

🔄 开始拉取本机 Prometheus 数据...

📊 验证拉取的数据:

  ✓ CPU 使用率: 23.5%
  ✓ 内存使用率: 67.8%
  ✓ 磁盘使用率: 45.2%

✅ 所有指标拉取成功！
```

## 🧹 清理环境

```bash
docker stop prometheus-test node-exporter
docker rm prometheus-test node-exporter
rm -rf /tmp/prometheus
```

## 📝 常见问题

### Q1: 测试显示 "Prometheus 服务不可用"

**A**: 确保 Prometheus 运行在 `http://localhost:9090`

```bash
# 检查
curl http://localhost:9090/api/v1/status/config

# 启动
docker run -d -p 9090:9090 prom/prometheus
```

### Q2: 测试显示 "Node Exporter 不可用"

**A**: 确保 Node Exporter 运行在 `http://localhost:9100`

```bash
# 检查
curl http://localhost:9100/metrics

# 启动
docker run -d -p 9100:9100 prom/node-exporter
```

### Q3: Prometheus 查询不到 Node Exporter 数据

**A**: 检查 Prometheus 配置中的 target 地址

```yaml
# Mac/Windows Docker
targets: ['host.docker.internal:9100']

# Linux Docker
targets: ['172.17.0.1:9100']

# 宿主机直接运行 Node Exporter
targets: ['localhost:9100']
```

### Q4: 集成测试被跳过

**A**: 需要设置环境变量 `PROMETHEUS_ENABLED=true`

```bash
export PROMETHEUS_ENABLED=true
mvn test -Dtest=PrometheusDataPullerIntegrationTest
```

## 📖 相关文档

- Prometheus 查询文档: https://prometheus.io/docs/prometheus/latest/querying/basics/
- Node Exporter 指标: https://github.com/prometheus/node_exporter
- PromQL 示例: https://prometheus.io/docs/prometheus/latest/querying/examples/



