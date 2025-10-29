# MiniTB 告警系统使用指南

## 📋 快速开始

### 1. 在 DeviceProfile 中配置告警规则

```java
// 在 DataInitializer 中配置 GPU 高温告警
DeviceProfile gpuProfile = DeviceProfile.builder()
    .name("GPU 监控配置")
    .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
    .telemetryDefinitions(createGpuTelemetryDefinitions())
    // 添加告警规则
    .alarmRules(Arrays.asList(
        // GPU 高温告警
        createGpuTemperatureAlarmRule(),
        // GPU 高功耗告警
        createGpuPowerAlarmRule()
    ))
    .build();

// GPU 温度告警规则
private AlarmRule createGpuTemperatureAlarmRule() {
    return AlarmRule.builder()
        .id("gpu_high_temperature")
        .alarmType("GPU High Temperature")
        .createConditions(Map.of(
            // CRITICAL: > 85°C 持续 30 秒
            AlarmSeverity.CRITICAL, AlarmCondition.duration(30,
                AlarmConditionFilter.greaterThan("gpu_temperature", 85.0)
            ),
            // MAJOR: > 80°C 持续 30 秒
            AlarmSeverity.MAJOR, AlarmCondition.duration(30,
                AlarmConditionFilter.greaterThan("gpu_temperature", 80.0)
            ),
            // WARNING: > 75°C（立即）
            AlarmSeverity.WARNING, AlarmCondition.simple(
                AlarmConditionFilter.greaterThan("gpu_temperature", 75.0)
            )
        ))
        .clearCondition(AlarmCondition.simple(
            AlarmConditionFilter.lessThan("gpu_temperature", 70.0)
        ))
        .build();
}

// GPU 功耗告警规则
private AlarmRule createGpuPowerAlarmRule() {
    return AlarmRule.builder()
        .id("gpu_high_power")
        .alarmType("GPU High Power Usage")
        .createConditions(Map.of(
            AlarmSeverity.WARNING, AlarmCondition.simple(
                AlarmConditionFilter.greaterThan("power_usage", 200.0)
            )
        ))
        .clearCondition(AlarmCondition.simple(
            AlarmConditionFilter.lessThan("power_usage", 180.0)
        ))
        .build();
}
```

### 2. 将告警评估节点添加到规则链

```java
// RuleEngineService 或 DataInitializer 中
RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
rootRuleChain
    .addNode(new LogNode("入口日志"))
    .addNode(new SaveTelemetryNode(telemetryStorage))  // 先保存数据
    .addNode(new AlarmEvaluatorNode(alarmEvaluator, deviceService))  // 评估告警
    .addNode(new LogNode("完成"));
```

### 3. 自动触发（数据到达时）

告警评估会在每次遥测数据到达时自动触发：

```
遥测数据到达
  ↓
SaveTelemetryNode (保存数据)
  ↓
AlarmEvaluatorNode (评估告警)
  ├─ 获取设备的告警规则
  ├─ 评估每个规则的条件
  ├─ 条件满足 → AlarmService.createOrUpdateAlarm()
  └─ 条件不满足 → AlarmService.clearAlarm()
```

### 4. 通过 REST API 访问告警

```bash
# 获取所有活动告警
GET http://localhost:8080/api/alarms/active

# 获取设备的告警
GET http://localhost:8080/api/alarms/device/{deviceId}

# 确认告警
POST http://localhost:8080/api/alarms/{alarmId}/ack

# 清除告警（手动）
POST http://localhost:8080/api/alarms/{alarmId}/clear

# 获取告警统计
GET http://localhost:8080/api/alarms/stats
```

## 📊 完整示例：GPU 监控告警

### 场景说明

监控 NVIDIA GPU 的温度和功耗，在异常时自动创建告警。

### 1. 配置告警规则

