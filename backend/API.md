# 掼蛋游戏后端 API 接口文档

## 目录

- [认证说明](#认证说明)
- [通用响应格式](#通用响应格式)
- [认证模块](#认证模块)
- [房间管理模块](#房间管理模块)
- [游戏控制模块](#游戏控制模块)
- [快速匹配模块](#快速匹配模块)
- [玩家信息模块](#玩家信息模块)
- [健康检查模块](#健康检查模块)
- [兼容接口模块](#兼容接口模块)
- [Redis 测试模块](#redis-测试模块)

---

## 认证说明

### Token 获取

通过登录接口获取 Token：

```bash
POST /api/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1
  }
}
```

### 使用 Token

需要认证的接口需要在请求头中携带 Token：

```http
Authorization: Bearer {token}
```

示例：
```bash
curl -X GET http://localhost:8080/api/user/info \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

---

## 通用响应格式

### 成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 错误响应
```json
{
  "code": 500,
  "message": "错误描述"
}
```

---

## 认证模块

### 1. 用户注册

**接口地址：** `POST /api/register`

**是否需要认证：** 否

**请求参数：**
```json
{
  "username": "testuser",
  "password": "password123",
  "nickname": "测试用户"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "testuser",
    "nickname": "测试用户"
  }
}
```

---

### 2. 用户登录

**接口地址：** `POST /api/login`

**是否需要认证：** 否

**请求参数：**
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "username": "testuser"
  }
}
```

---

### 3. 获取用户信息

**接口地址：** `GET /api/user/info`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "testuser",
    "nickname": "测试用户",
    "avatar": null,
    "phone": null
  }
}
```

---

### 4. 退出登录

**接口地址：** `POST /api/user/logout`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": "退出登录成功"
}
```

---

### 5. 修改密码

**接口地址：** `POST /api/user/change-password`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```json
{
  "oldPassword": "oldpass123",
  "newPassword": "newpass123"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": "密码修改成功，请重新登录"
}
```

---

### 6. 检查用户名是否存在

**接口地址：** `GET /api/user/check-username`

**是否需要认证：** 否

**请求参数：**
```
username=testuser
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

---

## 房间管理模块

### 1. 创建新房间

**接口地址：** `POST /api/new-game`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```json
{
  "isPrivate": false,
  "config": "{}"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "roomNo": "123456",
    "message": "房间创建成功"
  }
}
```

---

### 2. 获取可用房间列表

**接口地址：** `GET /api/rooms`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "roomNo": "123456",
      "status": 0,
      "creatorId": 1,
      "levelTeamA": 2,
      "levelTeamB": 2,
      "createTime": "2024-01-01T10:00:00"
    }
  ]
}
```

**房间状态说明：**
- `0`: 等待中
- `1`: 游戏中
- `2`: 已结束

---

### 3. 获取用户当前所在房间

**接口地址：** `GET /api/room/current`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "roomNo": "123456",
    "status": 0,
    "creatorId": 1,
    "levelTeamA": 2,
    "levelTeamB": 2
  }
}
```

**注意：** 如果用户不在任何房间，`data` 字段为 `null`

---

## 游戏控制模块

### 1. 加入房间

**接口地址：** `POST /api/room/join`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```json
{
  "roomNo": "123456"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "seatIndex": 1,
    "roomNo": "123456",
    "message": "加入房间成功"
  }
}
```

---

### 2. 退出房间

**接口地址：** `POST /api/room/exit`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```json
{
  "roomId": "room_123456"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "退出房间成功"
  }
}
```

---

### 3. 获取房间详情

**接口地址：** `GET /api/room/{roomNo}/detail`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**路径参数：**
- `roomNo`: 房间号

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "roomNo": "123456",
    "status": "WAITING",
    "creatorId": 1,
    "levelTeamA": 2,
    "levelTeamB": 2,
    "currentPlayerIndex": 0,
    "playerCount": 2
  }
}
```

---

### 4. 解散房间

**接口地址：** `POST /api/room/{roomNo}/dissolve`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**路径参数：**
- `roomNo`: 房间号

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "房间已解散"
  }
}
```

**注意：** 只有房主才能解散房间

---

### 5. 准备就绪

**接口地址：** `POST /api/game/ready`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```json
{
  "roomId": "room_123456"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "准备就绪"
  }
}
```

---

### 6. 开始游戏

**接口地址：** `POST /api/game/start`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```json
{
  "roomId": "room_123456"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "游戏开始"
  }
}
```

---

### 7. 获取游戏状态

**接口地址：** `GET /api/game/{roomId}/state`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**路径参数：**
- `roomId`: 房间ID

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "roomId": "room_123456",
    "status": "PLAYING",
    "currentPlayerIndex": 1,
    "playerCount": 4,
    "myCards": [1, 2, 3, 4, 5]
  }
}
```

