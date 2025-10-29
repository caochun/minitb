# ThingsBoard 告警机制深度解析

## 📋 核心概念

### 1. 告警（Alarm）实体

告警是 ThingsBoard 中用于表示异常状态的核心实体。

**关键字段**:
```java
public class Alarm {
    private AlarmId id;                    // 告警唯一ID
    private TenantId tenantId;             // 租户ID
    private CustomerId customerId;         // 客户ID
    private String type;                   // 告警类型（如 "High Temperature"）
    private EntityId originator;           // 告警源（通常是设备ID）
    private AlarmSeverity severity;        // 严重级别：CRITICAL, MAJOR, MINOR, WARNING, INDETERMINATE
    
    // 状态标志
    private boolean acknowledged;          // 是否已确认
    private boolean cleared;               // 是否已清除
    
    // 时间戳
    private long startTs;                  // 告警开始时间
    private long endTs;                    // 告警结束时间（最后更新）
    private long ackTs;                    // 确认时间
    private long clearTs;                  // 清除时间
    
    // 详情和传播
    private JsonNode details;              // 告警详情（JSON）
    private boolean propagate;             // 是否传播到父实体
    private boolean propagateToOwner;      // 是否传播到所有者
    private List<String> propagateRelationTypes; // 传播的关系类型
}
```

### 2. 告警严重级别（AlarmSeverity）

```
CRITICAL        → 严重告警（最高级）
MAJOR           → 主要告警
MINOR           → 次要告警
WARNING         → 警告
INDETERMINATE   → 不确定
```

---

## 🏗️ 告警生成流程

### 核心组件

1. **DeviceProfile** → 定义告警规则
2. **TbDeviceProfileNode** → 规则引擎节点，评估告警条件
3. **AlarmState** → 管理单个设备的告警状态
4. **AlarmRuleState** → 评估具体的告警规则
5. **AlarmService** → 持久化告警到数据库

### 完整流程

```
设备上报遥测数据
    ↓
TransportService → DeviceActor
    ↓
RuleEngine → TbDeviceProfileNode (关键节点)
    ↓
获取设备的 DeviceProfile
    ↓
DeviceState.process(msg)
    ↓
遍历所有告警定义 (DeviceProfileAlarm)
    ↓
AlarmState.process() - 评估告警条件
    ↓
AlarmRuleState.eval() - 评估具体规则
    ↓
条件满足 → 创建/更新告警
条件不满足 → 清除告警
    ↓
AlarmService.createAlarm() / clearAlarm()
    ↓
持久化到数据库 + 发布事件
    ↓
告警通知 (WebSocket/Email/SMS)
```

---

## 📐 告警规则定义

### DeviceProfileAlarm 结构

```java
public class DeviceProfileAlarm {
    private String id;                                    // 告警规则ID
    private String alarmType;                             // 告警类型名称
    private TreeMap<AlarmSeverity, AlarmRule> createRules; // 创建告警的规则（按严重级别）
    private AlarmRule clearRule;                          // 清除告警的规则
    
    // 传播设置
    private boolean propagate;
    private boolean propagateToOwner;
    private boolean propagateToTenant;
    private List<String> propagateRelationTypes;
}
```

### AlarmRule 结构

```java
public class AlarmRule {
    private AlarmCondition condition;     // 告警条件
    private AlarmSchedule schedule;       // 生效时间段
    private String alarmDetails;          // 告警详情模板
    private DashboardId dashboardId;      // 关联的仪表板
}
```

### AlarmCondition 结构

```java
public class AlarmCondition {
    private List<AlarmConditionFilter> condition;  // 条件过滤器列表（AND 关系）
    private AlarmConditionSpec spec;               // 条件规范（SIMPLE/DURATION/REPEATING）
}
```

### AlarmConditionFilter 示例

```java
// 示例1: 温度 > 80
{
    "key": {
        "type": "TIME_SERIES",    // 时序数据
        "key": "temperature"       // 指标名
    },
    "valueType": "NUMERIC",
    "predicate": {
        "operation": "GREATER",    // 大于
        "value": {
            "defaultValue": 80.0
        }
    }
}

// 示例2: 风扇转速 < 500 RPM
{
    "key": {
        "type": "TIME_SERIES",
        "key": "fan_speed"
    },
    "valueType": "NUMERIC",
    "predicate": {
        "operation": "LESS",
        "value": {
            "defaultValue": 500
        }
    }
}
```