```java
@Component
public class GpuAlarmConfiguration {
    
    /**
     * 配置 GPU 告警规则
     */
    public List<AlarmRule> createGpuAlarmRules() {
        List<AlarmRule> rules = new ArrayList<>();
        
        // 1. GPU 温度告警（多级）
        rules.add(AlarmRule.builder()
            .id("gpu_temperature_alarm")
            .alarmType("GPU Temperature Alarm")
            .createConditions(Map.of(
                AlarmSeverity.CRITICAL, AlarmCondition.duration(30,
                    AlarmConditionFilter.greaterThan("gpu_temperature", 85.0)
                ),
                AlarmSeverity.MAJOR, AlarmCondition.duration(30,
                    AlarmConditionFilter.greaterThan("gpu_temperature", 80.0)
                ),
                AlarmSeverity.WARNING, AlarmCondition.duration(30,
                    AlarmConditionFilter.greaterThan("gpu_temperature", 75.0)
                )
            ))
            .clearCondition(AlarmCondition.simple(
                AlarmConditionFilter.lessThan("gpu_temperature", 70.0)
            ))
            .build());
        
        // 2. 显存温度告警
        rules.add(AlarmRule.builder()
            .id("memory_temperature_alarm")
            .alarmType("Memory Temperature Alarm")
            .createConditions(Map.of(
                AlarmSeverity.CRITICAL, AlarmCondition.duration(30,
                    AlarmConditionFilter.greaterThan("memory_temperature", 90.0)
                ),
                AlarmSeverity.WARNING, AlarmCondition.duration(30,
                    AlarmConditionFilter.greaterThan("memory_temperature", 85.0)
                )
            ))
            .clearCondition(AlarmCondition.simple(
                AlarmConditionFilter.lessThan("memory_temperature", 80.0)
            ))
            .build());
        
        // 3. 功耗告警
        rules.add(AlarmRule.builder()
            .id("power_usage_alarm")
            .alarmType("High Power Usage")
            .createConditions(Map.of(
                AlarmSeverity.WARNING, AlarmCondition.simple(
                    AlarmConditionFilter.greaterThan("power_usage", 200.0)
                )
            ))
            .clearCondition(AlarmCondition.simple(
                AlarmConditionFilter.lessThan("power_usage", 180.0)
            ))
            .build());
        
        // 4. GPU 利用率异常低（可能故障）
        rules.add(AlarmRule.builder()
            .id("gpu_utilization_low")
            .alarmType("GPU Utilization Abnormally Low")
            .createConditions(Map.of(
                // 连续 5 次低于 5%
                AlarmSeverity.WARNING, AlarmCondition.repeating(5,
                    AlarmConditionFilter.lessThan("gpu_utilization", 5.0)
                )
            ))
            .clearCondition(AlarmCondition.simple(
                AlarmConditionFilter.greaterThan("gpu_utilization", 10.0)
            ))
            .build());
        
        return rules;
    }
}
```

### 2. 运行效果

```
╔════════════════════════════════════════════════════════╗
║         MiniTB GPU 监控系统启动                         ║
╚════════════════════════════════════════════════════════╝

✅ 告警表初始化完成
✅ GPU 监控配置已加载 (4 个告警规则)
✅ 2 个设备 Actor 已创建
✅ Prometheus 数据拉取已启动 (每 2 秒)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  实时监控中...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[14:32:15] GPU 0: 温度=74°C, 功耗=152W, 利用率=100%
[14:32:17] GPU 0: 温度=76°C, 功耗=155W, 利用率=100%
[14:32:19] GPU 0: 温度=78°C, 功耗=158W, 利用率=100%
[14:32:21] 🔔 开始持续评估: GPU Temperature Alarm - GPU 0
[14:32:23] GPU 0: 温度=80°C, 功耗=160W, 利用率=100%
...
[14:32:45] ⚠️ 持续条件满足: GPU Temperature Alarm - GPU 0 (30 秒)
[14:32:45] ✅ 告警已创建: GPU Temperature Alarm [WARNING] - GPU 0

[14:32:47] GPU 0: 温度=82°C, 功耗=165W, 利用率=100%
[14:32:49] ⚠️ 告警严重程度已更新: GPU Temperature Alarm WARNING → MAJOR - GPU 0

[14:33:15] GPU 0: 温度=68°C, 功耗=145W, 利用率=98%
[14:33:17] 🔕 告警已清除: GPU Temperature Alarm - GPU 0
```

