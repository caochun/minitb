# MiniTB 告警系统设计方案

## 📋 设计目标

参考 ThingsBoard 的告警机制，设计一个轻量级但完整的告警系统，遵循 MiniTB 的六边形架构和 DDD 设计原则。

## 🏗️ 架构设计

### 1. 核心概念

```
DeviceProfile (设备配置)
    └─ AlarmRule (告警规则) ← 配置在这里
        ├─ createConditions: Map<AlarmSeverity, AlarmCondition>
        └─ clearCondition: AlarmCondition

AlarmCondition (告警条件)
    ├─ filters: List<AlarmConditionFilter>  (key, operator, value)
    ├─ type: SIMPLE | DURATION | REPEATING
    └─ spec: AlarmConditionSpec (duration, count)

Alarm (告警实例)
    ├─ originator: DeviceId
    ├─ type: String (e.g. "High Temperature")
    ├─ severity: CRITICAL | MAJOR | MINOR | WARNING | INDETERMINATE
    ├─ status: ACTIVE_UNACK | ACTIVE_ACK | CLEARED_UNACK | CLEARED_ACK
    ├─ startTs, endTs, ackTs, clearTs
    └─ details: JsonNode
```

### 2. 数据流程

```
遥测数据到达
    ↓
DeviceActor
    ↓
RuleEngineActor
    ↓
【新增】AlarmEvaluatorNode (告警评估节点)
    ├─ 获取设备的告警规则 (从 DeviceProfile)
    ├─ 评估所有规则的条件
    │   ├─ SIMPLE: 立即评估
    │   ├─ DURATION: 持续 N 秒满足条件
    │   └─ REPEATING: 连续 N 次满足条件
    ├─ 条件满足 → 创建/更新告警
    └─ 条件不满足 → 清除告警
    ↓
【新增】CreateAlarmNode / ClearAlarmNode
    ↓
AlarmService.createAlarm() / clearAlarm()
    ↓
AlarmRepository.save()
    ↓
【可选】NotificationNode (通知节点)
    └─ 发送告警通知 (Email, Slack, WebSocket)
```

### 3. 告警生命周期

```
1. 创建阶段
   条件满足 → AlarmService.createAlarm()
   ├─ 检查是否存在相同类型的活动告警
   ├─ 不存在 → 创建新告警 (ACTIVE_UNACK)
   └─ 存在 → 更新严重程度 (如果变化)

2. 确认阶段
   用户操作 → AlarmService.acknowledgeAlarm()
   └─ ACTIVE_UNACK → ACTIVE_ACK

3. 清除阶段
   清除条件满足 → AlarmService.clearAlarm()
   ├─ ACTIVE_UNACK → CLEARED_UNACK
   └─ ACTIVE_ACK → CLEARED_ACK

4. 删除阶段
   用户操作 → AlarmService.deleteAlarm()
   └─ 物理删除告警记录
```

## 📦 实现组件

### Domain Layer (领域层)

```
com.minitb.domain.alarm/
├── Alarm.java                          # 告警实体
├── AlarmId.java                        # 告警ID (强类型)
├── AlarmSeverity.java                  # 严重程度枚举
├── AlarmStatus.java                    # 状态枚举
├── AlarmRule.java                      # 告警规则
├── AlarmCondition.java                 # 告警条件
├── AlarmConditionFilter.java           # 条件过滤器
├── AlarmConditionSpec.java             # 条件规格 (SIMPLE/DURATION/REPEATING)
├── AlarmRepository.java                # 仓储接口 (Port)
└── AlarmEvaluationContext.java         # 评估上下文
```

### Application Layer (应用层)

```
com.minitb.application.service/
├── AlarmService.java                   # 告警服务接口
└── impl/
    ├── AlarmServiceImpl.java           # 告警服务实现
    └── AlarmEvaluator.java             # 告警评估引擎
```

### Infrastructure Layer (基础设施层)