---

## 🔍 告警条件类型

### 1. SIMPLE（简单条件）

**特点**: 条件满足立即触发

```java
public class SimpleAlarmConditionSpec implements AlarmConditionSpec {
    // 无额外配置，条件满足即触发
}
```

**评估逻辑**:
```java
if (condition.eval(data)) {
    createAlarm();  // 立即创建告警
}
```

**示例**: 温度 > 80°C → 立即触发高温告警

---

### 2. DURATION（持续时间条件）

**特点**: 条件需要持续满足一段时间才触发

```java
public class DurationAlarmConditionSpec implements AlarmConditionSpec {
    private TimeUnit unit;           // 时间单位（SECONDS, MINUTES, HOURS）
    private FilterPredicateValue<Long> predicate;  // 持续时长阈值
}
```

**评估逻辑**:
```java
if (condition.eval(data)) {
    duration += (currentTs - lastEventTs);  // 累积持续时间
    if (duration > requiredDuration) {
        createAlarm();  // 持续时间达到阈值，创建告警
    }
} else {
    duration = 0;  // 条件不满足，重置计数器
}
```

**示例**: 温度 > 80°C 持续 5 分钟 → 触发告警

**状态**:
```java
class PersistedAlarmRuleState {
    private Long eventCount;      // 事件计数
    private Long lastEventTs;     // 上次事件时间戳
    private Long duration;        // 累积持续时间（毫秒）
}
```

---

### 3. REPEATING（重复次数条件）

**特点**: 条件需要在N次数据上报中满足M次才触发

```java
public class RepeatingAlarmConditionSpec implements AlarmConditionSpec {
    private FilterPredicateValue<Long> predicate;  // 重复次数阈值
}
```

**评估逻辑**:
```java
if (condition.eval(data)) {
    eventCount++;  // 累积满足次数
    if (eventCount >= requiredRepeats) {
        createAlarm();  // 达到重复次数，创建告警
    }
} else {
    eventCount = 0;  // 条件不满足，重置计数器
}
```

**示例**: 温度 > 80°C 连续出现 3 次 → 触发告警

---

## 🔄 告警状态机

### 告警生命周期

```
不存在 (None)
    ↓ [条件满足]
活动的 (Active) ← ─┐
    │               │ [条件再次满足]
    │ [确认]        │ [更新 endTs 或 severity]
    ↓               │
已确认 (Acknowledged) ┘
    ↓ [清除条件满足]
已清除 (Cleared)
    ↓ [手动删除或过期]
已删除 (Deleted)
```

### 状态转换

| 当前状态 | 条件 | 操作 | 结果状态 |
|---------|------|------|---------|
| 不存在 | 创建条件满足 | createAlarm() | Active |
| Active | 创建条件满足（严重级别更高） | updateAlarm() | Active (severity↑) |
| Active | 用户确认 | acknowledgeAlarm() | Acknowledged |
| Active/Acknowledged | 清除条件满足 | clearAlarm() | Cleared |
| Cleared | 创建条件再次满足 | createAlarm() | Active (新告警) |

---

## 💻 核心代码解析

### 1. TbDeviceProfileNode - 规则引擎节点

**职责**: 接收设备消息，评估告警规则

```java
@Override
public void onMsg(TbContext ctx, TbMsg msg) {
    if (EntityType.DEVICE.equals(msg.getOriginator().getEntityType())) {
        DeviceId deviceId = new DeviceId(msg.getOriginator().getId());
        
        // 1. 获取或创建设备状态
        DeviceState deviceState = getOrCreateDeviceState(ctx, deviceId, null, false);
        
        // 2. 处理消息（评估告警）
        if (deviceState != null) {
            deviceState.process(ctx, msg);
        }
    }
}
```

**关键**: 每个设备维护一个 `DeviceState` 对象

---

### 2. DeviceState - 设备状态管理

**职责**: 管理单个设备的所有告警状态

