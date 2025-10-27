# MiniTB Web API 文档

## 🌐 访问地址

```
Web 界面: http://localhost:8080
REST API: http://localhost:8080/api/*
```

---

## 📡 API 端点

### **1. 设备管理 API**

#### **获取所有设备**
```http
GET /api/devices
```

**响应示例：**
```json
[
  {
    "id": "ffef486c-7879-4068-9bc6-208c3e132829",
    "name": "NVIDIA TITAN V - GPU 0",
    "type": "NVIDIA_GPU",
    "prometheusLabel": "gpu=0",
    "createdTime": 1761572057610
  },
  {
    "id": "33661981-9aa4-4bb9-907c-a34e64aef8ed",
    "name": "NVIDIA TITAN V - GPU 1",
    "type": "NVIDIA_GPU",
    "prometheusLabel": "gpu=1",
    "createdTime": 1761572057610
  }
]
```

---

### **2. 遥测数据 API**

#### **获取设备最新遥测数据**
```http
GET /api/telemetry/{deviceId}/latest
```

**响应示例：**
```json
{
  "deviceId": "ffef486c-7879-4068-9bc6-208c3e132829",
  "deviceName": "NVIDIA TITAN V - GPU 0",
  "timestamp": 1761572158303,
  "telemetry": {
    "gpu_utilization": 100,
    "gpu_temperature": 74,
    "memory_temperature": 82,
    "power_usage": 165.419,
    "memory_used": 614,
    "memory_free": 11442,
    "memory_copy_utilization": 100
  }
}
```

#### **获取指标历史数据**
```http
GET /api/telemetry/{deviceId}/history/{key}?duration={seconds}
```

**参数：**
- `deviceId`: 设备 ID
- `key`: 指标名称（如 `gpu_temperature`）
- `duration`: 时间范围（秒），默认 60

**响应示例：**
```json
[
  {
    "timestamp": 1761572158303,
    "key": "gpu_temperature",
    "value": 74,
    "dataType": "LONG"
  },
  {
    "timestamp": 1761572160363,
    "key": "gpu_temperature",
    "value": 74,
    "dataType": "LONG"
  }
]
```

#### **获取设备遥测摘要**
```http
GET /api/telemetry/{deviceId}/summary
```

**响应示例：**
```json
{
  "gpu_utilization": 100,
  "gpu_temperature": 74,
  "power_usage": 165.419,
  "memory_used": 614,
  "memory_free": 11442
}
```

---

## 📊 支持的遥测指标

### **GPU 指标**

| 指标名 | 说明 | 单位 | 数据类型 |
|--------|------|------|----------|
| `gpu_utilization` | GPU 计算利用率 | % | LONG |
| `gpu_temperature` | GPU 核心温度 | °C | LONG |
| `memory_temperature` | 显存温度 | °C | LONG |
| `power_usage` | 实时功耗 | W | DOUBLE |
| `memory_used` | 已用显存 | MiB | LONG |
| `memory_free` | 空闲显存 | MiB | LONG |
| `memory_copy_utilization` | PCIe 传输利用率 | % | LONG |

---

## 🔧 使用示例

### **cURL 示例**

```bash
# 获取所有设备
curl http://localhost:8080/api/devices

# 获取 GPU 0 最新数据
DEVICE_ID="ffef486c-7879-4068-9bc6-208c3e132829"
curl "http://localhost:8080/api/telemetry/$DEVICE_ID/latest"

# 获取 GPU 温度历史（最近 30 秒）
curl "http://localhost:8080/api/telemetry/$DEVICE_ID/history/gpu_temperature?duration=30"

# 获取功耗历史（最近 60 秒）
curl "http://localhost:8080/api/telemetry/$DEVICE_ID/history/power_usage?duration=60"
```

### **JavaScript 示例**

```javascript
// 获取设备列表
const devices = await fetch('/api/devices').then(r => r.json());

// 获取第一个设备的最新数据
const deviceId = devices[0].id;
const latest = await fetch(`/api/telemetry/${deviceId}/latest`).then(r => r.json());

console.log('GPU 温度:', latest.telemetry.gpu_temperature, '°C');
console.log('GPU 利用率:', latest.telemetry.gpu_utilization, '%');

// 获取温度历史（最近 60 秒）
const history = await fetch(
  `/api/telemetry/${deviceId}/history/gpu_temperature?duration=60`
).then(r => r.json());

// 绘制图表
history.forEach(point => {
  console.log(new Date(point.timestamp), point.value);
});
```

---

## 🎨 Web 界面功能

### **实时监控卡片**
- ✅ GPU 利用率
- ✅ GPU 温度
- ✅ 显存温度
- ✅ 功耗
- ✅ 已用显存
- ✅ 空闲显存
- ✅ PCIe 利用率
- ✅ 显存使用率（计算值）

### **趋势图表**
- ✅ 温度趋势（最近 60 秒）
- ✅ GPU 利用率趋势（最近 60 秒）
- ✅ 功耗趋势（最近 60 秒）

### **交互功能**
- ✅ GPU 设备切换（GPU 0 / GPU 1）
- ✅ 实时刷新（每 2 秒）
- ✅ 响应式设计

---

## 🚀 快速启动

```bash
# 启动 MiniTB
./start-gpu-monitor.sh

# 或手动启动
mvn spring-boot:run

# 浏览器访问
open http://localhost:8080
```

---

## ⚡ 性能特点

- **低延迟**: API 响应 < 10ms（内存查询）
- **高频更新**: 每 2 秒拉取新数据
- **轻量级**: 单页应用，无需额外依赖
- **实时性**: 数据流 Prometheus → Actor → Storage → API → Web

---

## 🔒 安全注意事项

**当前实现：**
- ⚠️ 无认证（开发模式）
- ⚠️ 无 HTTPS

**生产环境建议：**
- ✅ 添加 Spring Security
- ✅ 启用 HTTPS
- ✅ 添加访问控制
- ✅ API 限流

