# MiniTB 监控指标汇总

## 📊 GPU 监控指标 (Prometheus/DCGM)

当前支持 **17 个** GPU 监控指标：

### 基础指标 (原有 7 个)
1. **gpu_utilization** - GPU利用率 (%)
2. **memory_copy_utilization** - 内存拷贝带宽利用率 (%)
3. **gpu_temperature** - GPU温度 (°C)
4. **memory_temperature** - 显存温度 (°C)
5. **power_usage** - 功耗 (W)
6. **memory_used** - 已用显存 (MiB)
7. **memory_free** - 空闲显存 (MiB)

### 新增指标 (10 个)
8. **sm_clock** - SM时钟频率 (MHz)
9. **memory_clock** - 显存时钟频率 (MHz)
10. **sm_utilization** - SM利用率 (%)
11. **pcie_tx_throughput** - PCIe发送吞吐量 (KB/s)
12. **pcie_rx_throughput** - PCIe接收吞吐量 (KB/s)
13. **ecc_sbe_aggregate** - ECC单比特错误总数 (次)
14. **ecc_dbe_aggregate** - ECC双比特错误总数 (次)
15. **power_limit** - 功耗上限 (W)
16. **fan_speed** - 风扇转速 (%)
17. **nvlink_bandwidth** - NVLink总带宽 (MB/s)

### 数据源配置
- **类型**: Prometheus
- **端点**: 在 Device.configuration 中配置 (PrometheusDeviceConfiguration)
- **标签**: 通过 `gpu=<id>` 标签区分不同设备
- **拉取周期**: 2 秒

### Prometheus 查询示例
```promql
# GPU 利用率
DCGM_FI_DEV_GPU_UTIL{gpu="0"}

# SM 时钟频率
DCGM_FI_DEV_SM_CLOCK{gpu="0"}

# PCIe 吞吐量
DCGM_FI_PROF_PCIE_TX_BYTES{gpu="0"}
```

---

## 🖥️ BMC 监控指标 (IPMI)

当前支持 **17 个** 服务器 BMC 监控指标：

### 基础指标 (原有 7 个)
1. **cpu0_temperature** - CPU0温度 (°C)
2. **cpu1_temperature** - CPU1温度 (°C)
3. **cpu0_fan_speed** - CPU0风扇转速 (RPM)
4. **cpu1_fan_speed** - CPU1风扇转速 (RPM)
5. **voltage_12v** - 12V电压 (V)
6. **voltage_5v** - 5V电压 (V)
7. **memory_temperature** - 内存温度 (°C)

### 新增指标 (10 个)
8. **motherboard_temperature** - 主板温度 (°C)
9. **system_fan1_speed** - 系统风扇1转速 (RPM)
10. **system_fan2_speed** - 系统风扇2转速 (RPM)
11. **voltage_3_3v** - 3.3V电压 (V)
12. **cpu0_vcore** - CPU0核心电压 (V)
13. **cpu1_vcore** - CPU1核心电压 (V)
14. **psu1_input_power** - PSU1输入功率 (W)
15. **psu2_input_power** - PSU2输入功率 (W)
16. **inlet_temperature** - 进风口温度 (°C)
17. **outlet_temperature** - 出风口温度 (°C)

### 数据源配置
- **类型**: IPMI
- **连接信息**: 在 Device.configuration 中配置 (IpmiDeviceConfiguration)
  - host: BMC IP地址
  - username: IPMI用户名
  - password: IPMI密码
  - driver: IPMI驱动 (如 "LAN_2_0")
- **拉取周期**: 30 秒 (可配置)
- **协议**: ipmitool + lanplus

### ipmitool 命令示例
```bash
# 查看所有传感器
ipmitool -I lanplus -H <BMC_IP> -U <USERNAME> -P <PASSWORD> sensor list

# 查看 CPU 温度
ipmitool -I lanplus -H <BMC_IP> -U <USERNAME> -P <PASSWORD> sensor get CPU0_TEMP
```

---

## 🏗️ 架构特点