### 3. Web 界面集成

```html
<!-- 告警指示器 -->
<div class="device-header">
    <h2>GPU 0: NVIDIA TITAN V</h2>
    <div id="alarm-indicator" class="alarm-badge"></div>
</div>

<!-- 告警列表 -->
<div class="alarm-panel">
    <h3>活动告警</h3>
    <div id="alarm-list"></div>
</div>
```

```javascript
// 加载设备告警
async function loadDeviceAlarms(deviceId) {
    const response = await fetch(`/api/alarms/device/${deviceId}?status=ACTIVE_UNACK`);
    const alarms = await response.json();
    
    // 显示告警指示器
    if (alarms.length > 0) {
        const maxSeverity = Math.max(...alarms.map(a => getSeverityLevel(a.severity)));
        showAlarmIndicator(maxSeverity);
    }
    
    // 渲染告警列表
    renderAlarmList(alarms);
}

function showAlarmIndicator(severity) {
    const indicator = document.getElementById('alarm-indicator');
    indicator.className = `alarm-badge alarm-${severity.toLowerCase()}`;
    indicator.textContent = `${alarms.length} 告警`;
}

function renderAlarmList(alarms) {
    const list = document.getElementById('alarm-list');
    list.innerHTML = alarms.map(alarm => `
        <div class="alarm-item alarm-${alarm.severity.toLowerCase()}">
            <div class="alarm-header">
                <span class="alarm-severity">${alarm.severity}</span>
                <span class="alarm-time">${formatTime(alarm.startTs)}</span>
            </div>
            <div class="alarm-content">
                <h4>${alarm.type}</h4>
                <p>设备: ${alarm.deviceName}</p>
            </div>
            <div class="alarm-actions">
                <button onclick="acknowledgeAlarm('${alarm.id}')">确认</button>
            </div>
        </div>
    `).join('');
}

// 确认告警
async function acknowledgeAlarm(alarmId) {
    await fetch(`/api/alarms/${alarmId}/ack`, { method: 'POST' });
    loadDeviceAlarms(currentDeviceId);  // 刷新列表
}
```

## 🎯 高级用法

### 1. 复杂条件（多个过滤器）

```java
// 同时满足多个条件（AND 关系）
AlarmCondition.simple(
    AlarmConditionFilter.greaterThan("gpu_temperature", 85.0),
    AlarmConditionFilter.greaterThan("power_usage", 200.0)
)
```

### 2. 持续条件避免抖动

```java
// 温度必须持续 60 秒 > 85°C 才触发告警
AlarmCondition.duration(60,
    AlarmConditionFilter.greaterThan("gpu_temperature", 85.0)
)
```

### 3. 重复条件检测间歇性问题

```java
// 连续 10 次采样都 > 85°C 才触发告警
AlarmCondition.repeating(10,
    AlarmConditionFilter.greaterThan("gpu_temperature", 85.0)
)
```

### 4. 字符串匹配条件

```java
// 检查错误消息
AlarmCondition.simple(
    AlarmConditionFilter.builder()
        .key("error_message")
        .operator(FilterOperator.CONTAINS)
        .value("timeout")
        .build()
)
```

### 5. 编程方式创建和管理告警

```java
@Service
public class CustomAlarmService {
    
    @Autowired
    private AlarmService alarmService;
    
    /**
     * 手动创建告警
     */
    public void createCustomAlarm(DeviceId deviceId, String deviceName) {
        alarmService.createAlarm(
            deviceId,
            deviceName,
            "Custom Alert",
            AlarmSeverity.WARNING
        );
    }
    
    /**
     * 批量确认设备的所有告警
     */
    public void acknowledgeAllDeviceAlarms(DeviceId deviceId) {
        List<Alarm> alarms = alarmService.findByDeviceAndStatus(
            deviceId, AlarmStatus.ACTIVE_UNACK
        );
        
        for (Alarm alarm : alarms) {
            alarmService.acknowledgeAlarm(alarm.getId());
        }
    }
}
```

## 📈 监控和统计

### 获取告警统计