```
com.minitb.infrastructure/
├── persistence/
│   ├── jpa/
│   │   ├── entity/AlarmEntity.java
│   │   ├── SpringDataAlarmRepository.java
│   │   └── JpaAlarmRepositoryAdapter.java
│   └── sqlite/
│       └── SqliteAlarmRepositoryAdapter.java
├── rule/
│   ├── AlarmEvaluatorNode.java         # 告警评估节点
│   ├── CreateAlarmNode.java            # 创建告警节点
│   └── ClearAlarmNode.java             # 清除告警节点
└── web/
    ├── controller/AlarmController.java # REST API
    └── dto/
        ├── AlarmDto.java
        ├── AlarmRuleDto.java
        └── AlarmQueryDto.java
```

## 💡 核心实现

### 1. 告警规则配置 (在 DeviceProfile 中)

```java
DeviceProfile gpuProfile = DeviceProfile.builder()
    .name("GPU 监控配置")
    .dataSourceType(DataSourceType.PROMETHEUS)
    .telemetryDefinitions(createGpuTelemetryDefinitions())
    // 【新增】告警规则
    .alarmRules(Arrays.asList(
        // 高温告警
        AlarmRule.builder()
            .id("high_temperature_alarm")
            .alarmType("High Temperature")
            .createConditions(Map.of(
                // CRITICAL: GPU 温度 > 85°C 持续 30 秒
                AlarmSeverity.CRITICAL, AlarmCondition.builder()
                    .type(AlarmConditionType.DURATION)
                    .spec(AlarmConditionSpec.duration(30))  // 30 秒
                    .filters(Arrays.asList(
                        AlarmConditionFilter.builder()
                            .key("gpu_temperature")
                            .operator(FilterOperator.GREATER_THAN)
                            .value(85.0)
                            .build()
                    ))
                    .build(),
                
                // MAJOR: GPU 温度 > 80°C 持续 30 秒
                AlarmSeverity.MAJOR, AlarmCondition.builder()
                    .type(AlarmConditionType.DURATION)
                    .spec(AlarmConditionSpec.duration(30))
                    .filters(Arrays.asList(
                        AlarmConditionFilter.builder()
                            .key("gpu_temperature")
                            .operator(FilterOperator.GREATER_THAN)
                            .value(80.0)
                            .build()
                    ))
                    .build()
            ))
            // 清除条件: GPU 温度 < 75°C
            .clearCondition(AlarmCondition.builder()
                .type(AlarmConditionType.SIMPLE)
                .filters(Arrays.asList(
                    AlarmConditionFilter.builder()
                        .key("gpu_temperature")
                        .operator(FilterOperator.LESS_THAN)
                        .value(75.0)
                        .build()
                ))
                .build())
            .build(),
        
        // 高功耗告警
        AlarmRule.builder()
            .id("high_power_alarm")
            .alarmType("High Power Usage")
            .createConditions(Map.of(
                AlarmSeverity.WARNING, AlarmCondition.builder()
                    .type(AlarmConditionType.SIMPLE)
                    .filters(Arrays.asList(
                        AlarmConditionFilter.builder()
                            .key("power_usage")
                            .operator(FilterOperator.GREATER_THAN)
                            .value(200.0)
                            .build()
                    ))
                    .build()
            ))
            .clearCondition(AlarmCondition.builder()
                .type(AlarmConditionType.SIMPLE)
                .filters(Arrays.asList(
                    AlarmConditionFilter.builder()
                        .key("power_usage")
                        .operator(FilterOperator.LESS_THAN)
                        .value(180.0)
                        .build()
                ))
                .build())
            .build()
    ))
    .build();
```

### 2. 告警评估引擎