```java
class DeviceState {
    private final Map<String, AlarmState> alarmStates;  // 告警类型 → 告警状态
    private final ProfileState deviceProfile;           // 设备配置文件
    
    public void process(TbContext ctx, TbMsg msg) {
        // 1. 解析遥测数据
        DataSnapshot data = new DataSnapshot(msg);
        
        // 2. 遍历设备配置文件中的所有告警定义
        for (DeviceProfileAlarm alarmDef : deviceProfile.getAlarmDefinitions()) {
            AlarmState alarmState = alarmStates.get(alarmDef.getAlarmType());
            
            // 3. 评估告警条件
            boolean alarmChanged = alarmState.process(ctx, msg, data, update);
            
            // 4. 如果告警状态改变，发送通知消息
            if (alarmChanged) {
                ctx.tellNext(createAlarmMsg(alarmState), "Alarm Created/Updated");
            }
        }
    }
}
```

---

### 3. AlarmState - 单个告警的状态管理

**职责**: 管理特定类型告警的创建和清除逻辑

```java
class AlarmState {
    private List<AlarmRuleState> createRulesSortedBySeverityDesc;  // 创建规则（按严重级别降序）
    private AlarmRuleState clearState;                              // 清除规则
    private Alarm currentAlarm;                                     // 当前活动的告警
    
    public boolean process(TbContext ctx, TbMsg msg, DataSnapshot data) {
        // 1. 初始化当前告警（从数据库加载）
        initCurrentAlarm(ctx);
        
        // 2. 评估创建和清除条件
        return createOrClearAlarms(ctx, msg, data, update, (state, data) -> state.eval(data));
    }
    
    private boolean createOrClearAlarms(...) {
        AlarmRuleState resultState = null;
        
        // 3. 按严重级别从高到低评估创建规则
        for (AlarmRuleState state : createRulesSortedBySeverityDesc) {
            AlarmEvalResult result = state.eval(data);
            
            if (result == AlarmEvalResult.TRUE) {
                resultState = state;
                break;  // 找到第一个满足的规则
            }
        }
        
        // 4. 如果有规则满足
        if (resultState != null) {
            if (currentAlarm == null) {
                // 创建新告警
                createNewAlarm(ctx, resultState);
            } else if (currentAlarm.getSeverity() != resultState.getSeverity()) {
                // 更新告警严重级别
                updateAlarmSeverity(ctx, resultState);
            } else {
                // 更新告警 endTs
                updateAlarm(ctx);
            }
        }
        // 5. 评估清除条件
        else if (clearState != null && clearState.eval(data) == AlarmEvalResult.TRUE) {
            if (currentAlarm != null && !currentAlarm.isCleared()) {
                clearAlarm(ctx);
            }
        }
    }
}
```

**告警去重**: 相同 originator + type 的告警只会存在一个活动实例

---

### 4. AlarmRuleState - 告警规则评估

**职责**: 评估单个告警规则是否满足

```java
class AlarmRuleState {
    private AlarmSeverity severity;              // 严重级别
    private AlarmRule alarmRule;                 // 告警规则定义
    private AlarmConditionSpec spec;             // 条件规范（SIMPLE/DURATION/REPEATING）
    private PersistedAlarmRuleState state;       // 持久化状态（计数器、持续时间）
    
    public AlarmEvalResult eval(DataSnapshot data) {
        // 1. 检查时间调度（是否在生效时间段内）
        boolean active = isActive(data, data.getTs());
        
        // 2. 根据条件类型评估
        switch (spec.getType()) {
            case SIMPLE:
                return (active && evalCondition(data)) ? TRUE : FALSE;
            
            case DURATION:
                return evalDuration(data, active);
            
            case REPEATING:
                return evalRepeating(data, active);
        }
    }
    
    private AlarmEvalResult evalDuration(DataSnapshot data, boolean active) {
        if (active && evalCondition(data)) {
            // 累积持续时间
            if (state.getLastEventTs() > 0) {
                duration += (data.getTs() - state.getLastEventTs());
            }
            state.setLastEventTs(data.getTs());
            
            // 检查是否达到阈值
            if (duration > requiredDurationInMs) {
                return AlarmEvalResult.TRUE;
            }
            return AlarmEvalResult.NOT_YET_TRUE;
        } else {
            // 条件不满足，重置
            duration = 0;
            return AlarmEvalResult.FALSE;
        }
    }
    
    private boolean evalCondition(DataSnapshot data) {
        // 评估所有过滤器（AND 逻辑）
        for (AlarmConditionFilter filter : alarmRule.getCondition().getCondition()) {
            if (!evalFilter(filter, data)) {
                return false;
            }
        }
        return true;
    }
}
```