```java
// REST API
GET /api/alarms/stats

// 响应
{
    "total": 15,
    "active": 8,
    "unacknowledged": 5,
    "cleared": 7,
    "critical": 2,
    "major": 3,
    "minor": 5,
    "warning": 5
}
```

### 自定义告警仪表盘

```javascript
async function renderAlarmDashboard() {
    const stats = await fetch('/api/alarms/stats').then(r => r.json());
    
    // 渲染告警数量卡片
    document.getElementById('active-alarms').textContent = stats.active;
    document.getElementById('unack-alarms').textContent = stats.unacknowledged;
    
    // 渲染严重程度分布图
    renderSeverityChart({
        'CRITICAL': stats.critical,
        'MAJOR': stats.major,
        'MINOR': stats.minor,
        'WARNING': stats.warning
    });
}
```

## 🧪 测试告警规则

```java
@Test
void testGpuTemperatureAlarm() {
    // 1. 创建测试设备
    Device device = createTestGpuDevice();
    DeviceProfile profile = createProfileWithTemperatureAlarm();
    
    // 2. 模拟正常温度
    Map<String, TsKvEntry> normalData = Map.of(
        "gpu_temperature", new LongDataEntry(70L)
    );
    alarmEvaluator.evaluate(device, profile, normalData);
    
    // 验证：没有告警
    assertEquals(0, alarmService.findByDevice(device.getId()).size());
    
    // 3. 模拟高温
    Map<String, TsKvEntry> highTempData = Map.of(
        "gpu_temperature", new LongDataEntry(90L)
    );
    alarmEvaluator.evaluate(device, profile, highTempData);
    
    // 验证：创建了告警
    List<Alarm> alarms = alarmService.findByDevice(device.getId());
    assertEquals(1, alarms.size());
    assertEquals(AlarmSeverity.CRITICAL, alarms.get(0).getSeverity());
}
```

## 💡 最佳实践

### 1. 合理设置阈值

```java
// ❌ 不好：阈值太敏感，容易误报
AlarmCondition.simple(
    AlarmConditionFilter.greaterThan("temperature", 70.0)  // 70°C 太低
)

// ✅ 好：使用持续条件避免瞬时抖动
AlarmCondition.duration(30,  // 持续 30 秒
    AlarmConditionFilter.greaterThan("temperature", 85.0)
)
```

### 2. 分级告警

```java
// ✅ 设置多个严重程度级别
Map.of(
    AlarmSeverity.CRITICAL, condition1,  // 85°C
    AlarmSeverity.MAJOR, condition2,     // 80°C
    AlarmSeverity.WARNING, condition3    // 75°C
)
```

### 3. 合理的清除条件

```java
// ✅ 清除阈值应低于告警阈值（避免频繁切换）
.createCondition(AlarmCondition.simple(
    AlarmConditionFilter.greaterThan("temperature", 85.0)
))
.clearCondition(AlarmCondition.simple(
    AlarmConditionFilter.lessThan("temperature", 75.0)  // 低 10°C
))
```

### 4. 命名规范

```java
// ✅ 使用清晰的告警类型名称
.alarmType("GPU Temperature Critical")
.alarmType("High Memory Usage")
.alarmType("Network Connection Lost")

// ❌ 避免模糊的名称
.alarmType("Alert 1")
.alarmType("Problem")
```

## 🚀 性能优化

### 1. 避免过多的告警规则

- 每个设备配置建议不超过 10 个告警规则
- 使用多级严重程度而不是创建多个相似规则

### 2. 合理使用 DURATION 和 REPEATING

- DURATION 适合检测持续性问题
- REPEATING 适合检测间歇性问题
- 避免时间过短导致评估上下文频繁重置

### 3. 清理历史告警

```java
// 定期清理已清除的旧告警
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
public void cleanupOldAlarms() {
    long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
    
    List<Alarm> oldAlarms = alarmRepository.findByTimeRange(0, sevenDaysAgo);
    oldAlarms.stream()
        .filter(Alarm::isCleared)
        .forEach(alarm -> alarmRepository.deleteById(alarm.getId()));
}
```

这个告警系统实现了 ThingsBoard 的核心告警功能，并保持了 MiniTB 的轻量级和清晰架构！


