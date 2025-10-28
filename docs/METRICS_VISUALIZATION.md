# MiniTB 监控指标可视化指南

## 📊 Web UI 查看指标

### 启动应用
```bash
cd minitb
mvn spring-boot:run
```

### 访问 Web UI
打开浏览器访问: `http://localhost:8080`

### API 端点

#### 1. 获取所有设备
```bash
curl http://localhost:8080/api/devices | jq
```

#### 2. 获取设备最新遥测数据
```bash
# GPU 设备
curl http://localhost:8080/api/telemetry/{deviceId}/latest | jq

# 示例响应 (17 个 GPU 指标):
{
  "gpu_utilization": 45.0,
  "memory_copy_utilization": 15.0,
  "gpu_temperature": 68.0,
  "memory_temperature": 65.0,
  "power_usage": 75.0,
  "memory_used": 8192.0,
  "memory_free": 4096.0,
  "sm_clock": 1200.0,
  "memory_clock": 850.0,
  "sm_utilization": 42.0,
  "pcie_tx_throughput": 1024.0,
  "pcie_rx_throughput": 512.0,
  "ecc_sbe_aggregate": 0.0,
  "ecc_dbe_aggregate": 0.0,
  "power_limit": 250.0,
  "fan_speed": 65.0,
  "nvlink_bandwidth": 2048.0
}
```

#### 3. 获取历史数据
```bash
curl "http://localhost:8080/api/telemetry/{deviceId}/history/gpu_temperature?startTime=0&endTime=9999999999999" | jq
```

#### 4. 获取数据汇总
```bash
curl http://localhost:8080/api/telemetry/{deviceId}/summary | jq
```

---

## 🎨 Chart.js 图表展示

### GPU 监控面板

#### 温度监控
```javascript
// 实时监控 GPU 和显存温度
const tempChart = new Chart(ctx, {
  type: 'line',
  data: {
    datasets: [
      { label: 'GPU 温度', data: [] },
      { label: '显存温度', data: [] }
    ]
  }
});
```

#### 性能监控
```javascript
// 监控 GPU 利用率、SM 利用率、时钟频率
const perfChart = new Chart(ctx, {
  type: 'line',
  data: {
    datasets: [
      { label: 'GPU 利用率 (%)', data: [] },
      { label: 'SM 利用率 (%)', data: [] },
      { label: 'SM 时钟 (MHz)', data: [], yAxisID: 'clock' }
    ]
  }
});
```

#### PCIe 吞吐量
```javascript
// 监控 PCIe TX/RX 带宽
const pcieChart = new Chart(ctx, {
  type: 'line',
  data: {
    datasets: [
      { label: 'TX 吞吐量 (KB/s)', data: [] },
      { label: 'RX 吞吐量 (KB/s)', data: [] }
    ]
  }
});
```

#### ECC 错误统计
```javascript
// 累计 ECC 错误
const eccChart = new Chart(ctx, {
  type: 'bar',
  data: {
    labels: ['单比特错误', '双比特错误'],
    datasets: [{
      label: 'ECC 错误次数',
      data: [sbe_count, dbe_count]
    }]
  }
});
```

### BMC 监控面板

#### 温度分布
```javascript
// 多点温度监控
const tempDistChart = new Chart(ctx, {
  type: 'line',
  data: {
    datasets: [
      { label: 'CPU0 温度', data: [] },
      { label: 'CPU1 温度', data: [] },
      { label: '主板温度', data: [] },
      { label: '内存温度', data: [] },
      { label: '进风口温度', data: [] },
      { label: '出风口温度', data: [] }
    ]
  }
});
```

#### 风扇转速
```javascript
// 所有风扇转速
const fanChart = new Chart(ctx, {
  type: 'line',
  data: {
    datasets: [
      { label: 'CPU0 风扇', data: [] },
      { label: 'CPU1 风扇', data: [] },
      { label: '系统风扇1', data: [] },
      { label: '系统风扇2', data: [] }
    ]
  }
});
```

#### 电源监控
```javascript
// PSU 功率和电压
const powerChart = new Chart(ctx, {
  type: 'line',
  data: {
    datasets: [
      { label: 'PSU1 功率 (W)', data: [], yAxisID: 'power' },
      { label: 'PSU2 功率 (W)', data: [], yAxisID: 'power' },
      { label: '12V 电压', data: [], yAxisID: 'voltage' },
      { label: '5V 电压', data: [], yAxisID: 'voltage' },
      { label: '3.3V 电压', data: [], yAxisID: 'voltage' }
    ]
  },
  options: {
    scales: {
      power: { type: 'linear', position: 'left' },
      voltage: { type: 'linear', position: 'right' }
    }
  }
});
```