### 六边形架构 (Hexagonal Architecture)
- **领域层** (`domain/`): 定义 `TelemetryDefinition`, `DeviceProfile`, `DeviceConfiguration`
- **应用层** (`application/`): `DataInitializer` 初始化默认指标配置
- **基础设施层** (`infrastructure/`):
  - `datasource/prometheus/`: Prometheus 数据拉取
  - `datasource/ipmi/`: IPMI 数据拉取
  - `persistence/`: 多种存储适配器 (JPA/SQLite)

### 可扩展性
1. **添加新指标**: 在 `DeviceProfile.telemetryDefinitions` 中增加 `TelemetryDefinition`
2. **添加新协议**: 实现 `ProtocolConfig` 接口 (如 `PrometheusConfig`, `IpmiConfig`)
3. **添加新数据源**: 创建新的 DataPuller (如 `PrometheusDataPuller`, `IpmiDataPuller`)
4. **添加新存储**: 实现 Repository 接口 (如 `JpaDeviceRepositoryAdapter`, `SqliteDeviceRepositoryAdapter`)

### 设备配置策略
- **PrometheusDeviceConfiguration**: endpoint + label
- **IpmiDeviceConfiguration**: host + username + password + driver
- 通过 Jackson 多态序列化存储为 JSON

---

## 📈 实际应用

### GPU 监控示例
```
设备: NVIDIA TITAN V - GPU 0
端点: http://192.168.30.134:9090
标签: gpu=0

实时数据:
- GPU 利用率: 45%
- SM 时钟: 1200 MHz
- 显存时钟: 850 MHz
- 功耗: 75W / 250W
- 温度: 68°C
- PCIe TX: 1024 KB/s
- ECC 错误: 0
```

### BMC 监控示例
```
设备: Gigabyte MZ72-HB2 服务器
BMC IP: 114.212.81.58

实时数据:
- CPU0 温度: 30°C
- CPU1 温度: 33°C
- CPU0 风扇: 1500 RPM
- CPU1 风扇: 1200 RPM
- 系统风扇1: 2000 RPM
- PSU1 功率: 320W
- PSU2 功率: 315W
- 进风口温度: 22°C
- 12V: 12.35V
```

---

## 🚀 如何添加更多指标

### 1. GPU 指标 (Prometheus)
在 `DataInitializer.createGpuTelemetryDefinitions()` 中添加:

```java
defs.add(TelemetryDefinition.builder()
    .key("新指标key")
    .displayName("显示名称")
    .dataType(DataType.DOUBLE)
    .unit("单位")
    .protocolConfig(PrometheusConfig.builder()
        .promQL("DCGM_FI_<METRIC_NAME>")
        .build())
    .build());
```

### 2. BMC 指标 (IPMI)
在 `BmcMonitoringEndToEndTest.createBmcTelemetryDefinitions()` 或生产环境的初始化代码中添加:

```java
defs.add(TelemetryDefinition.builder()
    .key("新指标key")
    .displayName("显示名称")
    .dataType(DataType.DOUBLE)
    .unit("单位")
    .protocolConfig(IpmiConfig.builder()
        .sensorName("SENSOR_NAME")  // ipmitool 输出的传感器名称
        .build())
    .build());
```

### 3. 验证指标
- 重启应用后，新指标会自动初始化到数据库
- 数据拉取器会根据配置自动拉取新指标
- Web UI 可通过 `/api/telemetry/{deviceId}/latest` 查看

---

## 📝 注意事项

### Prometheus (DCGM) 指标
- 某些指标需要启用 DCGM Profiling 模式 (如 `DCGM_FI_PROF_*`)
- NVLink 指标仅在支持 NVLink 的 GPU 上有效
- ECC 指标仅在启用 ECC 的 GPU 上有效

### IPMI 传感器
- 传感器名称因主板厂商和型号而异
- 建议先运行 `ipmitool sensor list` 查看可用传感器
- 某些传感器可能返回 "na" 或 "disabled" 状态，会被自动过滤

### 性能考虑
- Prometheus 拉取周期: 2s (可调整 `@Scheduled(fixedRate=2000)`)
- IPMI 拉取周期: 30s (配置: `minitb.datasource.ipmi.pull-interval`)
- 数据存储: 内存 + 定期持久化到磁盘
- 时间序列数据会根据配置的保留策略自动清理

---

**总计**: 34 个监控指标 (17 GPU + 17 BMC)

**最后更新**: 2025-10-28