---

## 🎯 告警规则配置示例

### 示例1: 简单条件 - GPU 高温告警

```json
{
    "id": "highGpuTemperature",
    "alarmType": "GPU High Temperature",
    "createRules": {
        "CRITICAL": {
            "condition": {
                "condition": [
                    {
                        "key": {
                            "type": "TIME_SERIES",
                            "key": "gpu_temperature"
                        },
                        "valueType": "NUMERIC",
                        "predicate": {
                            "operation": "GREATER",
                            "value": {
                                "defaultValue": 85.0
                            }
                        }
                    }
                ],
                "spec": {
                    "type": "SIMPLE"
                }
            }
        },
        "MAJOR": {
            "condition": {
                "condition": [
                    {
                        "key": {
                            "type": "TIME_SERIES",
                            "key": "gpu_temperature"
                        },
                        "valueType": "NUMERIC",
                        "predicate": {
                            "operation": "GREATER",
                            "value": {
                                "defaultValue": 75.0
                            }
                        }
                    }
                ],
                "spec": {
                    "type": "SIMPLE"
                }
            }
        }
    },
    "clearRule": {
        "condition": {
            "condition": [
                {
                    "key": {
                        "type": "TIME_SERIES",
                        "key": "gpu_temperature"
                    },
                    "valueType": "NUMERIC",
                    "predicate": {
                        "operation": "LESS_OR_EQUAL",
                        "value": {
                            "defaultValue": 70.0
                        }
                    }
                }
            ],
            "spec": {
                "type": "SIMPLE"
            }
        }
    }
}
```

**工作流程**:
1. 温度 > 85°C → 创建 CRITICAL 告警
2. 75°C < 温度 ≤ 85°C → 更新为 MAJOR 告警
3. 温度 ≤ 70°C → 清除告警

---

### 示例2: 持续时间条件 - 风扇故障

```json
{
    "id": "fanFailure",
    "alarmType": "Fan Failure",
    "createRules": {
        "CRITICAL": {
            "condition": {
                "condition": [
                    {
                        "key": {
                            "type": "TIME_SERIES",
                            "key": "fan_speed"
                        },
                        "valueType": "NUMERIC",
                        "predicate": {
                            "operation": "LESS",
                            "value": {
                                "defaultValue": 500
                            }
                        }
                    }
                ],
                "spec": {
                    "type": "DURATION",
                    "unit": "MINUTES",
                    "predicate": {
                        "defaultValue": 5
                    }
                }
            }
        }
    },
    "clearRule": {
        "condition": {
            "condition": [
                {
                    "key": {
                        "type": "TIME_SERIES",
                        "key": "fan_speed"
                    },
                    "valueType": "NUMERIC",
                    "predicate": {
                        "operation": "GREATER_OR_EQUAL",
                        "value": {
                            "defaultValue": 800
                        }
                    }
                }
            ],
            "spec": {
                "type": "SIMPLE"
            }
        }
    }
}
```

**工作流程**:
1. 风扇转速 < 500 RPM 持续 5 分钟 → 创建告警
2. 风扇转速 ≥ 800 RPM → 清除告警

---

### 示例3: 重复条件 - 网络抖动

```json
{
    "id": "networkJitter",
    "alarmType": "Network Connection Lost",
    "createRules": {
        "MAJOR": {
            "condition": {
                "condition": [
                    {
                        "key": {
                            "type": "ATTRIBUTE",
                            "key": "active"
                        },
                        "valueType": "BOOLEAN",
                        "predicate": {
                            "operation": "EQUAL",
                            "value": {
                                "defaultValue": false
                            }
                        }
                    }
                ],
                "spec": {
                    "type": "REPEATING",
                    "predicate": {
                        "defaultValue": 3
                    }
                }
            }
        }
    }
}
```