---

## 📈 Prometheus 查询示例

### GPU 指标 PromQL

```promql
# 1. GPU 利用率趋势
DCGM_FI_DEV_GPU_UTIL{gpu="0"}

# 2. SM 时钟频率
DCGM_FI_DEV_SM_CLOCK{gpu="0"}

# 3. 显存时钟频率
DCGM_FI_DEV_MEM_CLOCK{gpu="0"}

# 4. PCIe 总带宽 (TX + RX)
DCGM_FI_PROF_PCIE_TX_BYTES{gpu="0"} + DCGM_FI_PROF_PCIE_RX_BYTES{gpu="0"}

# 5. 功耗占比
(DCGM_FI_DEV_POWER_USAGE{gpu="0"} / DCGM_FI_DEV_POWER_MGMT_LIMIT{gpu="0"}) * 100

# 6. ECC 错误率 (单比特)
rate(DCGM_FI_DEV_ECC_SBE_AGG_TOTAL{gpu="0"}[5m])

# 7. NVLink 带宽 (如果支持)
DCGM_FI_PROF_NVLINK_TX_BYTES{gpu="0"} + DCGM_FI_PROF_NVLINK_RX_BYTES{gpu="0"}
```

### Grafana Dashboard JSON (示例)

```json
{
  "dashboard": {
    "title": "MiniTB GPU 监控",
    "panels": [
      {
        "title": "GPU 利用率",
        "targets": [
          { "expr": "DCGM_FI_DEV_GPU_UTIL{gpu='0'}" }
        ]
      },
      {
        "title": "SM 时钟频率",
        "targets": [
          { "expr": "DCGM_FI_DEV_SM_CLOCK{gpu='0'}" }
        ]
      },
      {
        "title": "PCIe 吞吐量",
        "targets": [
          { "expr": "DCGM_FI_PROF_PCIE_TX_BYTES{gpu='0'}", "legendFormat": "TX" },
          { "expr": "DCGM_FI_PROF_PCIE_RX_BYTES{gpu='0'}", "legendFormat": "RX" }
        ]
      }
    ]
  }
}
```

---

## 🛠️ IPMI 命令行查询

### 查看所有传感器
```bash
ipmitool -I lanplus -H 114.212.81.58 -U admin -P <password> sensor list
```

### 查看特定传感器
```bash
# CPU 温度
ipmitool -I lanplus -H 114.212.81.58 -U admin -P <password> sensor get CPU0_TEMP

# 风扇转速
ipmitool -I lanplus -H 114.212.81.58 -U admin -P <password> sensor get CPU0_FAN

# PSU 功率
ipmitool -I lanplus -H 114.212.81.58 -U admin -P <password> sensor get PSU1_PIN
```

### 批量查询脚本
```bash
#!/bin/bash
BMC_HOST="114.212.81.58"
BMC_USER="admin"
BMC_PASS="<password>"

SENSORS=(
  "CPU0_TEMP" "CPU1_TEMP" "MB_TEMP" "INLET_TEMP" "OUTLET_TEMP"
  "CPU0_FAN" "CPU1_FAN" "SYS_FAN1" "SYS_FAN2"
  "P_12V" "P_5V" "P_3V3" "CPU0_VCORE" "CPU1_VCORE"
  "PSU1_PIN" "PSU2_PIN"
)

for sensor in "${SENSORS[@]}"; do
  echo "=== $sensor ==="
  ipmitool -I lanplus -H $BMC_HOST -U $BMC_USER -P $BMC_PASS sensor get $sensor
  echo
done
```

---

## 📊 数据分析示例

### GPU 性能分析

#### 计算 GPU 效率
```python
import requests

device_id = "<gpu_device_id>"
telemetry = requests.get(f"http://localhost:8080/api/telemetry/{device_id}/latest").json()

gpu_util = telemetry["gpu_utilization"]
sm_util = telemetry["sm_utilization"]
power_usage = telemetry["power_usage"]
power_limit = telemetry["power_limit"]

# 计算功耗效率 (利用率 / 功耗占比)
power_ratio = power_usage / power_limit
efficiency = gpu_util / power_ratio
print(f"GPU 效率: {efficiency:.2f}% / %功耗")
```

