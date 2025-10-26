# MiniTB HTTP API 文档

MiniTB 提供了基于 Javalin 的轻量级 HTTP API，用于设备管理和查询。

## 服务地址

- **端口**: 8080
- **Base URL**: `http://localhost:8080/api`

## API 列表

### 1. 健康检查

检查服务是否正常运行。

```bash
GET /api/health
```

**响应示例：**
```json
{
  "status": "ok",
  "timestamp": 1729926000000
}
```

---

### 2. 获取所有设备

获取系统中所有注册的设备。

```bash
GET /api/devices
```

**响应示例：**
```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "温度传感器-01",
      "type": "sensor",
      "accessToken": "test-token-001",
      "deviceProfileId": "660e8400-e29b-41d4-a716-446655440001",
      "createdTime": 1729926000000
    }
  ]
}
```

---

### 3. 获取单个设备

根据设备ID获取设备详情。

```bash
GET /api/devices/{id}
```

**路径参数：**
- `id`: 设备UUID

**响应示例：**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "温度传感器-01",
    "type": "sensor",
    "accessToken": "test-token-001",
    "deviceProfileId": "660e8400-e29b-41d4-a716-446655440001",
    "createdTime": 1729926000000
  }
}
```

**错误响应：**
```json
{
  "success": false,
  "message": "设备不存在: ...",
  "data": null
}
```

---

### 4. 创建设备

创建新的设备。

```bash
POST /api/devices
Content-Type: application/x-www-form-urlencoded
```

**表单参数：**
- `name` (必填): 设备名称
- `type` (必填): 设备类型
- `accessToken` (可选): 访问令牌
- `deviceProfileId` (可选): 设备配置ID

**示例：**
```bash
curl -X POST http://localhost:8080/api/devices \
  -d "name=温度传感器-02" \
  -d "type=sensor" \
  -d "accessToken=test-token-002"
```

**响应示例：**
```json
{
  "success": true,
  "message": "设备创建成功",
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "name": "温度传感器-02",
    "type": "sensor",
    "accessToken": "test-token-002",
    "deviceProfileId": null,
    "createdTime": 1729926100000
  }
}
```

---

### 5. 更新设备

更新现有设备的信息。

```bash
PUT /api/devices/{id}
Content-Type: application/x-www-form-urlencoded
```

**路径参数：**
- `id`: 设备UUID

**表单参数（都是可选的）：**
- `name`: 设备名称
- `type`: 设备类型
- `accessToken`: 访问令牌
- `deviceProfileId`: 设备配置ID

**示例：**
```bash
curl -X PUT http://localhost:8080/api/devices/770e8400-e29b-41d4-a716-446655440002 \
  -d "name=温度传感器-02-Updated"
```

**响应示例：**
```json
{
  "success": true,
  "message": "设备更新成功",
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "name": "温度传感器-02-Updated",
    "type": "sensor",
    "accessToken": "test-token-002",
    "deviceProfileId": null,
    "createdTime": 1729926100000
  }
}
```

---

### 6. 删除设备

删除指定的设备。

```bash
DELETE /api/devices/{id}
```

**路径参数：**
- `id`: 设备UUID

**示例：**
```bash
curl -X DELETE http://localhost:8080/api/devices/770e8400-e29b-41d4-a716-446655440002
```

**响应示例：**
```json
{
  "success": true,
  "message": "设备删除成功",
  "data": null
}
```

---

## 错误处理

所有API都使用统一的错误响应格式：

```json
{
  "success": false,
  "message": "错误描述信息",
  "data": null
}
```

**常见HTTP状态码：**
- `200 OK`: 请求成功
- `201 Created`: 资源创建成功
- `400 Bad Request`: 请求参数错误
- `404 Not Found`: 资源不存在
- `500 Internal Server Error`: 服务器内部错误

---

## 测试

运行测试脚本：

```bash
./test-http-api.sh
```

或手动测试：

```bash
# 健康检查
curl http://localhost:8080/api/health

# 获取所有设备
curl http://localhost:8080/api/devices

# 创建设备
curl -X POST http://localhost:8080/api/devices \
  -d "name=测试设备" \
  -d "type=sensor" \
  -d "accessToken=test-token"
```

---

## 技术栈

- **Web框架**: Javalin 5.6.3（轻量级，不依赖Spring）
- **JSON序列化**: Jackson 2.16.1
- **服务端口**: 8080

---

## 未来扩展

计划添加的API功能：

- [ ] 设备配置管理 (`/api/device-profiles`)
- [ ] 遥测数据查询 (`/api/telemetry`)
- [ ] 规则链管理 (`/api/rule-chains`)
- [ ] 实体关系管理 (`/api/relations`)
- [ ] WebSocket支持（实时数据推送）