**工作流程**:
连续 3 次检测到 active=false → 创建告警

---

## 📊 告警评估逻辑详解

### 条件过滤器操作符

```java
// 数值比较
EQUAL, NOT_EQUAL
GREATER, GREATER_OR_EQUAL
LESS, LESS_OR_EQUAL

// 字符串比较
STARTS_WITH, ENDS_WITH
CONTAINS, NOT_CONTAINS

// 布尔比较
EQUAL (true/false)

// 复杂条件
AND, OR
```

### 多条件组合

```java
// 示例: CPU温度 > 80 AND 风扇转速 < 1000
{
    "condition": [
        {
            "key": {"type": "TIME_SERIES", "key": "cpu_temperature"},
            "valueType": "NUMERIC",
            "predicate": {"operation": "GREATER", "value": {"defaultValue": 80}}
        },
        {
            "key": {"type": "TIME_SERIES", "key": "fan_speed"},
            "valueType": "NUMERIC",
            "predicate": {"operation": "LESS", "value": {"defaultValue": 1000}}
        }
    ]
}
```

**注意**: 所有条件之间是 **AND** 关系（必须全部满足）

---

## 🔧 告警服务（AlarmService）

### 核心方法

```java
public interface AlarmService {
    // 创建或更新活动告警
    AlarmApiCallResult createAlarm(AlarmCreateOrUpdateActiveRequest request);
    
    // 确认告警
    AlarmApiCallResult acknowledgeAlarm(TenantId tenantId, AlarmId alarmId, long ackTs);
    
    // 清除告警
    AlarmApiCallResult clearAlarm(TenantId tenantId, AlarmId alarmId, long clearTs, JsonNode details);
    
    // 删除告警
    boolean deleteAlarm(TenantId tenantId, AlarmId alarmId);
    
    // 查询告警
    PageData<AlarmInfo> findAlarms(AlarmQuery query);
}
```

### 创建告警实现

```java
@Override
public AlarmApiCallResult createAlarm(AlarmCreateOrUpdateActiveRequest request) {
    // 1. 验证请求
    validateAlarmRequest(request);
    
    // 2. 获取客户ID
    CustomerId customerId = entityService.fetchEntityCustomerId(
        request.getTenantId(), 
        request.getOriginator()
    ).orElse(null);
    
    // 3. 调用 DAO 创建或更新告警
    AlarmApiCallResult result = alarmDao.createOrUpdateActiveAlarm(request);
    
    // 4. 发布事件
    if (result.getAlarm() != null) {
        eventPublisher.publishEvent(
            SaveEntityEvent.builder()
                .tenantId(result.getAlarm().getTenantId())
                .entityId(result.getAlarm().getId())
                .entity(result)
                .created(true)
                .build()
        );
    }
    
    // 5. 处理传播
    return withPropagated(result);
}
```

---

## 🗄️ 数据库存储

### Alarm 表结构

```sql
CREATE TABLE alarm (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID,
    created_time BIGINT NOT NULL,
    
    -- 告警基本信息
    originator_id UUID NOT NULL,
    originator_type VARCHAR(32) NOT NULL,
    type VARCHAR(255) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    
    -- 时间戳
    start_ts BIGINT NOT NULL,
    end_ts BIGINT NOT NULL,
    ack_ts BIGINT DEFAULT 0,
    clear_ts BIGINT DEFAULT 0,
    assign_ts BIGINT DEFAULT 0,
    
    -- 状态
    acknowledged BOOLEAN DEFAULT FALSE,
    cleared BOOLEAN DEFAULT FALSE,
    
    -- 其他
    assignee_id UUID,
    additional_info TEXT,
    propagate BOOLEAN DEFAULT FALSE,
    propagate_to_owner BOOLEAN DEFAULT FALSE,
    propagate_to_tenant BOOLEAN DEFAULT FALSE,
    propagate_relation_types TEXT,
    
    -- 索引
    CONSTRAINT idx_alarm_originator_type UNIQUE (originator_id, type, cleared)
);
```

**关键约束**: `(originator_id, type, cleared)` 唯一索引确保同一设备的同一类型告警只有一个活动实例

---

## 🔔 告警通知流程

### 事件发布