#### 检查 ECC 健康状态
```python
sbe = telemetry["ecc_sbe_aggregate"]
dbe = telemetry["ecc_dbe_aggregate"]

if dbe > 0:
    print("⚠️ 警告: 检测到双比特 ECC 错误，可能导致数据损坏！")
elif sbe > 100:
    print("⚠️ 注意: 单比特 ECC 错误较多，建议检查硬件")
else:
    print("✅ ECC 状态正常")
```

### BMC 健康分析

#### 热管理检查
```python
telemetry = requests.get(f"http://localhost:8080/api/telemetry/{bmc_device_id}/latest").json()

cpu0_temp = telemetry["cpu0_temperature"]
cpu1_temp = telemetry["cpu1_temperature"]
inlet_temp = telemetry["inlet_temperature"]
outlet_temp = telemetry["outlet_temperature"]

# 温差分析
temp_rise = outlet_temp - inlet_temp
print(f"机箱温升: {temp_rise:.1f}°C")

if temp_rise > 20:
    print("⚠️ 警告: 机箱温升过大，散热可能不足")
elif cpu0_temp > 80 or cpu1_temp > 80:
    print("⚠️ 警告: CPU 温度过高")
else:
    print("✅ 热管理正常")
```

#### 电源健康检查
```python
psu1_power = telemetry["psu1_input_power"]
psu2_power = telemetry["psu2_input_power"]
voltage_12v = telemetry["voltage_12v"]
voltage_5v = telemetry["voltage_5v"]
voltage_3_3v = telemetry["voltage_3_3v"]

# 检查电压偏差
def check_voltage(actual, nominal, tolerance=0.05):
    deviation = abs(actual - nominal) / nominal
    return deviation <= tolerance

if not check_voltage(voltage_12v, 12.0):
    print("⚠️ 警告: 12V 电压偏差过大")
if not check_voltage(voltage_5v, 5.0):
    print("⚠️ 警告: 5V 电压偏差过大")
if not check_voltage(voltage_3_3v, 3.3, 0.1):
    print("⚠️ 警告: 3.3V 电压偏差过大")

# 检查 PSU 负载均衡
psu_diff = abs(psu1_power - psu2_power) / max(psu1_power, psu2_power)
if psu_diff > 0.2:
    print("⚠️ 注意: PSU 负载不均衡")
else:
    print("✅ 电源系统正常")
```

---

## 🔔 告警规则示例

### GPU 告警
```yaml
# 高温告警
- alert: GPUHighTemperature
  expr: DCGM_FI_DEV_GPU_TEMP > 80
  for: 5m
  annotations:
    summary: "GPU 温度过高 ({{ $value }}°C)"

# ECC 错误告警
- alert: GPUECCErrors
  expr: rate(DCGM_FI_DEV_ECC_DBE_AGG_TOTAL[5m]) > 0
  annotations:
    summary: "检测到 GPU ECC 双比特错误"

# 功耗超限
- alert: GPUPowerOverLimit
  expr: DCGM_FI_DEV_POWER_USAGE > DCGM_FI_DEV_POWER_MGMT_LIMIT
  annotations:
    summary: "GPU 功耗超过限制"
```

### BMC 告警
```yaml
# CPU 高温
- alert: CPUHighTemperature
  expr: cpu_temperature > 85
  for: 10m
  annotations:
    summary: "CPU 温度过高 ({{ $value }}°C)"

# 风扇故障
- alert: FanFailure
  expr: fan_speed < 300
  annotations:
    summary: "风扇转速异常，可能故障"

# 电压异常
- alert: VoltageAbnormal
  expr: abs(voltage_12v - 12) / 12 > 0.1
  annotations:
    summary: "12V 电压偏差超过 10%"
```

---

## 🎯 最佳实践

### 1. 监控策略
- **GPU 监控**: 2 秒拉取一次，适合实时性能分析
- **BMC 监控**: 30 秒拉取一次，减少 IPMI 开销
- **历史数据**: 保留 7 天，用于趋势分析

### 2. 数据可视化
- **实时图表**: 显示最近 5 分钟数据
- **历史趋势**: 提供 1 小时、1 天、1 周视图
- **聚合统计**: 显示平均值、峰值、最小值

### 3. 告警配置
- **多级告警**: 警告 (warning) / 严重 (critical)
- **抑制规则**: 避免告警风暴
- **通知渠道**: 邮件、Slack、钉钉

### 4. 性能优化
- **批量查询**: 一次 API 调用获取所有指标
- **缓存策略**: 前端缓存 1-2 秒
- **数据压缩**: 历史数据降采样存储

---

**文档更新**: 2025-10-28