**游戏状态说明：**
- `WAITING`: 等待中
- `PLAYING`: 游戏中
- `FINISHED`: 已结束

---

## 快速匹配模块

### 1. 加入匹配队列

**接口地址：** `POST /match/join`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

---

### 2. 取消匹配

**接口地址：** `POST /match/cancel`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

---

### 3. 查询匹配状态

**接口地址：** `POST /match/status`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

**返回值说明：**
- `true`: 正在匹配中
- `false`: 未在匹配队列中

---

## 玩家信息模块

### 1. 获取玩家统计信息

**接口地址：** `GET /player/statistics`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalGames": 100,
    "winGames": 60,
    "winRate": 60.0,
    "levelCurrent": 5
  }
}
```

---

### 2. 获取玩家战绩记录

**接口地址：** `GET /player/records`

**是否需要认证：** 是

**请求头：**
```http
Authorization: Bearer {token}
```

**请求参数：**
```
page=1
pageSize=20
startTime=2024-01-01 00:00:00
endTime=2024-12-31 23:59:59
```

**参数说明：**
- `page`: 页码，从 1 开始，默认 1
- `pageSize`: 每页大小，默认 20，最多 100
- `startTime`: 开始时间（可选），格式：`yyyy-MM-dd HH:mm:ss`
- `endTime`: 结束时间（可选），格式：`yyyy-MM-dd HH:mm:ss`

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1,
        "opponentNames": "玩家A,玩家B,玩家C",
        "result": 1,
        "score": 100,
        "gameTime": "2024-01-01T10:00:00",
        "duration": null
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

**result 字段说明：**
- `1`: 胜利
- `0`: 失败

---

## 健康检查模块

### 1. 测试数据库连接

**接口地址：** `GET /health/database`

**是否需要认证：** 否

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "database": "MySQL",
    "version": "8.0.33",
    "status": "connected",
    "currentDatabase": "guandan",
    "tableCount": 8
  }
}
```

---

### 2. 测试 Redis 连接

**接口地址：** `GET /health/redis`

**是否需要认证：** 否

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "connected",
    "testResult": "pong",
    "ping": "PONG"
  }
}
```

---

### 3. 获取系统信息

**接口地址：** `GET /health/system`

**是否需要认证：** 否

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "jvm": {
      "maxMemory": "2048 MB",
      "totalMemory": "512 MB",
      "freeMemory": "256 MB",
      "usedMemory": "256 MB",
      "availableProcessors": 8
    },
    "system": {
      "osName": "Windows 10",
      "osVersion": "10.0",
      "javaVersion": "17.0.1",
      "javaHome": "C:\\Program Files\\Java\\jdk-17"
    }
  }
}
```

---

## 兼容接口模块

### 1. 兼容登录接口

**接口地址：** `POST /login`

**是否需要认证：** 否