```java
// 告警创建后发布事件
eventPublisher.publishEvent(
    SaveEntityEvent.builder()
        .tenantId(alarm.getTenantId())
        .entityId(alarm.getId())
        .entity(alarm)
        .created(true)
        .build()
);
```

### 通知渠道

1. **WebSocket**: 实时推送到前端
2. **Email**: 发送邮件通知
3. **SMS**: 发送短信通知
4. **Slack/钉钉**: 第三方集成
5. **Webhook**: HTTP 回调

---

## 💡 在 MiniTB 中实现告警

基于 ThingsBoard 的设计，在 MiniTB 中实现告警需要：

### 1. 扩展 DeviceProfile

```java
@Data
public class DeviceProfile {
    // ... 现有字段 ...
    
    // 新增: 告警定义列表
    private List<AlarmDefinition> alarmDefinitions;
}

@Data
public class AlarmDefinition {
    private String alarmType;                           // 如 "High Temperature"
    private Map<AlarmSeverity, AlarmRule> createRules;  // 创建规则
    private AlarmRule clearRule;                        // 清除规则
}

@Data
public class AlarmRule {
    private List<AlarmCondition> conditions;  // 条件列表（AND）
    private AlarmConditionType type;          // SIMPLE/DURATION/REPEATING
    private Long durationMs;                  // DURATION: 持续时间
    private Integer repeatCount;              // REPEATING: 重复次数
}

@Data
public class AlarmCondition {
    private String key;              // 遥测键（如 "temperature"）
    private String operator;         // 操作符：>, <, >=, <=, ==, !=
    private Double threshold;        // 阈值
}
```

### 2. 创建 AlarmNode (规则节点)

```java
@Component
public class AlarmNode implements RuleNode {
    
    private final AlarmService alarmService;
    private final TelemetryStorage telemetryStorage;
    
    // 设备告警状态缓存
    private final Map<DeviceId, Map<String, AlarmState>> deviceAlarmStates = new ConcurrentHashMap<>();
    
    @Override
    public void process(Message msg) {
        if (msg.getType() != MessageType.POST_TELEMETRY_REQUEST) {
            return;
        }
        
        DeviceId deviceId = msg.getOriginator();
        Device device = deviceService.findById(deviceId).orElse(null);
        if (device == null) return;
        
        DeviceProfile profile = deviceService.findProfileById(device.getDeviceProfileId()).orElse(null);
        if (profile == null || profile.getAlarmDefinitions() == null) return;
        
        // 解析遥测数据
        Map<String, Object> telemetry = parseTelemetry(msg.getData());
        
        // 评估每个告警定义
        for (AlarmDefinition alarmDef : profile.getAlarmDefinitions()) {
            evaluateAlarm(deviceId, alarmDef, telemetry, msg.getTs());
        }
    }
    
    private void evaluateAlarm(DeviceId deviceId, AlarmDefinition alarmDef, 
                               Map<String, Object> telemetry, long ts) {
        
        // 1. 获取或创建告警状态
        AlarmState alarmState = getOrCreateAlarmState(deviceId, alarmDef.getAlarmType());
        
        // 2. 按严重级别从高到低评估创建规则
        AlarmSeverity triggeredSeverity = null;
        for (AlarmSeverity severity : AlarmSeverity.values()) {
            AlarmRule rule = alarmDef.getCreateRules().get(severity);
            if (rule != null && evaluateRule(rule, telemetry, ts, alarmState)) {
                triggeredSeverity = severity;
                break;
            }
        }
        
        // 3. 处理告警
        if (triggeredSeverity != null) {
            // 创建或更新告警
            if (alarmState.getCurrentAlarm() == null) {
                createAlarm(deviceId, alarmDef.getAlarmType(), triggeredSeverity, telemetry);
            } else if (alarmState.getCurrentAlarm().getSeverity() != triggeredSeverity) {
                updateAlarmSeverity(alarmState.getCurrentAlarm(), triggeredSeverity);
            }
        } else {
            // 评估清除条件
            if (alarmDef.getClearRule() != null && 
                evaluateRule(alarmDef.getClearRule(), telemetry, ts, alarmState)) {
                clearAlarm(alarmState.getCurrentAlarm());
            }
        }
    }
    
    private boolean evaluateRule(AlarmRule rule, Map<String, Object> telemetry, 
                                 long ts, AlarmState state) {
        // 1. 评估所有条件（AND 逻辑）
        boolean allConditionsMet = true;
        for (AlarmCondition condition : rule.getConditions()) {
            if (!evaluateCondition(condition, telemetry)) {
                allConditionsMet = false;
                break;
            }
        }
        
        // 2. 根据条件类型判断
        switch (rule.getType()) {
            case SIMPLE:
                return allConditionsMet;
            
            case DURATION:
                return evaluateDuration(allConditionsMet, ts, rule.getDurationMs(), state);
            
            case REPEATING:
                return evaluateRepeating(allConditionsMet, rule.getRepeatCount(), state);
            
            default:
                return false;
        }
    }
    
    private boolean evaluateCondition(AlarmCondition condition, Map<String, Object> telemetry) {
        Object value = telemetry.get(condition.getKey());
        if (value == null) return false;
        
        double numValue = ((Number) value).doubleValue();
        double threshold = condition.getThreshold();
        
        switch (condition.getOperator()) {
            case ">": return numValue > threshold;
            case ">=": return numValue >= threshold;
            case "<": return numValue < threshold;
            case "<=": return numValue <= threshold;
            case "==": return numValue == threshold;
            case "!=": return numValue != threshold;
            default: return false;
        }
    }
}
```

