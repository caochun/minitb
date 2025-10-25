# MiniTB 服务层架构

## 📁 目录结构

```
minitb/src/main/java/com/minitb/service/
├── AbstractEntityService.java            # 抽象服务基类
├── MiniTbException.java                  # 业务异常类
├── MiniTbErrorCode.java                  # 错误码枚举
├── device/                               # 设备服务层
│   ├── TbDeviceService.java              # 设备服务接口
│   └── DefaultTbDeviceService.java      # 设备服务默认实现
├── asset/                                # 资产服务层
│   ├── TbAssetService.java               # 资产服务接口
│   └── DefaultTbAssetService.java        # 资产服务默认实现
├── deviceprofile/                        # 设备配置服务层
│   ├── TbDeviceProfileService.java      # 设备配置服务接口
│   └── DefaultTbDeviceProfileService.java # 设备配置服务默认实现
├── rulechain/                            # 规则链服务层
│   ├── TbRuleChainService.java           # 规则链服务接口
│   └── DefaultTbRuleChainService.java    # 规则链服务默认实现
└── relation/                             # 关系服务层（已存在）
    └── EntityRelationService.java
```

## 🏗️ 架构设计

### 分层架构
```
Controller层 (REST API)
    ↓
TbService层 (业务逻辑层)  
    ↓
Dao层 (数据访问层)
    ↓
Entity层 (实体对象)
```

### 设计模式

#### 1. 接口分离原则
- 每个实体都有对应的服务接口
- 接口定义业务契约
- 实现类负责具体业务逻辑

#### 2. 依赖倒置原则
- 高层模块依赖抽象接口
- 低层模块实现具体接口
- 便于测试和扩展

#### 3. 单一职责原则
- 每个服务只负责一个实体的业务逻辑
- 职责清晰，便于维护

#### 4. 开闭原则
- 对扩展开放，对修改关闭
- 可以轻松添加新的服务实现

## 🔧 核心功能

### 设备服务 (TbDeviceService)
- 设备CRUD操作
- 设备配置管理
- 访问令牌管理
- 设备查询和验证

### 资产服务 (TbAssetService)
- 资产CRUD操作
- 资产类型管理
- 资产标签管理
- 资产查询和验证

### 设备配置服务 (TbDeviceProfileService)
- 设备配置CRUD操作
- 遥测定义管理
- 数据源类型管理
- 配置验证和检查

### 规则链服务 (TbRuleChainService)
- 规则链CRUD操作
- 规则节点管理
- 根规则链设置
- 规则链执行

## 🚀 使用示例

### 设备服务使用
```java
@Autowired
private TbDeviceService deviceService;

// 创建设备
Device device = new Device();
device.setName("温度传感器");
device.setType("SENSOR");
device.setAccessToken("sensor-001");
Device savedDevice = deviceService.save(device);

// 查询设备
Optional<Device> deviceOpt = deviceService.findById(deviceId);
Device device = deviceService.getById(deviceId);

// 更新设备配置
deviceService.updateDeviceProfile(deviceId, profileId);
```

### 资产服务使用
```java
@Autowired
private TbAssetService assetService;

// 创建资产
Asset asset = new Asset();
asset.setName("智能建筑");
asset.setType("BUILDING");
Asset savedAsset = assetService.save(asset);

// 查询资产
List<Asset> assets = assetService.findByType("BUILDING");
```

### 设备配置服务使用
```java
@Autowired
private TbDeviceProfileService profileService;

// 创建设备配置
DeviceProfile profile = new DeviceProfile();
profile.setName("传感器配置");
profile.setDataSourceType(DataSourceType.MQTT);
DeviceProfile savedProfile = profileService.save(profile);

// 添加遥测定义
TelemetryDefinition tempDef = TelemetryDefinition.simple("temperature", DataType.DOUBLE);
profileService.addTelemetryDefinition(profileId, tempDef);
```

### 规则链服务使用
```java
@Autowired
private TbRuleChainService ruleChainService;

// 创建规则链
RuleChain ruleChain = new RuleChain();
ruleChain.setName("数据处理链");
RuleChain savedRuleChain = ruleChainService.save(ruleChain);

// 添加规则节点
RuleNode filterNode = new FilterNode("filter-1", "temperature > 25");
ruleChainService.addRuleNode(ruleChainId, filterNode);

// 执行规则链
ruleChainService.executeRuleChain(ruleChainId, message);
```

## 🎯 优势

1. **松耦合**: 接口依赖，便于测试和扩展
2. **可维护**: 职责清晰，代码结构良好
3. **可扩展**: 支持插件化和功能扩展
4. **可测试**: 便于单元测试和集成测试
5. **一致性**: 统一的错误处理和日志记录
6. **性能**: 优化的数据访问和缓存策略

## 📝 注意事项

1. 所有服务方法都包含完整的参数验证
2. 统一的异常处理和错误码
3. 详细的操作日志记录
4. 事务管理和数据一致性
5. 性能优化和缓存策略
