# 掼蛋游戏后端 API 文档

> 版本: v1.1.0 | 更新日期: 2026-05-17

## 概述

本文档描述掼蛋游戏后端服务提供的 RESTful API 接口。基础 URL：`http://localhost:8081/api`

### 认证方式

除登录、注册、WebSocket 连接外，所有接口需要在请求头中携带 JWT Token：

```
Authorization: Bearer {token}
```

Token 通过登录接口获取，默认有效期 24 小时。

### 响应格式

所有接口统一返回 JSON 格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

#### 错误码说明

| code | 说明 |
|------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如用户名已存在） |
| 500 | 服务器内部错误 |

### 健康检查

```
GET /actuator/health
```

返回示例：
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

健康检查端点无需认证，可用于监控和负载均衡的探活检测。

---

## 1. 用户认证

### 1.1 用户注册

```
POST /api/user/register
```

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nickname | String | 是 | 用户昵称，最长 10 字符 |
| password | String | 是 | 登录密码，6-20 字符 |

**响应数据：**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户ID |
| username | String | 系统分配的 6 位数字账号 |
| nickname | String | 用户昵称 |

**异常场景：**
- 400: 昵称为空或密码格式不正确
- 409: 用户名已存在（极少出现，系统自动分配唯一账号）

### 1.2 用户登录

```
POST /api/user/login
```

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 6 位数字账号 |
| password | String | 是 | 登录密码 |

**响应数据：**

| 字段 | 类型 | 说明 |
|------|------|------|
| token | String | JWT Token |
| userId | Long | 用户ID |
| username | String | 用户账号 |
| nickname | String | 用户昵称 |

**异常场景：**
- 400: 账号或密码为空
- 401: 账号不存在或密码错误

### 1.3 退出登录

```
POST /api/user/logout
```

**请求头：** `Authorization: Bearer {token}`

### 1.4 获取当前用户信息

```
GET /api/user/current
```

**请求头：** `Authorization: Bearer {token}`

**响应数据：**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户ID |
| username | String | 用户账号 |
| nickname | String | 用户昵称 |
| avatar | String | 头像URL |
| online | Boolean | 在线状态 |

### 1.5 刷新 Token

```
POST /api/user/refresh-token
```

**请求头：** `Authorization: Bearer {token}`

### 1.6 修改密码

```
POST /api/user/change-password
```

**请求头：** `Authorization: Bearer {token}`

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | String | 是 | 旧密码 |
| newPassword | String | 是 | 新密码，6-20 字符 |

---

## 2. 房间管理

### 2.1 创建房间

```
POST /api/room/create
```

**请求头：** `Authorization: Bearer {token}`

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| roomName | String | 否 | 房间名称（选填） |

### 2.2 获取房间列表

```
GET /api/room/list
```

**请求头：** `Authorization: Bearer {token}`

**查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码（默认 1） |
| size | Integer | 否 | 每页数量（默认 20） |

### 2.3 加入房间

```
POST /api/room/join
```

**请求头：** `Authorization: Bearer {token}`

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| roomNo | String | 是 | 6 位房间号 |

### 2.4 获取房间详情

```
GET /api/room/info/{roomNo}
```

**请求头：** `Authorization: Bearer {token}`

### 2.5 退出房间

```
POST /api/room/leave
```

**请求头：** `Authorization: Bearer {token}`

### 2.6 解散房间

```
POST /api/room/dissolve
```

**请求头：** `Authorization: Bearer {token}`

**说明：** 仅房主可解散房间。

---

## 3. 游戏控制

### 3.1 准备游戏

```
POST /api/game/ready
```

**请求头：** `Authorization: Bearer {token}`

### 3.2 开始游戏

```
POST /api/game/start
```

**请求头：** `Authorization: Bearer {token}`

**说明：** 房间内所有玩家准备完成后，房主调用此接口开始游戏。

### 3.3 获取游戏状态

```
GET /api/game/state/{roomId}
```

**请求头：** `Authorization: Bearer {token}`

---

## 4. 玩家信息

### 4.1 获取玩家统计信息

```
GET /api/player/statistics
```

**请求头：** `Authorization: Bearer {token}`

### 4.2 获取玩家战绩记录

```
GET /api/player/records
```

**请求头：** `Authorization: Bearer {token}`

**查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码（默认 1） |
| size | Integer | 否 | 每页数量（默认 10） |
| startDate | String | 否 | 开始日期（yyyy-MM-dd） |
| endDate | String | 否 | 结束日期（yyyy-MM-dd） |

---

## 5. 快速匹配

### 5.1 加入匹配队列

```
POST /api/match/join
```

**请求头：** `Authorization: Bearer {token}`

### 5.2 取消匹配

```
POST /api/match/cancel
```

**请求头：** `Authorization: Bearer {token}`

### 5.3 查询匹配状态

```
POST /api/match/status
```

**请求头：** `Authorization: Bearer {token}`

---

## 6. WebSocket

### 6.1 连接地址

```
ws://localhost:8081/ws/game/{playerId}
```

### 6.2 消息类型

#### 客户端发送

| 消息类型 | 说明 |
|----------|------|
| JOIN_ROOM | 加入房间 |
| PLAY_CARD | 出牌 |
| HEARTBEAT | 心跳（每 30 秒） |
| RECONNECT | 重连请求 |

#### 服务器发送

| 消息类型 | 说明 |
|----------|------|
| GAME_START | 游戏开始 |
| PLAYER_ACTION | 玩家行动 |
| TURN_CHANGE | 回合更新 |
| RECONNECT_SUCCESS | 重连成功 |
| ERROR | 错误消息 |

---

## 更新记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.1.0 | 2026-05-17 | 文档与阶段提升版本对齐，补充健康检查说明 |
| v1.0.0 | 2026-01-16 | 初始 API 文档 |
