# MiniTB - 轻量级物联网数据平台

MiniTB 是一个基于 **Spring Boot + Actor 模型 + 六边形架构** 的轻量级物联网（IoT）数据采集与处理平台，采用 **DDD（领域驱动设计）**，专注于核心数据流的高效处理。

**核心特点**: Spring Boot 3.2 | Actor 异步架构 | 六边形架构 | 强类型数据系统 | Prometheus 数据拉取 | GPU 监控 | Web 可视化 | 完整测试覆盖

---

## 📋 目录

- [快速开始](#-快速开始)
- [GPU 监控案例](#-gpu-监控案例---完整示例)
- [核心组件](#-核心组件)
- [数据流程](#-数据流程)
- [六边形架构](#️-六边形架构)
- [项目结构](#-项目结构)
- [测试](#-测试)
- [技术栈](#-技术栈)

---

## 🚀 快速开始

### 环境要求

```bash
# Java 17（必须）
java -version  # 确认 Java 17

# Maven 3.6+
mvn -version

# 可选：MQTT 客户端
brew install mosquitto

# 可选：Prometheus（用于 GPU 监控）
# 需要 DCGM Exporter for NVIDIA GPUs
```

### 启动应用

```bash
# 1. 编译
cd minitb
mvn clean install

# 2. 启动（使用 SQLite 存储）
mvn spring-boot:run

# 或使用快速启动脚本
./start-gpu-monitor.sh
```

### 访问 Web 界面

```bash
# GPU 监控界面（实时图表）
http://localhost:8080

# 设备列表 API
http://localhost:8080/api/devices

# 遥测数据 API
http://localhost:8080/api/telemetry/{deviceId}/latest
```

启动后会看到：

```
╔════════════════════════════════════════════════════════╗
║         MiniTB GPU 监控系统启动                         ║
╚════════════════════════════════════════════════════════╝

✅ SQLite 数据库初始化完成
✅ Actor 系统已创建 (5 threads)
✅ 规则链初始化完成: Root Rule Chain (5 nodes)
✅ 2 个设备 Actor 已创建
✅ MQTT 服务器启动成功 (端口 1883)
✅ Prometheus 数据拉取已启动 (每 2 秒)

🌐 Web 界面: http://localhost:8080
📊 监控设备: 2 块 NVIDIA TITAN V GPU
```

---

## 🎯 GPU 监控案例 - 完整示例

这是一个完整的、生产级的 GPU 监控系统实现，展示了如何使用 MiniTB 从定义设备到前端展示的全流程。

### 场景说明

**目标**: 监控 2 块 NVIDIA TITAN V GPU，实时显示 7 个核心指标
- GPU 利用率、内存拷贝带宽利用率
- GPU 温度、显存温度
- 功耗、已用显存、空闲显存

**数据源**: Prometheus + DCGM Exporter (`http://192.168.30.134:9090`)

**更新频率**: 每 2 秒自动拉取

### 第一步：定义 DeviceProfile（设备配置模板）

`DeviceProfile` 定义了一类设备的通用配置，包括数据源类型、遥测指标定义、协议配置等。

```java
// DataInitializer.java
DeviceProfile gpuProfile = DeviceProfile.builder()
    .id(DeviceProfileId.random())
    .name("GPU 监控配置")
    .description("NVIDIA GPU 监控配置 (DCGM)")
    
    // 数据源类型：Prometheus
    .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
    .prometheusEndpoint("http://192.168.30.134:9090")  // Prometheus 服务器地址
    .prometheusDeviceLabelKey("gpu")  // 用于区分不同 GPU 的标签 key
    
    // 遥测指标定义（7 个指标）
    .telemetryDefinitions(createGpuTelemetryDefinitions())
    .build();

// 保存到数据库
DeviceProfile savedProfile = deviceService.saveProfile(gpuProfile);
```

**遥测指标定义示例**:

```java
private List<TelemetryDefinition> createGpuTelemetryDefinitions() {
    List<TelemetryDefinition> defs = new ArrayList<>();
    
    // 1. GPU 利用率 (%)
    defs.add(TelemetryDefinition.builder()
        .key("gpu_utilization")                  // 存储时使用的 key
        .displayName("GPU利用率")                 // 前端显示名称
        .dataType(DataType.LONG)                 // 数据类型
        .unit("%")                               // 单位
        .protocolConfig(PrometheusConfig.builder()
            .promQL("DCGM_FI_DEV_GPU_UTIL")      // Prometheus 查询语句
            .build())
        .build());
    
    // 2. GPU 温度 (°C)
    defs.add(TelemetryDefinition.builder()
        .key("gpu_temperature")
        .displayName("GPU温度")
        .dataType(DataType.LONG)
        .unit("°C")
        .protocolConfig(PrometheusConfig.builder()
            .promQL("DCGM_FI_DEV_GPU_TEMP")
            .build())
        .build());
    
    // 3. 功耗 (W)
    defs.add(TelemetryDefinition.builder()
        .key("power_usage")
        .displayName("功耗")
        .dataType(DataType.DOUBLE)
        .unit("W")
        .protocolConfig(PrometheusConfig.builder()
            .promQL("DCGM_FI_DEV_POWER_USAGE")
            .build())
        .build());
    
    // ... 其他 4 个指标（显存、温度等）
    
    return defs;
}
```

### 第二步：创建 Device（具体设备实例）

每个 GPU 是一个独立的 `Device` 实例，通过 `prometheusLabel` 映射到 Prometheus 的具体标签。

```java
// GPU 0
Device gpu0 = Device.builder()
    .id(DeviceId.random())
    .name("NVIDIA TITAN V - GPU 0")
    .type("NVIDIA_GPU")
    .deviceProfileId(savedProfile.getId())  // 关联 DeviceProfile
    
    // 设备认证凭证（用于 MQTT/HTTP 推送）
    .accessToken("gpu-0-token")
    
    // Prometheus 标签映射（关键！）
    // 格式: "label_key=label_value"
    // Prometheus 查询结果中，只有 gpu="0" 的数据会被分配给这个设备
    .prometheusLabel("gpu=0")
    
    .createdTime(System.currentTimeMillis())
    .build();

Device savedGpu0 = deviceService.save(gpu0);

// GPU 1
Device gpu1 = Device.builder()
    .id(DeviceId.random())
    .name("NVIDIA TITAN V - GPU 1")
    .type("NVIDIA_GPU")
    .deviceProfileId(savedProfile.getId())
    .accessToken("gpu-1-token")
    .prometheusLabel("gpu=1")  // 映射到 Prometheus 的 gpu="1" 标签
    .createdTime(System.currentTimeMillis())
    .build();

Device savedGpu1 = deviceService.save(gpu1);
```

**Prometheus 标签映射原理**:

```
Prometheus 查询结果:
DCGM_FI_DEV_GPU_UTIL{gpu="0", instance="192.168.30.134:9400"} = 100
DCGM_FI_DEV_GPU_UTIL{gpu="1", instance="192.168.30.134:9400"} = 98

MiniTB 自动过滤:
- gpu0 (prometheusLabel="gpu=0") → 只接收 gpu="0" 的数据
- gpu1 (prometheusLabel="gpu=1") → 只接收 gpu="1" 的数据
```

### 第三步：自动数据采集（PrometheusDataPuller）

系统启动后，`PrometheusDataPuller` 会自动定时拉取数据：

```java
@Component
@Slf4j
public class PrometheusDataPuller {
    
    @Scheduled(fixedRate = 2000, initialDelay = 5000)  // 每 2 秒执行一次
    public void pullAllPrometheusDevices() {
        // 1. 查找所有 Prometheus 类型的 DeviceProfile
        List<DeviceProfile> prometheusProfiles = deviceService.findAll().stream()
            .map(device -> deviceService.findProfileById(device.getDeviceProfileId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(profile -> profile.getDataSourceType() == DeviceProfile.DataSourceType.PROMETHEUS)
            .distinct()
            .collect(Collectors.toList());
        
        // 2. 对每个 Profile，拉取所有关联设备的数据
        for (DeviceProfile profile : prometheusProfiles) {
            List<Device> devicesForProfile = deviceService.findAll().stream()
                .filter(d -> d.getDeviceProfileId().equals(profile.getId()))
                .collect(Collectors.toList());
            
            // 3. 对每个遥测指标执行 PromQL 查询
            for (TelemetryDefinition telemetryDef : profile.getTelemetryDefinitions()) {
                PrometheusConfig config = (PrometheusConfig) telemetryDef.getProtocolConfig();
                String promQL = config.getPromQL();  // 例如: "DCGM_FI_DEV_GPU_UTIL"
                
                // 4. 查询 Prometheus
                List<PrometheusQueryResult> results = queryPrometheus(
                    profile.getPrometheusEndpoint(), 
                    promQL
                );
                
                // 5. 根据标签映射，将数据分配给对应的设备
                for (Device device : devicesForProfile) {
                    String labelFilter = device.getPrometheusLabel();  // "gpu=0"
                    
                    // 过滤出匹配的结果
                    Optional<PrometheusQueryResult> matchedResult = results.stream()
                        .filter(result -> matchesLabel(result.getMetric(), labelFilter))
                        .findFirst();
                    
                    if (matchedResult.isPresent()) {
                        double value = matchedResult.get().getValue();
                        
                        // 6. 构造 JSON 遥测数据
                        Map<String, Object> telemetryData = new HashMap<>();
                        telemetryData.put(telemetryDef.getKey(), value);  // "gpu_utilization": 100
                        
                        String json = objectMapper.writeValueAsString(telemetryData);
                        
                        // 7. 发送到 TransportService（进入正常的数据流）
                        transportService.processTelemetry(device.getAccessToken(), json);
                    }
                }
            }
        }
    }
}
```

**关键点**:
- **自动化**: 无需手动配置每个指标的查询
- **标签映射**: 自动将 Prometheus 数据分配给正确的设备
- **统一流程**: 拉取的数据通过 `TransportService` 进入标准的 Actor → RuleEngine → Storage 流程

### 第四步：数据处理（Actor + RuleEngine）

数据进入 MiniTB 后，会经过标准的处理流程：

```
TransportService.processTelemetry(token, json)
  ↓
1. 根据 accessToken 查找 Device
  ↓
2. 创建 TransportToDeviceMsg 消息
  ↓
3. 发送到 DeviceActor（异步）
  ↓
DeviceActor 接收消息
  ↓
4. 解析 JSON → List<TsKvEntry>（强类型）
  ↓
5. 创建 ToRuleEngineMsg
  ↓
6. 发送到 RuleEngineActor
  ↓
RuleEngineActor 协调规则链执行
  ↓
7. LogNode (入口日志) → FilterNode (过滤) → SaveTelemetryNode (保存) → LogNode (完成)
  ↓
8. 数据持久化到 TelemetryStorage
```

**示例日志**:

```
[PrometheusDataPuller] ✓ gpu_utilization = 100.0
[TransportService] 接收到遥测数据: token=gpu-0-token, data={"gpu_utilization":100}
[DeviceActor] [gpu0-id] 收到遥测数据: {"gpu_utilization":100}
[RuleEngineActor] 规则引擎收到消息: deviceId=gpu0-id, type=POST_TELEMETRY_REQUEST
[LogNode] [入口日志] 数据点: key=gpu_utilization, type=LONG, value=100
[SaveTelemetryNode] 保存遥测数据成功: deviceId=gpu0-id, 数据点数=1
[TelemetryStorage] 批量保存遥测数据: deviceId=gpu0-id, 数据点数=1
[LogNode] [保存完成] 数据点: key=gpu_utilization, type=LONG, value=100
```

### 第五步：前端访问（REST API + Web 界面）

#### REST API

MiniTB 提供了完整的 REST API 供前端调用：

```bash
# 1. 获取设备列表
GET /api/devices
Response:
[
  {
    "id": "33661981-9aa4-4bb9-907c-a34e64aef8ed",
    "name": "NVIDIA TITAN V - GPU 0",
    "type": "NVIDIA_GPU",
    "accessToken": "gpu-0-token"
  },
  {
    "id": "ffef486c-7879-4068-9bc6-208c3e132829",
    "name": "NVIDIA TITAN V - GPU 1",
    "type": "NVIDIA_GPU",
    "accessToken": "gpu-1-token"
  }
]

# 2. 获取设备的最新遥测数据
GET /api/telemetry/{deviceId}/latest
Response:
{
  "deviceId": "33661981-9aa4-4bb9-907c-a34e64aef8ed",
  "deviceName": "NVIDIA TITAN V - GPU 0",
  "data": {
    "gpu_utilization": { "timestamp": 1730038841918, "value": 100 },
    "gpu_temperature": { "timestamp": 1730038841918, "value": 74 },
    "power_usage": { "timestamp": 1730038841918, "value": 152.719 },
    "memory_used": { "timestamp": 1730038841918, "value": 614 },
    "memory_free": { "timestamp": 1730038841918, "value": 11442 }
  }
}

# 3. 获取历史数据（用于绘制趋势图）
GET /api/telemetry/{deviceId}/history/gpu_temperature?limit=100
Response:
[
  { "timestamp": 1730038841918, "value": 74 },
  { "timestamp": 1730038839918, "value": 73 },
  { "timestamp": 1730038837918, "value": 74 },
  // ... 最近 100 个数据点
]
```

**API 实现示例**:

```java
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {
    
    private final TelemetryStorage telemetryStorage;
    private final DeviceService deviceService;
    
    @GetMapping("/{deviceId}/latest")
    public LatestTelemetryDto getLatestTelemetry(@PathVariable String deviceId) {
        DeviceId id = new DeviceId(UUID.fromString(deviceId));
        Device device = deviceService.findById(id).orElseThrow();
        
        // 获取所有 key 的最新数据
        Map<String, TsKvEntry> latestData = telemetryStorage.getLatest(id);
        
        // 转换为 DTO
        Map<String, TelemetryDataPointDto> dataMap = latestData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> TelemetryDataPointDto.fromTsKvEntry(e.getValue())
            ));
        
        return new LatestTelemetryDto(deviceId, device.getName(), dataMap);
    }
    
    @GetMapping("/{deviceId}/history/{key}")
    public List<TelemetryDataPointDto> getHistoryData(
            @PathVariable String deviceId,
            @PathVariable String key,
            @RequestParam(defaultValue = "100") int limit) {
        
        DeviceId id = new DeviceId(UUID.fromString(deviceId));
        
        // 查询历史数据（最近 limit 个点）
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (limit * 2000L);  // 假设 2 秒一个点
        
        List<TsKvEntry> history = telemetryStorage.query(id, key, startTime, endTime);
        
        // 转换为 DTO
        return history.stream()
            .map(TelemetryDataPointDto::fromTsKvEntry)
            .collect(Collectors.toList());
    }
}
```

#### Web 界面（Chart.js 实时图表）

前端使用 HTML + JavaScript + Chart.js 实现实时监控界面：

```html
<!-- index.html -->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <title>MiniTB GPU 监控</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0"></script>
</head>
<body>
    <!-- 设备选择器 -->
    <div id="device-selector"></div>
    
    <!-- 8 个指标卡片 -->
    <div class="metrics-grid">
        <div class="metric-card">
            <h3>GPU 利用率</h3>
            <div class="value" id="gpu-utilization">--</div>
            <div class="unit">%</div>
        </div>
        <div class="metric-card">
            <h3>GPU 温度</h3>
            <div class="value" id="gpu-temperature">--</div>
            <div class="unit">°C</div>
        </div>
        <!-- ... 其他 6 个卡片 -->
    </div>
    
    <!-- 3 个趋势图表 -->
    <div class="charts-grid">
        <canvas id="temperature-chart"></canvas>
        <canvas id="utilization-chart"></canvas>
        <canvas id="power-chart"></canvas>
    </div>
    
    <script src="gpu-monitor.js"></script>
</body>
</html>
```

```javascript
// gpu-monitor.js
let currentDeviceId = null;
let charts = {};

// 1. 加载设备列表
async function loadDevices() {
    const response = await fetch('/api/devices');
    const devices = await response.json();
    
    // 渲染设备选择器
    renderDeviceTabs(devices);
    
    // 默认选择第一个设备
    if (devices.length > 0) {
        selectDevice(devices[0].id);
    }
}

// 2. 初始化图表
function initCharts() {
    charts.temperature = new Chart(document.getElementById('temperature-chart'), {
        type: 'line',
        data: {
            labels: [],  // 时间轴
            datasets: [{
                label: 'GPU 温度',
                data: [],
                borderColor: '#ff6b6b',
                tension: 0.4
            }, {
                label: '显存温度',
                data: [],
                borderColor: '#ffa94d',
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            animation: false,  // 实时更新时禁用动画
            scales: {
                y: { title: { display: true, text: '温度 (°C)' } }
            }
        }
    });
    
    // ... 初始化其他图表
}

// 3. 更新数据（每 2 秒调用一次）
async function updateData() {
    if (!currentDeviceId) return;
    
    // 获取最新数据
    const response = await fetch(`/api/telemetry/${currentDeviceId}/latest`);
    const latest = await response.json();
    
    // 更新指标卡片
    document.getElementById('gpu-utilization').textContent = 
        latest.data.gpu_utilization?.value ?? '--';
    document.getElementById('gpu-temperature').textContent = 
        latest.data.gpu_temperature?.value ?? '--';
    // ... 更新其他卡片
    
    // 获取历史数据（用于趋势图）
    const historyTemp = await fetch(`/api/telemetry/${currentDeviceId}/history/gpu_temperature?limit=50`);
    const tempData = await historyTemp.json();
    
    // 更新图表
    updateChart(charts.temperature, tempData, 0);  // 第 0 个 dataset
}

// 4. 启动自动刷新
setInterval(updateData, 2000);  // 每 2 秒更新

// 初始化
loadDevices();
initCharts();
```

**界面效果**:

```
┌──────────────────────────────────────────────────────────┐
│  MiniTB GPU 监控                         🔄 更新于: 14:32:58 │
├──────────────────────────────────────────────────────────┤
│  [ GPU 0 ]  [ GPU 1 ]                                      │
├──────────────────────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │
│  │GPU 利用率│ │GPU 温度  │ │  功耗    │ │已用显存  │    │
│  │   100%  │ │   74°C  │ │ 152.7W  │ │  614MB  │    │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │
│  │显存温度  │ │拷贝带宽  │ │空闲显存  │ │最后更新  │    │
│  │   82°C  │ │  100%   │ │ 11442MB │ │  2秒前   │    │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │
├──────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────┐   │
│  │  GPU/显存温度趋势 (最近 100 秒)                     │   │
│  │  [折线图: GPU温度=74°C, 显存温度=82°C]              │   │
│  └───────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────┐   │
│  │  GPU/带宽利用率 (最近 100 秒)                       │   │
│  │  [折线图: GPU=100%, 带宽=100%]                     │   │
│  └───────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────┐   │
│  │  功耗 (最近 100 秒)                                 │   │
│  │  [折线图: 功耗=152.7W]                             │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

### 总结：从定义到展示的完整流程

```
1. 定义 DeviceProfile
   ├─ 数据源类型: PROMETHEUS
   ├─ Prometheus 端点: http://192.168.30.134:9090
   ├─ 标签 key: gpu
   └─ 7 个遥测指标定义 (gpu_utilization, gpu_temperature, ...)

2. 创建 Device 实例
   ├─ GPU 0: prometheusLabel="gpu=0", accessToken="gpu-0-token"
   └─ GPU 1: prometheusLabel="gpu=1", accessToken="gpu-1-token"

3. 自动数据采集 (PrometheusDataPuller)
   ├─ 每 2 秒查询 Prometheus
   ├─ 根据标签映射分配数据
   └─ 调用 transportService.processTelemetry(token, json)

4. 数据处理 (Actor + RuleEngine)
   ├─ DeviceActor 接收消息（异步）
   ├─ 转换为强类型 TsKvEntry
   ├─ RuleEngineActor 执行规则链
   └─ SaveTelemetryNode 持久化到 TelemetryStorage

5. 前端访问
   ├─ REST API: /api/devices, /api/telemetry/{id}/latest
   ├─ Web 界面: 8 个指标卡片 + 3 个趋势图表
   └─ 每 2 秒自动刷新
```

**核心优势**:
- ✅ **配置驱动**: 只需定义 DeviceProfile 和 Device，无需编写数据采集代码
- ✅ **自动映射**: Prometheus 标签自动映射到具体设备
- ✅ **强类型**: JSON → TsKvEntry 自动类型推断
- ✅ **异步处理**: Actor 模型保证高吞吐、低延迟
- ✅ **规则引擎**: 灵活的数据处理流程（过滤、转换、告警）
- ✅ **开箱即用**: REST API + Web 界面，无需额外开发

---

## 🧩 核心组件

### 1. DeviceProfile（设备配置模板）

**职责**: 定义一类设备的通用配置

```java
@Data
@Builder
public class DeviceProfile {
    private DeviceProfileId id;
    private String name;
    private String description;
    
    // 数据源配置
    private DataSourceType dataSourceType;  // PROMETHEUS, MQTT, HTTP
    private String prometheusEndpoint;       // Prometheus 服务器地址
    private String prometheusDeviceLabelKey; // 用于区分设备的标签 key
    
    // 遥测指标定义
    private List<TelemetryDefinition> telemetryDefinitions;
    
    public enum DataSourceType {
        PROMETHEUS,  // 拉取模式
        MQTT,        // 推送模式
        HTTP         // 推送模式
    }
}
```

**使用场景**:
- 定义一类设备的监控指标（例如：所有 NVIDIA GPU 的通用指标）
- 配置数据源和协议参数
- 复用配置，避免重复定义

### 2. Device（设备实例）

**职责**: 代表一个具体的物理设备或逻辑设备

```java
@Data
@Builder
public class Device {
    private DeviceId id;
    private String name;
    private String type;
    private DeviceProfileId deviceProfileId;  // 关联 DeviceProfile
    
    // 认证
    private String accessToken;  // MQTT/HTTP 推送时的认证凭证
    
    // Prometheus 映射
    private String prometheusLabel;  // 例如: "gpu=0", "instance=localhost:9100"
    
    // 元数据
    private Long createdTime;
}
```

**关键字段**:
- `accessToken`: 设备推送数据时的身份凭证（类似 API Key）
- `prometheusLabel`: Prometheus 标签过滤器（格式: `key=value`）

### 3. TelemetryDefinition（遥测指标定义）

**职责**: 定义一个具体的监控指标

```java
@Data
@Builder
public class TelemetryDefinition {
    private String key;           // 存储时使用的 key（例如: "cpu_usage"）
    private String displayName;   // 前端显示名称（例如: "CPU 使用率"）
    private DataType dataType;    // BOOLEAN, LONG, DOUBLE, STRING, JSON
    private String unit;          // 单位（例如: "%", "°C", "MB"）
    
    // 协议配置（策略模式）
    private ProtocolConfig protocolConfig;  // PrometheusConfig, MqttConfig, HttpConfig
}
```

**协议配置示例**:

```java
// Prometheus 配置
PrometheusConfig config = PrometheusConfig.builder()
    .promQL("DCGM_FI_DEV_GPU_UTIL")  // PromQL 查询语句
    .build();

// MQTT 配置（未来扩展）
MqttConfig config = MqttConfig.builder()
    .topic("device/+/telemetry")
    .jsonPath("$.sensors.temperature")
    .build();
```

### 4. TsKvEntry（时间序列数据）

**职责**: 表示一个时间序列数据点（强类型）

```java
public interface TsKvEntry {
    long getTs();              // 时间戳
    String getKey();           // 数据 key
    DataType getDataType();    // 数据类型
    
    Optional<Boolean> getBooleanValue();
    Optional<Long> getLongValue();
    Optional<Double> getDoubleValue();
    Optional<String> getStringValue();
    Optional<String> getJsonValue();
}
```

**实现类**:

```java
// LONG 类型
public class LongDataEntry extends BasicKvEntry {
    private final Long value;
    
    public Optional<Long> getLongValue() {
        return Optional.of(value);
    }
    
    public Optional<Double> getDoubleValue() {
        return Optional.of(value.doubleValue());  // 自动转换
    }
}

// DOUBLE 类型
public class DoubleDataEntry extends BasicKvEntry {
    private final Double value;
    
    public Optional<Double> getDoubleValue() {
        return Optional.of(value);
    }
}
```

**优势**:
- **类型安全**: 编译时检查，避免运行时错误
- **自动转换**: LONG 可以自动转换为 DOUBLE
- **不可变**: 线程安全

### 5. Actor System（异步消息处理）

**职责**: 提供高并发、故障隔离的异步处理能力

```java
// Actor 基类
public interface MiniTbActor {
    String getActorId();
    void onMsg(Object msg);
}

// DeviceActor（每个设备一个实例）
public class DeviceActor implements MiniTbActor {
    private final DeviceId deviceId;
    private final Device device;
    
    @Override
    public void onMsg(Object msg) {
        if (msg instanceof TransportToDeviceMsg) {
            processTransportMsg((TransportToDeviceMsg) msg);
        }
    }
    
    private void processTransportMsg(TransportToDeviceMsg msg) {
        // 1. 解析 JSON → List<TsKvEntry>
        List<TsKvEntry> telemetry = parseTelemetry(msg.getPayload());
        
        // 2. 创建消息
        ToRuleEngineMsg ruleMsg = ToRuleEngineMsg.builder()
            .deviceId(deviceId)
            .telemetry(telemetry)
            .build();
        
        // 3. 发送到 RuleEngineActor
        actorSystem.tell("RuleEngineActor", ruleMsg);
    }
}
```

**特点**:
- **独立消息队列**: 每个 DeviceActor 有独立的消息队列
- **串行处理**: 同一 Actor 的消息串行执行，避免并发问题
- **故障隔离**: 一个 Actor 崩溃不影响其他 Actor
- **背压保护**: 队列过长时自动拒绝新消息

### 6. RuleChain（规则链）

**职责**: 定义数据处理流程（责任链模式）

```java
@Slf4j
public class RuleChain {
    private final String name;
    private RuleNode head;  // 链头
    
    public void onMsg(Message msg, RuleNodeContext context) {
        if (head != null) {
            head.onMsg(msg, context);  // 从链头开始执行
        }
    }
    
    public void addNode(RuleNode node) {
        if (head == null) {
            head = node;
        } else {
            // 添加到链尾
            RuleNode tail = head;
            while (tail.getNext() != null) {
                tail = tail.getNext();
            }
            tail.setNext(node);
        }
    }
}
```

**内置节点**:

```java
// 1. LogNode - 日志节点
public class LogNode implements RuleNode {
    private final String label;
    
    @Override
    public void onMsg(Message msg, RuleNodeContext context) {
        log.info("[{}] 消息: deviceId={}, 数据点数={}", 
            label, msg.getOriginator(), msg.getTelemetry().size());
        
        // 传递给下一个节点
        if (next != null) {
            next.onMsg(msg, context);
        }
    }
}

// 2. FilterNode - 过滤节点
public class FilterNode implements RuleNode {
    private final String condition;  // "temperature > 80"
    
    @Override
    public void onMsg(Message msg, RuleNodeContext context) {
        if (matches(msg, condition)) {
            if (next != null) {
                next.onMsg(msg, context);
            }
        }
    }
}

// 3. SaveTelemetryNode - 保存节点
public class SaveTelemetryNode implements RuleNode {
    private final TelemetryStorage storage;
    
    @Override
    public void onMsg(Message msg, RuleNodeContext context) {
        storage.save(msg.getOriginator(), msg.getTelemetry());
        
        if (next != null) {
            next.onMsg(msg, context);
        }
    }
}
```

**配置示例**:

```java
RuleChain chain = new RuleChain("Root Rule Chain");
chain.addNode(new LogNode("入口日志"));
chain.addNode(new FilterNode("temperature > 20"));
chain.addNode(new LogNode("过滤后日志"));
chain.addNode(new SaveTelemetryNode(telemetryStorage));
chain.addNode(new LogNode("保存完成"));
```

### 7. TelemetryStorage（遥测数据存储）

**职责**: 时间序列数据的内存存储

```java
@Component
public class TelemetryStorage {
    // 存储结构: Map<DeviceId, Map<Key, List<TsKvEntry>>>
    private final Map<DeviceId, Map<String, List<TsKvEntry>>> storage = 
        new ConcurrentHashMap<>();
    
    // 保存单个数据点
    public void save(DeviceId deviceId, TsKvEntry entry) {
        storage.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(entry.getKey(), k -> new CopyOnWriteArrayList<>())
               .add(entry);
    }
    
    // 批量保存
    public void save(DeviceId deviceId, List<TsKvEntry> entries) {
        entries.forEach(entry -> save(deviceId, entry));
    }
    
    // 查询最新值
    public Optional<TsKvEntry> getLatest(DeviceId deviceId, String key) {
        List<TsKvEntry> entries = getEntries(deviceId, key);
        return entries.isEmpty() ? Optional.empty() : 
               Optional.of(entries.get(entries.size() - 1));
    }
    
    // 查询所有 key 的最新值
    public Map<String, TsKvEntry> getLatest(DeviceId deviceId) {
        Map<String, List<TsKvEntry>> deviceData = storage.get(deviceId);
        if (deviceData == null) return Collections.emptyMap();
        
        return deviceData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get(e.getValue().size() - 1)
            ));
    }
    
    // 范围查询
    public List<TsKvEntry> query(DeviceId deviceId, String key, 
                                  long startTs, long endTs) {
        return getEntries(deviceId, key).stream()
            .filter(e -> e.getTs() >= startTs && e.getTs() <= endTs)
            .collect(Collectors.toList());
    }
}
```

**特点**:
- **内存存储**: 高性能，适合实时监控
- **线程安全**: 使用 `ConcurrentHashMap` 和 `CopyOnWriteArrayList`
- **灵活查询**: 支持最新值、范围查询、聚合查询

---

## 🔄 数据流程

### 完整数据流（Prometheus 拉取模式）

```
┌─────────────────────────────────────────────────────────────┐
│  1. Prometheus Data Source                                  │
│     • DCGM Exporter: http://192.168.30.134:9400/metrics     │
│     • Prometheus Server: http://192.168.30.134:9090         │
└─────────────────┬───────────────────────────────────────────┘
                  │ PromQL 查询
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  2. PrometheusDataPuller (@Scheduled, 每 2 秒)              │
│     • 读取 DeviceProfile 的遥测定义                          │
│     • 执行 PromQL 查询: DCGM_FI_DEV_GPU_UTIL                │
│     • 根据 prometheusLabel 过滤结果                         │
│     • 构造 JSON: {"gpu_utilization": 100}                   │
└─────────────────┬───────────────────────────────────────────┘
                  │ transportService.processTelemetry(token, json)
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  3. TransportService                                         │
│     • 根据 accessToken 查找 Device                           │
│     • 验证设备是否存在                                        │
│     • 创建 TransportToDeviceMsg 消息                         │
│     • JSON 字符串 → 消息对象                                 │
└─────────────────┬───────────────────────────────────────────┘
                  │ actorSystem.tell(deviceActor, msg)
                  ↓ (异步！消息入队后立即返回)
┌─────────────────────────────────────────────────────────────┐
│  4. DeviceActor (独立消息队列)                               │
│     • 从队列取出消息（串行处理）                              │
│     • 解析 JSON → List<TsKvEntry>（强类型）                  │
│       - "gpu_utilization": 100 → LongDataEntry(100)         │
│     • 创建 ToRuleEngineMsg                                   │
└─────────────────┬───────────────────────────────────────────┘
                  │ actorSystem.tell("RuleEngineActor", msg)
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  5. RuleEngineActor                                          │
│     • 协调规则链执行                                         │
│     • 异步执行规则链（不阻塞 Actor）                          │
└─────────────────┬───────────────────────────────────────────┘
                  │ ruleChain.onMsg(msg, context)
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  6. RuleChain (Root Rule Chain)                             │
│     ┌─────────────────────────────────────────────────┐    │
│     │ LogNode[入口日志]                                 │    │
│     │   • 日志: "收到遥测数据: gpu_utilization=100"      │    │
│     └────────────┬────────────────────────────────────┘    │
│                  ↓                                          │
│     ┌─────────────────────────────────────────────────┐    │
│     │ FilterNode[temperature > 20]                     │    │
│     │   • 判断条件（本例中无 temperature，跳过）         │    │
│     └────────────┬────────────────────────────────────┘    │
│                  ↓                                          │
│     ┌─────────────────────────────────────────────────┐    │
│     │ LogNode[过滤后日志]                               │    │
│     │   • 日志: "过滤后数据: gpu_utilization=100"        │    │
│     └────────────┬────────────────────────────────────┘    │
│                  ↓                                          │
│     ┌─────────────────────────────────────────────────┐    │
│     │ SaveTelemetryNode                                │    │
│     │   • storage.save(deviceId, telemetry)            │    │
│     └────────────┬────────────────────────────────────┘    │
│                  ↓                                          │
│     ┌─────────────────────────────────────────────────┐    │
│     │ LogNode[保存完成]                                 │    │
│     │   • 日志: "数据已保存: gpu_utilization=100"        │    │
│     └─────────────────────────────────────────────────┘    │
└─────────────────┬───────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  7. TelemetryStorage (内存 + 文件备份)                       │
│     • Map<DeviceId, Map<Key, List<TsKvEntry>>>              │
│     • 保存时间序列数据                                        │
│     • 支持查询: 最新值、范围查询、聚合                         │
└─────────────────┬───────────────────────────────────────────┘
                  │ (REST API 查询)
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  8. REST API (TelemetryController)                          │
│     • GET /api/telemetry/{id}/latest                        │
│     • GET /api/telemetry/{id}/history/{key}                 │
│     • storage.getLatest(deviceId, key)                      │
└─────────────────┬───────────────────────────────────────────┘
                  │ HTTP Response (JSON)
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  9. Web 界面 (HTML + JavaScript + Chart.js)                 │
│     • fetch('/api/telemetry/xxx/latest')                    │
│     • 更新指标卡片                                            │
│     • 更新趋势图表                                            │
│     • 每 2 秒自动刷新                                         │
└─────────────────────────────────────────────────────────────┘
```

### MQTT 推送模式数据流（对比）

```
┌─────────────────────────────────────────────────────────────┐
│  1. IoT Device (MQTT Client)                                │
│     mosquitto_pub -h localhost -p 1883 \                    │
│       -u gpu-0-token \                                      │
│       -t v1/devices/me/telemetry \                          │
│       -m '{"temperature":25.5}'                             │
└─────────────────┬───────────────────────────────────────────┘
                  │ MQTT Publish
                  ↓
┌─────────────────────────────────────────────────────────────┐
│  2. MqttTransportService (Netty Server, 端口 1883)          │
│     • 接收 MQTT 消息                                         │
│     • 从 username 提取 accessToken                          │
│     • 从 payload 提取 JSON                                   │
└─────────────────┬───────────────────────────────────────────┘
                  │ transportService.processTelemetry(token, json)
                  ↓
                (后续流程与 Prometheus 相同)
```

### 性能指标

| 阶段 | 耗时 | 说明 |
|------|------|------|
| **Prometheus 查询** | ~8ms | HTTP 请求 + 解析 |
| **数据拉取** | ~6ms | 过滤 + JSON 构造 + 发送 |
| **TransportService** | <1ms | 设备查找 + 消息创建 |
| **Actor 入队** | <1ms | 消息入队（异步） |
| **DeviceActor** | ~5ms | JSON 解析 + 类型转换 |
| **RuleEngine** | ~50ms | 规则链执行（测试环境，含日志） |
| **存储写入** | ~7ms | 内存写入 + 文件备份 |
| **总耗时** | ~65ms | Prometheus 拉取 → 持久化完成 |

---

## 🏗️ 六边形架构

### 架构图

```
┌───────────────────────────────────────────────────────────────────┐
│                        Adapters (适配器层)                         │
│  ┌──────────────────────┐           ┌─────────────────────────┐  │
│  │  Input Adapters      │           │  Output Adapters        │  │
│  │  (驱动适配器)         │           │  (被驱动适配器)          │  │
│  │                      │           │                         │  │
│  │  • PrometheusData    │           │  • JpaDeviceRepository  │  │
│  │    Puller            │           │    Adapter              │  │
│  │  • MqttTransport     │           │  • SqliteDeviceRepo     │  │
│  │    Service           │──────┐    │    sitoryAdapter        │  │
│  │  • REST Controllers  │      │    │  • TelemetryStorage     │  │
│  │  • DeviceController  │      │    │                         │  │
│  │  • TelemetryController│     │    │                         │  │
│  └──────────────────────┘      │    └─────────────────────────┘  │
└────────────────────────────────┼───────────────────────────────────┘
                                 │
                                 ↓
┌───────────────────────────────────────────────────────────────────┐
│                      Application Layer (应用层)                     │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  • DeviceService (设备服务)                                  │ │
│  │  • DeviceServiceImpl (实现)                                  │ │
│  │  • DataInitializer (初始化服务)                              │ │
│  │  • RuleEngineService (规则引擎服务)                          │ │
│  └─────────────────────────────────────────────────────────────┘ │
└────────────────────────────────┬─────────────────────────────────┘
                                 │ 依赖
                                 ↓
┌───────────────────────────────────────────────────────────────────┐
│                       Domain Layer (领域层)                         │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Entities (实体/聚合根)                                       │ │
│  │  • Device, DeviceProfile, Alarm                             │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Value Objects (值对象)                                      │ │
│  │  • DeviceId, DeviceProfileId, TsKvEntry, Message           │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Ports (端口 - 接口定义)                                     │ │
│  │  • DeviceRepository (仓储接口)                               │ │
│  │  • DeviceProfileRepository                                  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Domain Services (领域服务)                                  │ │
│  │  • RuleChain, RuleNode                                      │ │
│  └─────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

### 依赖方向

```
Infrastructure → Application → Domain
     ↑                              ↑
     └──────────────────────────────┘
          实现端口（接口）
```

**核心原则**:
- ✅ **依赖倒置**: Infrastructure 依赖 Domain 定义的接口，而不是反过来
- ✅ **领域独立**: Domain 层不依赖任何外部框架（Spring, JPA, Netty）
- ✅ **易于测试**: 可以 Mock 端口接口进行单元测试
- ✅ **易于替换**: 可以轻松替换技术实现（H2 → PostgreSQL, JPA → JDBC）

### 实际案例：设备仓储的六边形实现

#### 1. Domain Layer - 定义端口（接口）

```java
// minitb/src/main/java/com/minitb/domain/device/DeviceRepository.java
package com.minitb.domain.device;

/**
 * 设备仓储端口（Port）
 * 
 * 这是领域层定义的接口，规定了设备持久化的契约。
 * 领域层只关心"做什么"，不关心"怎么做"。
 */
public interface DeviceRepository {
    /**
     * 保存设备
     */
    Device save(Device device);
    
    /**
     * 根据 ID 查找设备
     */
    Optional<Device> findById(DeviceId id);
    
    /**
     * 根据 accessToken 查找设备
     */
    Optional<Device> findByAccessToken(String accessToken);
    
    /**
     * 查找所有设备
     */
    List<Device> findAll();
    
    /**
     * 删除设备
     */
    void deleteById(DeviceId id);
}
```

**关键点**:
- 使用领域对象 (`Device`, `DeviceId`)，不使用技术对象 (`DeviceEntity`, `UUID`)
- 不包含任何 JPA、JDBC、Spring 注解
- 纯粹的业务接口定义

#### 2. Infrastructure Layer - 实现适配器（Adapter）

##### 适配器 1: JPA 实现

```java
// minitb/src/main/java/com/minitb/infrastructure/persistence/jpa/JpaDeviceRepositoryAdapter.java
package com.minitb.infrastructure.persistence.jpa;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * JPA 设备仓储适配器
 * 
 * 实现领域层定义的 DeviceRepository 接口，
 * 使用 Spring Data JPA 作为技术实现。
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "jpa", matchIfMissing = true)
public class JpaDeviceRepositoryAdapter implements DeviceRepository {
    
    private final SpringDataDeviceRepository jpaRepository;
    
    public JpaDeviceRepositoryAdapter(SpringDataDeviceRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }
    
    @Override
    public Device save(Device device) {
        // 领域对象 → JPA 实体
        DeviceEntity entity = DeviceEntity.fromDomain(device);
        
        // JPA 保存
        DeviceEntity saved = jpaRepository.save(entity);
        
        // JPA 实体 → 领域对象
        return saved.toDomain();
    }
    
    @Override
    public Optional<Device> findById(DeviceId id) {
        return jpaRepository.findById(id.getId())
            .map(DeviceEntity::toDomain);
    }
    
    @Override
    public Optional<Device> findByAccessToken(String accessToken) {
        return jpaRepository.findByAccessToken(accessToken)
            .map(DeviceEntity::toDomain);
    }
    
    @Override
    public List<Device> findAll() {
        return jpaRepository.findAll().stream()
            .map(DeviceEntity::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public void deleteById(DeviceId id) {
        jpaRepository.deleteById(id.getId());
    }
}
```

**JPA 实体（技术对象）**:

```java
// minitb/src/main/java/com/minitb/infrastructure/persistence/jpa/entity/DeviceEntity.java
@Entity
@Table(name = "device")
@Data
public class DeviceEntity {
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String type;
    
    @Column(name = "device_profile_id")
    private UUID deviceProfileId;
    
    @Column(name = "access_token", unique = true)
    private String accessToken;
    
    @Column(name = "prometheus_label")
    private String prometheusLabel;
    
    @Column(name = "created_time")
    private Long createdTime;
    
    /**
     * 领域对象 → JPA 实体
     */
    public static DeviceEntity fromDomain(Device device) {
        DeviceEntity entity = new DeviceEntity();
        entity.setId(device.getId().getId());
        entity.setName(device.getName());
        entity.setType(device.getType());
        if (device.getDeviceProfileId() != null) {
            entity.setDeviceProfileId(device.getDeviceProfileId().getId());
        }
        entity.setAccessToken(device.getAccessToken());
        entity.setPrometheusLabel(device.getPrometheusLabel());
        entity.setCreatedTime(device.getCreatedTime());
        return entity;
    }
    
    /**
     * JPA 实体 → 领域对象
     */
    public Device toDomain() {
        return Device.builder()
            .id(new DeviceId(id))
            .name(name)
            .type(type)
            .deviceProfileId(deviceProfileId != null ? new DeviceProfileId(deviceProfileId) : null)
            .accessToken(accessToken)
            .prometheusLabel(prometheusLabel)
            .createdTime(createdTime)
            .build();
    }
}
```

##### 适配器 2: SQLite 实现

```java
// minitb/src/main/java/com/minitb/infrastructure/persistence/sqlite/SqliteDeviceRepositoryAdapter.java
package com.minitb.infrastructure.persistence.sqlite;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * SQLite 设备仓储适配器
 * 
 * 实现领域层定义的 DeviceRepository 接口，
 * 使用原生 JDBC 操作 SQLite 数据库。
 */
@Component
@ConditionalOnProperty(name = "minitb.storage.type", havingValue = "sqlite")
public class SqliteDeviceRepositoryAdapter implements DeviceRepository {
    
    private final SqliteConnectionManager connectionManager;
    private final DeviceRowMapper rowMapper = new DeviceRowMapper();
    
    @Override
    public Device save(Device device) {
        String sql = """
            INSERT INTO device (id, name, type, device_profile_id, access_token, 
                                prometheus_label, created_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                type = excluded.type,
                device_profile_id = excluded.device_profile_id,
                access_token = excluded.access_token,
                prometheus_label = excluded.prometheus_label,
                created_time = excluded.created_time
            """;
        
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, device.getId().toString());
            ps.setString(2, device.getName());
            ps.setString(3, device.getType());
            ps.setString(4, device.getDeviceProfileId() != null ? 
                         device.getDeviceProfileId().toString() : null);
            ps.setString(5, device.getAccessToken());
            ps.setString(6, device.getPrometheusLabel());
            ps.setLong(7, device.getCreatedTime());
            
            ps.executeUpdate();
            return device;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save device", e);
        }
    }
    
    @Override
    public Optional<Device> findById(DeviceId id) {
        String sql = "SELECT * FROM device WHERE id = ?";
        
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, id.toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rowMapper.map(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find device", e);
        }
    }
    
    // ... 其他方法实现
}
```

#### 3. Application Layer - 使用端口

```java
// minitb/src/main/java/com/minitb/application/service/impl/DeviceServiceImpl.java
package com.minitb.application.service.impl;

@Service
public class DeviceServiceImpl implements DeviceService {
    
    // 依赖注入时使用接口（端口），而不是具体实现
    private final DeviceRepository deviceRepository;
    private final DeviceProfileRepository deviceProfileRepository;
    
    // Spring 会自动注入正确的适配器（JPA 或 SQLite）
    public DeviceServiceImpl(
            DeviceRepository deviceRepository,
            DeviceProfileRepository deviceProfileRepository) {
        this.deviceRepository = deviceRepository;
        this.deviceProfileRepository = deviceProfileRepository;
    }
    
    @Override
    public Device save(Device device) {
        // 应用层只知道端口接口，不知道底层是 JPA 还是 SQLite
        return deviceRepository.save(device);
    }
    
    @Override
    public Optional<Device> findById(DeviceId id) {
        return deviceRepository.findById(id);
    }
    
    // ... 其他业务逻辑
}
```

#### 4. 配置驱动的适配器切换

```yaml
# application.yml
minitb:
  storage:
    type: sqlite  # 或 jpa
    sqlite:
      path: data/minitb.db

# 当 type=sqlite 时，SqliteDeviceRepositoryAdapter 生效
# 当 type=jpa 时，JpaDeviceRepositoryAdapter 生效
```

**切换存储实现，无需修改任何业务代码！**

### 六边形架构的优势

1. **领域层纯净**
   ```java
   // Device.java - 没有任何框架注解
   @Data
   @Builder
   public class Device {  // 纯 POJO
       private DeviceId id;
       private String name;
       private String type;
       // ...
   }
   ```

2. **易于测试**
   ```java
   @Test
   void testDeviceService() {
       // Mock 端口接口
       DeviceRepository mockRepo = mock(DeviceRepository.class);
       when(mockRepo.findById(any())).thenReturn(Optional.of(device));
       
       // 测试应用层逻辑
       DeviceService service = new DeviceServiceImpl(mockRepo, mockProfileRepo);
       Optional<Device> found = service.findById(deviceId);
       
       assertTrue(found.isPresent());
       assertEquals("Test Device", found.get().getName());
   }
   ```

3. **易于替换技术栈**
   ```
   需求: H2 → PostgreSQL
   
   实现步骤:
   1. 创建 PostgresDeviceRepositoryAdapter 实现 DeviceRepository
   2. 修改配置: minitb.storage.type=postgres
   3. 完成！业务逻辑完全不受影响
   ```

4. **单向依赖**
   ```
   ❌ 错误的依赖方向:
   Domain → Infrastructure (领域层依赖技术实现)
   
   ✅ 正确的依赖方向:
   Infrastructure → Domain (技术实现依赖领域接口)
   ```

---

## 📁 项目结构

```
minitb/
├── src/main/java/com/minitb/
│   ├── domain/                                  # 领域层（核心业务逻辑）
│   │   ├── id/                                  # 强类型 ID
│   │   │   ├── EntityId.java                    # 实体 ID 抽象基类
│   │   │   ├── DeviceId.java
│   │   │   ├── DeviceProfileId.java
│   │   │   └── AlarmId.java
│   │   ├── device/                              # 设备聚合
│   │   │   ├── Device.java                      # 聚合根
│   │   │   ├── DeviceProfile.java               # 设备配置
│   │   │   ├── TelemetryDefinition.java         # 遥测定义
│   │   │   ├── DeviceRepository.java            # 仓储端口
│   │   │   └── DeviceProfileRepository.java
│   │   ├── telemetry/                           # 遥测值对象
│   │   │   ├── DataType.java
│   │   │   ├── TsKvEntry.java
│   │   │   ├── LongDataEntry.java
│   │   │   ├── DoubleDataEntry.java
│   │   │   └── ...
│   │   ├── messaging/                           # 消息值对象
│   │   │   ├── Message.java
│   │   │   └── MessageType.java
│   │   ├── protocol/                            # 协议配置（策略模式）
│   │   │   ├── ProtocolConfig.java
│   │   │   ├── PrometheusConfig.java
│   │   │   ├── MqttConfig.java
│   │   │   └── HttpConfig.java
│   │   └── rule/                                # 规则模型
│   │       ├── RuleNode.java
│   │       ├── RuleNodeContext.java
│   │       └── RuleChain.java
│   │
│   ├── application/                             # 应用层（用例编排）
│   │   └── service/
│   │       ├── DeviceService.java               # 设备服务接口
│   │       ├── impl/
│   │       │   └── DeviceServiceImpl.java       # 设备服务实现
│   │       └── DataInitializer.java             # 初始化数据
│   │
│   ├── infrastructure/                          # 基础设施层（技术实现）
│   │   ├── persistence/                         # 持久化适配器
│   │   │   ├── jpa/                             # JPA 实现
│   │   │   │   ├── entity/
│   │   │   │   │   ├── DeviceEntity.java        # JPA 实体
│   │   │   │   │   └── DeviceProfileEntity.java
│   │   │   │   ├── SpringDataDeviceRepository.java
│   │   │   │   ├── JpaDeviceRepositoryAdapter.java
│   │   │   │   └── JpaDeviceProfileRepositoryAdapter.java
│   │   │   └── sqlite/                          # SQLite 实现
│   │   │       ├── SqliteConnectionManager.java
│   │   │       ├── SqliteDeviceRepositoryAdapter.java
│   │   │       ├── SqliteDeviceProfileRepositoryAdapter.java
│   │   │       └── mapper/
│   │   │           ├── DeviceRowMapper.java
│   │   │           └── DeviceProfileRowMapper.java
│   │   ├── transport/                           # 传输适配器（输入适配器）
│   │   │   ├── service/
│   │   │   │   └── TransportService.java        # 传输服务
│   │   │   └── mqtt/
│   │   │       ├── MqttTransportService.java
│   │   │       └── MqttTransportHandler.java
│   │   ├── web/                                 # Web 适配器（输入适配器）
│   │   │   ├── controller/
│   │   │   │   ├── DeviceController.java
│   │   │   │   └── TelemetryController.java
│   │   │   └── dto/
│   │   │       ├── DeviceDto.java
│   │   │       ├── LatestTelemetryDto.java
│   │   │       └── TelemetryDataPointDto.java
│   │   └── rule/                                # 规则节点实现
│   │       ├── LogNode.java
│   │       ├── FilterNode.java
│   │       ├── SaveTelemetryNode.java
│   │       └── DefaultRuleNodeContext.java
│   │
│   ├── actor/                                   # Actor 系统
│   │   ├── MiniTbActor.java
│   │   ├── MiniTbActorSystem.java
│   │   ├── device/
│   │   │   └── DeviceActor.java
│   │   ├── ruleengine/
│   │   │   └── RuleEngineActor.java
│   │   └── msg/
│   │       ├── TransportToDeviceMsg.java
│   │       └── ToRuleEngineMsg.java
│   │
│   ├── datasource/                              # 数据源（输入适配器）
│   │   └── prometheus/
│   │       ├── PrometheusDataPuller.java
│   │       └── PrometheusQueryResult.java
│   │
│   ├── ruleengine/                              # 规则引擎服务
│   │   └── RuleEngineService.java
│   │
│   ├── storage/                                 # 存储服务
│   │   └── TelemetryStorage.java
│   │
│   ├── configuration/                           # Spring 配置
│   │   └── MiniTBConfiguration.java
│   │
│   └── MiniTBSpringBootApplication.java         # Spring Boot 启动类
│
├── src/main/resources/
│   ├── application.yml                          # 主配置
│   ├── application-sqlite.yml                   # SQLite 配置
│   └── static/                                  # 静态资源
│       ├── index.html                           # GPU 监控界面
│       └── gpu-monitor.js                       # 前端逻辑
│
├── src/test/java/com/minitb/
│   ├── domain/device/                           # 领域模型测试
│   │   ├── DeviceTest.java
│   │   └── DeviceProfileTest.java
│   ├── infrastructure/persistence/              # 持久化集成测试
│   │   ├── jpa/
│   │   │   └── JpaDeviceRepositoryAdapterTest.java
│   │   └── sqlite/
│   │       └── SqliteDeviceRepositoryAdapterTest.java
│   ├── application/service/                     # 服务层测试
│   │   └── DeviceServiceTest.java
│   ├── datasource/prometheus/                   # Prometheus 测试
│   │   └── PrometheusDataPullerTest.java
│   └── integration/                             # 集成测试
│       └── GpuMonitoringEndToEndTest.java       # GPU 端到端测试
│
├── pom.xml
├── start-gpu-monitor.sh                         # 快速启动脚本
├── HEXAGONAL_ARCHITECTURE.md                    # 六边形架构文档
└── README.md
```

---

## 🧪 测试

### 端到端测试：GPU 监控

```bash
# 设置环境变量（需要 Prometheus + DCGM Exporter）
export GPU_MONITORING_ENABLED=true

# 运行测试
mvn test -Dtest=GpuMonitoringEndToEndTest
```

**测试结果**:

```
╔════════════════════════════════════════════════════════╗
║   GPU 监控端到端测试 - NVIDIA TITAN V                  ║
╚════════════════════════════════════════════════════════╝

✅ Prometheus 服务器可用: http://192.168.30.134:9090
✅ DCGM 数据已被 Prometheus 抓取

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  测试双 GPU 监控
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📊 GPU 0 数据验证:
  ✓ gpu_utilization: 100 %
  ✓ memory_copy_utilization: 100 %
  ✓ gpu_temperature: 74 °C
  ✓ memory_temperature: 82 °C
  ✓ power_usage: 152.72 W
  ✓ memory_used: 614 MiB
  ✓ memory_free: 11442 MiB
  总计: 7/7 指标成功

📊 GPU 1 数据验证:
  ✓ gpu_utilization: 100 %
  ✓ memory_copy_utilization: 99 %
  ✓ gpu_temperature: 83 °C
  ✓ memory_temperature: 89 °C
  ✓ power_usage: 160.59 W
  ✓ memory_used: 614 MiB
  ✓ memory_free: 11434 MiB
  总计: 7/7 指标成功

╔════════════════════════════════════════════════════════╗
║   ✅ 双 GPU 监控测试通过                                ║
╚════════════════════════════════════════════════════════╝

测试摘要:
  - 监控设备数量: 2 (GPU 0, GPU 1)
  - 每设备指标数: 7
  - 总指标数: 14
  - 总耗时: 1112 ms

[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 所有测试

```bash
# 运行所有测试
mvn test

# 测试报告
[INFO] Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
```

---

## 📚 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **核心框架** | Spring Boot | 3.2.1 | 应用框架 |
| **Web** | Spring MVC | 6.1.2 | REST API |
| **持久化** | Spring Data JPA | 3.2.1 | JPA 持久化 |
| **数据库** | H2 Database | 2.2.224 | 内存数据库（JPA） |
| **数据库** | SQLite | 3.44.1.0 | 文件数据库 |
| **JSON** | Jackson | 2.15.3 | JSON 序列化 |
| **网络** | Netty | 4.1.100 | MQTT 服务器 |
| **日志** | SLF4J + Logback | 2.0.9 | 日志框架 |
| **构建工具** | Maven | 3.6+ | 依赖管理 |
| **Java** | OpenJDK | 17 | 运行环境 |
| **代码简化** | Lombok | 1.18.36 | 减少样板代码 |
| **测试** | JUnit 5 | 5.10.1 | 单元测试 |
| **测试** | Mockito | 5.7.0 | Mock 框架 |
| **前端** | Chart.js | 4.4.0 | 图表库 |

---

## 🚀 未来规划

- [ ] PostgreSQL / TimescaleDB 支持
- [ ] 分布式 Actor 集群（Akka Cluster）
- [ ] Kafka 消息队列集成
- [ ] WebSocket 实时推送
- [ ] Grafana 集成
- [ ] 告警规则引擎增强
- [ ] HTTP 数据源支持

---

## 📄 许可证

MIT License

---

**MiniTB - 基于 Spring Boot + Actor 模型 + 六边形架构的高性能物联网数据平台**

**现在支持**: GPU 监控 | Prometheus 数据拉取 | SQLite/JPA 双存储 | Web 可视化 | 完整测试覆盖