```java
@Service
public class AlarmEvaluator {
    
    /**
     * 评估设备的所有告警规则
     */
    public List<AlarmAction> evaluate(
            Device device,
            DeviceProfile profile,
            Map<String, TsKvEntry> latestData) {
        
        List<AlarmAction> actions = new ArrayList<>();
        
        // 遍历所有告警规则
        for (AlarmRule rule : profile.getAlarmRules()) {
            
            // 1. 评估创建条件 (按严重程度从高到低)
            AlarmSeverity matchedSeverity = null;
            for (Map.Entry<AlarmSeverity, AlarmCondition> entry : 
                    rule.getCreateConditions().entrySet()) {
                
                if (evaluateCondition(entry.getValue(), latestData)) {
                    matchedSeverity = entry.getKey();
                    break;  // 匹配到第一个(最高)严重程度
                }
            }
            
            // 2. 查找当前是否有活动告警
            Optional<Alarm> existingAlarm = alarmService
                .findLatestByOriginatorAndType(device.getId(), rule.getAlarmType());
            
            if (matchedSeverity != null) {
                // 条件满足 → 创建或更新告警
                if (existingAlarm.isEmpty() || existingAlarm.get().isCleared()) {
                    actions.add(AlarmAction.create(rule.getAlarmType(), matchedSeverity));
                } else if (!existingAlarm.get().getSeverity().equals(matchedSeverity)) {
                    actions.add(AlarmAction.updateSeverity(
                        existingAlarm.get().getId(), matchedSeverity));
                }
            } else {
                // 3. 评估清除条件
                if (existingAlarm.isPresent() && !existingAlarm.get().isCleared()) {
                    if (evaluateCondition(rule.getClearCondition(), latestData)) {
                        actions.add(AlarmAction.clear(existingAlarm.get().getId()));
                    }
                }
            }
        }
        
        return actions;
    }
    
    /**
     * 评估单个条件
     */
    private boolean evaluateCondition(
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        switch (condition.getType()) {
            case SIMPLE:
                return evaluateSimple(condition, latestData);
            case DURATION:
                return evaluateDuration(condition, latestData);
            case REPEATING:
                return evaluateRepeating(condition, latestData);
            default:
                return false;
        }
    }
    
    /**
     * 简单条件: 立即判断
     */
    private boolean evaluateSimple(
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        // 所有过滤器必须同时满足 (AND 关系)
        for (AlarmConditionFilter filter : condition.getFilters()) {
            TsKvEntry entry = latestData.get(filter.getKey());
            if (entry == null || !matchesFilter(entry, filter)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 持续条件: 持续 N 秒满足
     * 需要维护状态 (首次满足时间)
     */
    private boolean evaluateDuration(
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        // 获取或创建评估上下文
        AlarmEvaluationContext context = getOrCreateContext(condition);
        
        boolean currentMatch = evaluateSimple(condition, latestData);
        long now = System.currentTimeMillis();
        
        if (currentMatch) {
            if (context.getFirstMatchTs() == null) {
                context.setFirstMatchTs(now);
                return false;  // 首次匹配，还未持续
            } else {
                long duration = now - context.getFirstMatchTs();
                return duration >= condition.getSpec().getDurationSeconds() * 1000;
            }
        } else {
            context.setFirstMatchTs(null);  // 重置
            return false;
        }
    }
    
    /**
     * 重复条件: 连续 N 次满足
     */
    private boolean evaluateRepeating(
            AlarmCondition condition,
            Map<String, TsKvEntry> latestData) {
        
        AlarmEvaluationContext context = getOrCreateContext(condition);
        
        boolean currentMatch = evaluateSimple(condition, latestData);
        
        if (currentMatch) {
            context.incrementMatchCount();
            return context.getMatchCount() >= condition.getSpec().getRepeatingCount();
        } else {
            context.resetMatchCount();
            return false;
        }
    }
}
```

### 3. 规则链集成

```java
// 在 RuleChain 中添加告警评估节点
RuleChain rootRuleChain = new RuleChain("Root Rule Chain");
rootRuleChain
    .addNode(new LogNode("入口日志"))
    .addNode(new SaveTelemetryNode(storage))  // 先保存数据
    .addNode(new AlarmEvaluatorNode(alarmService, deviceService))  // 【新增】评估告警
    .addNode(new LogNode("完成"));
```

### 4. REST API