### 3. AlarmService 实现

```java
@Service
public class AlarmService {
    
    private final Map<AlarmId, Alarm> alarms = new ConcurrentHashMap<>();
    private final TelemetryStorage telemetryStorage;
    
    public Alarm createAlarm(DeviceId deviceId, String type, AlarmSeverity severity, 
                            Map<String, Object> telemetry) {
        // 检查是否已存在活动告警
        Alarm existing = findActiveAlarm(deviceId, type);
        
        if (existing != null) {
            // 更新现有告警
            existing.setEndTs(System.currentTimeMillis());
            if (existing.getSeverity() != severity) {
                existing.setSeverity(severity);
            }
            return existing;
        }
        
        // 创建新告警
        Alarm alarm = Alarm.builder()
                .id(AlarmId.random())
                .type(type)
                .originator(deviceId)
                .severity(severity)
                .startTs(System.currentTimeMillis())
                .endTs(System.currentTimeMillis())
                .acknowledged(false)
                .cleared(false)
                .details(createDetails(telemetry))
                .build();
        
        alarms.put(alarm.getId(), alarm);
        
        // 发布告警事件
        publishAlarmEvent(alarm, "CREATED");
        
        log.warn("🚨 告警创建: [{}] {} - 严重级别: {}", 
                 deviceId, type, severity);
        
        return alarm;
    }
    
    public void clearAlarm(Alarm alarm) {
        alarm.setCleared(true);
        alarm.setClearTs(System.currentTimeMillis());
        
        publishAlarmEvent(alarm, "CLEARED");
        
        log.info("✅ 告警清除: [{}] {}", alarm.getOriginator(), alarm.getType());
    }
    
    private Alarm findActiveAlarm(DeviceId deviceId, String type) {
        return alarms.values().stream()
                .filter(a -> a.getOriginator().equals(deviceId))
                .filter(a -> a.getType().equals(type))
                .filter(a -> !a.isCleared())
                .findFirst()
                .orElse(null);
    }
}
```

---

## 📝 在 MiniTB 中应用告警的简化示例

### GPU 高温告警配置