**请求参数：**
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**响应示例：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1
}
```

---

### 2. 兼容注册接口

**接口地址：** `POST /register`

**是否需要认证：** 否

**请求参数：**
```json
{
  "username": "testuser",
  "password": "password123",
  "confirmation": "password123"
}
```

**响应示例：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1
}
```

---

### 3. 兼容创建游戏接口

**接口地址：** `POST /new_game`

**是否需要认证：** 否

**请求参数：**
```json
{
  "level": 2,
  "experimental": false
}
```

**响应示例：**
```json
{
  "token": "123456"
}
```

**说明：** 返回的 `token` 实际上是房间号（roomNo）

---

### 4. 兼容加入游戏接口

**接口地址：** `POST /join_game/{token}`

**是否需要认证：** 否

**路径参数：**
- `token`: 房间号

**请求参数：**
```json
{
  "username": "testuser"
}
```

**响应示例：**
```json
{
  "player_number": 1
}
```

---

### 5. 游戏状态查询（HTTP 轮询）

**接口地址：** `GET /get_player_game_state/{token}/{player_id}`

**是否需要认证：** 否

**路径参数：**
- `token`: 房间号
- `player_id`: 玩家ID

**响应示例：**
```json
{
  "room_status": "PLAYING",
  "current_player_index": 1,
  "my_cards": [1, 2, 3, 4, 5],
  "other_players_cards": {
    "2": 10,
    "3": 10,
    "4": 10
  }
}
```

---

## Redis 测试模块

### 1. 测试 String 操作

**接口地址：** `POST /redis/test/string`

**请求参数：**
```json
{
  "key": "test",
  "value": "hello"
}
```

---

### 2. 测试 List 操作

**接口地址：** `POST /redis/test/list`

**请求参数：**
```json
{
  "key": "testList",
  "values": ["a", "b", "c"]
}
```

---

### 3. 测试 Set 操作

**接口地址：** `POST /redis/test/set`

**请求参数：**
```json
{
  "key": "testSet",
  "values": ["a", "b", "c"]
}
```

---

### 4. 测试 Hash 操作

**接口地址：** `POST /redis/test/hash`

**请求参数：**
```json
{
  "key": "testHash",
  "field": "name",
  "value": "test"
}
```

---

### 5. 测试 ZSet 操作

**接口地址：** `POST /redis/test/zset`

**请求参数：**
```json
{
  "key": "testZSet",
  "value": "member1",
  "score": 1.0
}
```

---

### 6. 测试发布订阅

**接口地址：** `POST /redis/test/pubsub`

**请求参数：**
```json
{
  "channel": "testChannel",
  "message": "hello"
}
```

---

### 7. 测试分布式锁

**接口地址：** `POST /redis/test/lock`

**请求参数：**
```json
{
  "key": "testLock",
  "timeout": 10
}
```

---

### 8. 清理测试数据

**接口地址：** `POST /redis/test/clear`

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": "测试数据已清理"
}
```

---

## WebSocket 接口

### 连接地址

```
ws://localhost:8080/ws/game/{roomId}/{userId}
```

**路径参数：**
- `roomId`: 房间ID
- `userId`: 用户ID

### 消息格式

#### 客户端发送消息

```json
{
  "type": "action",
  "data": { ... }
}
```

**消息类型：**
- `action`: 玩家操作（出牌、进贡等）
- `ready`: 准备就绪
- `leave`: 离开房间

#### 服务端推送消息

```json
{
  "type": "state_update",
  "data": { ... }
}
```

**消息类型：**
- `state_update`: 游戏状态更新
- `player_join`: 玩家加入
- `player_leave`: 玩家离开
- `game_start`: 游戏开始
- `game_end`: 游戏结束

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 500 | 服务器内部错误 |
| 401 | 未授权（Token 无效或过期） |
| 403 | 禁止访问（权限不足） |
| 404 | 资源不存在 |

---

## 更新日志

- **2024-01-17**: 初始版本，整理所有 API 接口