```java
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {
    
    // 获取设备的所有告警
    @GetMapping("/device/{deviceId}")
    public List<AlarmDto> getDeviceAlarms(
            @PathVariable String deviceId,
            @RequestParam(required = false) AlarmStatus status) {
        // ...
    }
    
    // 获取告警详情
    @GetMapping("/{alarmId}")
    public AlarmDto getAlarm(@PathVariable String alarmId) {
        // ...
    }
    
    // 确认告警
    @PostMapping("/{alarmId}/ack")
    public AlarmDto acknowledgeAlarm(@PathVariable String alarmId) {
        // ...
    }
    
    // 清除告警
    @PostMapping("/{alarmId}/clear")
    public AlarmDto clearAlarm(@PathVariable String alarmId) {
        // ...
    }
    
    // 删除告警
    @DeleteMapping("/{alarmId}")
    public void deleteAlarm(@PathVariable String alarmId) {
        // ...
    }
    
    // 获取告警统计
    @GetMapping("/stats")
    public AlarmStatsDto getAlarmStats() {
        // ...
    }
}
```

## 🗄️ 数据库设计

### SQLite Schema

```sql
CREATE TABLE alarm (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    device_name TEXT,
    type TEXT NOT NULL,
    severity TEXT NOT NULL,
    status TEXT NOT NULL,
    start_ts INTEGER NOT NULL,
    end_ts INTEGER,
    ack_ts INTEGER,
    clear_ts INTEGER,
    details TEXT,  -- JSON
    created_time INTEGER NOT NULL,
    FOREIGN KEY (device_id) REFERENCES device(id)
);

CREATE INDEX idx_alarm_device_id ON alarm(device_id);
CREATE INDEX idx_alarm_type ON alarm(type);
CREATE INDEX idx_alarm_status ON alarm(status);
CREATE INDEX idx_alarm_start_ts ON alarm(start_ts DESC);
```

## 📊 前端展示

### 告警列表界面

```javascript
// 获取活动告警
async function loadActiveAlarms() {
    const response = await fetch('/api/alarms?status=ACTIVE_UNACK');
    const alarms = await response.json();
    
    renderAlarmList(alarms);
}

// 告警卡片
function renderAlarmCard(alarm) {
    return `
        <div class="alarm-card alarm-${alarm.severity.toLowerCase()}">
            <div class="alarm-header">
                <span class="alarm-severity">${alarm.severity}</span>
                <span class="alarm-status">${alarm.status}</span>
            </div>
            <div class="alarm-content">
                <h3>${alarm.type}</h3>
                <p>设备: ${alarm.deviceName}</p>
                <p>开始时间: ${formatTime(alarm.startTs)}</p>
            </div>
            <div class="alarm-actions">
                <button onclick="acknowledgeAlarm('${alarm.id}')">确认</button>
                <button onclick="viewDetails('${alarm.id}')">详情</button>
            </div>
        </div>
    `;
}

// 实时告警通知
const ws = new WebSocket('ws://localhost:8080/ws/alarms');
ws.onmessage = (event) => {
    const alarm = JSON.parse(event.data);
    showAlarmNotification(alarm);
};
```

### 设备页面集成

```javascript
// 在设备监控页面显示告警状态
async function loadDeviceWithAlarms(deviceId) {
    // 加载设备数据
    const device = await fetch(`/api/devices/${deviceId}`).then(r => r.json());
    
    // 加载设备告警
    const alarms = await fetch(`/api/alarms/device/${deviceId}?status=ACTIVE_UNACK`)
        .then(r => r.json());
    
    // 显示告警指示器
    if (alarms.length > 0) {
        const maxSeverity = Math.max(...alarms.map(a => getSeverityLevel(a.severity)));
        showAlarmIndicator(maxSeverity);
    }
}
```

## 🧪 测试策略

### 1. 单元测试

```java
@Test
void testSimpleConditionEvaluation() {
    // 准备数据
    Map<String, TsKvEntry> data = Map.of(
        "gpu_temperature", new LongDataEntry("gpu_temperature", 85L)
    );
    
    // 准备条件
    AlarmCondition condition = AlarmCondition.builder()
        .type(AlarmConditionType.SIMPLE)
        .filters(Arrays.asList(
            AlarmConditionFilter.builder()
                .key("gpu_temperature")
                .operator(FilterOperator.GREATER_THAN)
                .value(80.0)
                .build()
        ))
        .build();
    
    // 评估
    boolean result = alarmEvaluator.evaluateCondition(condition, data);
    
    assertTrue(result);
}
```