```java
// 在 DataInitializer 中配置告警
DeviceProfile gpuProfile = DeviceProfile.builder()
    .name("NVIDIA GPU Monitor")
    // ... 其他配置 ...
    .alarmDefinitions(List.of(
        // 高温告警
        AlarmDefinition.builder()
            .alarmType("High GPU Temperature")
            .createRules(Map.of(
                AlarmSeverity.CRITICAL, AlarmRule.builder()
                    .conditions(List.of(
                        AlarmCondition.builder()
                            .key("gpu_temperature")
                            .operator(">")
                            .threshold(85.0)
                            .build()
                    ))
                    .type(AlarmConditionType.SIMPLE)
                    .build(),
                AlarmSeverity.MAJOR, AlarmRule.builder()
                    .conditions(List.of(
                        AlarmCondition.builder()
                            .key("gpu_temperature")
                            .operator(">")
                            .threshold(75.0)
                            .build()
                    ))
                    .type(AlarmConditionType.SIMPLE)
                    .build()
            ))
            .clearRule(AlarmRule.builder()
                .conditions(List.of(
                    AlarmCondition.builder()
                        .key("gpu_temperature")
                        .operator("<=")
                        .threshold(70.0)
                        .build()
                ))
                .type(AlarmConditionType.SIMPLE)
                .build())
            .build(),
        
        // 风扇故障告警
        AlarmDefinition.builder()
            .alarmType("Fan Failure")
            .createRules(Map.of(
                AlarmSeverity.CRITICAL, AlarmRule.builder()
                    .conditions(List.of(
                        AlarmCondition.builder()
                            .key("fan_speed")
                            .operator("<")
                            .threshold(500.0)
                            .build()
                    ))
                    .type(AlarmConditionType.DURATION)
                    .durationMs(300000L)  // 5 分钟
                    .build()
            ))
            .clearRule(AlarmRule.builder()
                .conditions(List.of(
                    AlarmCondition.builder()
                        .key("fan_speed")
                        .operator(">=")
                        .threshold(800.0)
                        .build()
                ))
                .type(AlarmConditionType.SIMPLE)
                .build())
            .build()
    ))
    .build();
```

---

## 🎯 关键设计要点

### 1. 告警去重

- 通过 `(originator_id, type, cleared)` 唯一约束
- 同一设备的同一类型告警只有一个活动实例
- 清除后可以创建新的告警实例

### 2. 严重级别优先级

- 按 CRITICAL → MAJOR → MINOR → WARNING → INDETERMINATE 顺序评估
- 满足最高严重级别的条件就停止评估
- 可以动态升级或降级告警严重级别

### 3. 状态持久化

- 对于 DURATION 和 REPEATING 类型，需要持久化状态
- `PersistedAlarmRuleState`: eventCount, lastEventTs, duration
- 保存到 `rule_node_state` 表或内存中

### 4. 时间调度

- 告警规则可以配置生效时间段
- 支持特定时间（如工作时间 9:00-18:00）
- 支持自定义时间表（如工作日生效）

### 5. 告警传播

- 可以沿关系链向父实体传播
- 可以传播到所有者（租户/客户）
- 可以传播到租户级别

---

## 🚀 实现建议

### MiniTB 简化实现步骤

1. **定义告警数据模型**
   - 复用现有的 `Alarm.java`
   - 在 `DeviceProfile` 中添加 `alarmDefinitions`

2. **创建 AlarmNode**
   - 在规则链中添加告警节点
   - 评估遥测数据是否满足告警条件

3. **实现 AlarmService**
   - 告警的 CRUD 操作
   - 告警去重逻辑
   - 状态转换管理

4. **添加告警存储**
   - SQLite: 新增 `alarm` 表
   - JPA: 新增 `AlarmEntity`

5. **Web 界面展示**
   - 告警列表页面
   - 实时告警通知（WebSocket）
   - 告警确认/清除操作

---

## 📊 实际应用场景

### GPU 监控告警

```
高温告警:
  CRITICAL: gpu_temperature > 85°C
  MAJOR:    gpu_temperature > 75°C
  Clear:    gpu_temperature <= 70°C

ECC 错误告警:
  CRITICAL: ecc_dbe_aggregate > 0 (双比特错误)
  MAJOR:    ecc_sbe_aggregate > 100 (单比特错误过多)

功耗超限:
  MAJOR: power_usage > power_limit
```

### BMC 监控告警

```
CPU 过热:
  CRITICAL: cpu_temperature > 90°C 持续 10 分钟
  MAJOR:    cpu_temperature > 80°C
  Clear:    cpu_temperature <= 75°C

风扇故障:
  CRITICAL: fan_speed < 500 RPM 持续 5 分钟
  Clear:    fan_speed >= 800 RPM

电压异常:
  MAJOR: abs(voltage_12v - 12.0) / 12.0 > 0.1 (偏差 >10%)
```

---

**文档创建时间**: 2025-10-28