### 2. 集成测试

```java
@SpringBootTest
class AlarmIntegrationTest {
    
    @Test
    void testAlarmLifecycle() {
        // 1. 创建设备和配置
        Device device = createTestDevice();
        DeviceProfile profile = createProfileWithAlarmRules();
        
        // 2. 发送超过阈值的数据
        sendTelemetry(device, Map.of("gpu_temperature", 86.0));
        
        // 等待告警创建
        await().atMost(5, SECONDS).until(() -> 
            alarmService.findByDevice(device.getId()).isPresent()
        );
        
        // 3. 验证告警已创建
        Alarm alarm = alarmService.findByDevice(device.getId()).get();
        assertEquals(AlarmSeverity.CRITICAL, alarm.getSeverity());
        assertEquals(AlarmStatus.ACTIVE_UNACK, alarm.getStatus());
        
        // 4. 确认告警
        alarmService.acknowledgeAlarm(alarm.getId());
        assertEquals(AlarmStatus.ACTIVE_ACK, alarm.getStatus());
        
        // 5. 发送恢复数据
        sendTelemetry(device, Map.of("gpu_temperature", 70.0));
        
        // 等待告警清除
        await().atMost(5, SECONDS).until(() -> 
            alarmService.findById(alarm.getId()).get().isCleared()
        );
        
        assertEquals(AlarmStatus.CLEARED_ACK, alarm.getStatus());
    }
}
```

## 🚀 实施步骤

1. **Phase 1: 核心模型** (1-2 天)
   - 创建 Alarm 领域模型
   - 实现 AlarmRepository 接口和适配器
   - 基本的 AlarmService

2. **Phase 2: 评估引擎** (2-3 天)
   - 实现 AlarmEvaluator
   - 支持 SIMPLE/DURATION/REPEATING 条件
   - 集成到规则链

3. **Phase 3: REST API** (1 天)
   - 实现 AlarmController
   - 告警 CRUD 操作
   - 告警查询和统计

4. **Phase 4: 前端界面** (2-3 天)
   - 告警列表页面
   - 设备页面集成
   - 实时通知 (可选)

5. **Phase 5: 测试和优化** (2 天)
   - 单元测试
   - 集成测试
   - 性能优化

## 📈 扩展功能 (可选)

1. **告警通知**
   - Email 通知
   - Slack/钉钉通知
   - WebSocket 实时推送

2. **告警仪表盘**
   - 告警趋势图
   - 设备告警热力图
   - 告警响应时间统计

3. **告警分组和过滤**
   - 按设备类型分组
   - 按严重程度过滤
   - 按时间范围查询

4. **告警升级策略**
   - 未确认告警自动升级
   - 告警值班表
   - 告警静默期

## 💡 关键设计决策

1. **告警规则存储在 DeviceProfile 中**
   - 优点: 相同类型的设备共享规则，易于管理
   - ThingsBoard 也采用这种设计

2. **评估引擎集成到规则链**
   - 优点: 复用现有的 Actor 异步处理能力
   - 告警评估不阻塞数据保存

3. **支持多种条件类型**
   - SIMPLE: 立即判断，适合阈值告警
   - DURATION: 持续判断，避免瞬时抖动
   - REPEATING: 重复判断，适合间歇性问题

4. **告警状态机**
   - ACTIVE_UNACK → ACTIVE_ACK → CLEARED_ACK
   - 支持未确认直接清除 (ACTIVE_UNACK → CLEARED_UNACK)

5. **轻量级实现**
   - 不实现复杂的告警传播 (ThingsBoard 有)
   - 不实现告警关联 (ThingsBoard 有)
   - 专注核心告警生命周期

这个设计方案既参考了 ThingsBoard 的成熟经验，又保持了 MiniTB 的轻量级和清晰的架构风格！


