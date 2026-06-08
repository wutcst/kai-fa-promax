# 掼蛋游戏后端服务

> Project Board: Phase 1 后端模块验收完成 | Status: ✅ Synced
> **项目提升目标**：完善异常路径测试覆盖、增加边界用例和测试执行说明、
> 补齐动作失败重跑机制，全面提升后端交付质量。

## 项目简介

掼蛋游戏后端服务是一个基于Spring Boot 3.x开发的在线掼蛋游戏服务器，提供用户认证、房间管理、游戏控制、WebSocket实时通信等核心功能。

### 技术栈

- **框架**: Spring Boot 3.2.0
- **数据库**: MySQL 8.0
- **缓存**: Redis 7
- **ORM**: MyBatis-Plus 3.5.5
- **认证**: JWT (jjwt 0.12.3)
- **文档**: Knife4j (Swagger)
- **工具**: Lombok, Hutool
- **构建工具**: Maven
- **Java版本**: JDK 17

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+（可选，用于Token缓存）

### 2. 配置数据库

1. 创建数据库：
```sql
CREATE DATABASE guandan_game DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行初始化脚本：
```bash
mysql -u root -p guandan_game < src/main/resources/database_design.sql
```

3. 修改配置文件 `src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guandan_game?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

### 3. 配置Redis（可选）

如果使用Redis缓存Token，修改配置文件：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
```

如果不使用Redis，Token验证会进入降级模式（允许通过）。

### 4. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/guandan-backend-1.0.0.jar

# 或使用Maven运行
mvn spring-boot:run
```

### 5. 访问服务

- **API地址**: http://localhost:8081/api
- **接口文档**: http://localhost:8081/doc.html
- **健康检查**: http://localhost:8081/actuator/health

---

## 项目结构

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/guandan/
│   │   │   ├── annotation/          # 自定义注解
│   │   │   ├── aspect/              # AOP切面
│   │   │   ├── common/              # 通用类（Result, ResponseCode）
│   │   │   ├── config/              # 配置类
│   │   │   ├── controller/          # 控制器层
│   │   │   ├── dto/                 # 数据传输对象
│   │   │   ├── entity/              # 实体类
│   │   │   ├── exception/           # 异常处理
│   │   │   ├── interceptor/         # 拦截器
│   │   │   ├── mapper/              # MyBatis Mapper
│   │   │   ├── schedule/            # 定时任务
│   │   │   ├── service/             # 服务层
│   │   │   ├── util/                # 工具类
│   │   │   ├── validation/         # 自定义校验器
│   │   │   ├── vo/                  # 视图对象
│   │   │   └── websocket/           # WebSocket处理
│   │   └── resources/
│   │       ├── application.yml       # 主配置文件
│   │       ├── application-dev.yml  # 开发环境配置
│   │       ├── database_design.sql  # 数据库设计脚本
│   │       └── mapper/              # MyBatis XML映射文件
│   └── test/                        # 测试代码
├── Dockerfile                       # Docker镜像构建文件
├── docker-compose.yml               # Docker Compose编排文件
├── deploy.sh                        # 部署脚本
├── rollback.sh                      # 回滚脚本
├── pom.xml                          # Maven依赖配置
└── README.md                        # 项目说明文档
```

---

## 核心功能

### 1. 用户认证模块
- 用户注册（自动生成6位数字账号）
- 用户登录（JWT Token认证）
- 用户退出登录
- Token刷新
- 修改密码
- 用户名唯一性校验

### 2. 房间管理模块
- 创建房间（生成6位随机房间号）
- 获取房间列表
- 加入房间（自动分配位置和队伍）
- 获取房间详情
- 退出房间
- 解散房间（仅房主）

### 3. 游戏控制模块
- 准备游戏（切换准备状态）
- 开始游戏（发牌、设置级牌）
- 获取游戏状态
- 出牌逻辑（WebSocket）
- 回合切换
- 游戏结束判定

### 4. WebSocket实时通信
- WebSocket连接管理
- 游戏开始消息推送
- 玩家出牌消息推送
- 回合更新消息推送
- 心跳检测
- 断线重连

### 5. 玩家信息模块
- 获取玩家统计信息
- 获取玩家战绩记录（分页、时间筛选）
- 修改密码

### 6. 快速匹配模块
- 加入匹配队列
- 取消匹配
- 查询匹配状态
- 自动匹配（每3秒执行一次）
- 匹配成功自动创建房间

### 7. 系统功能模块
- 操作日志记录（AOP切面）
- 定时任务调度
  - 清理过期操作日志
  - 清理过期匹配队列
  - 清理空闲房间
  - 更新玩家统计
  - 检查游戏超时

---

## API接口

### 基础URL
```
http://localhost:8081/api
```

### 接口列表

#### 用户认证
- `POST /api/user/register` - 用户注册
- `POST /api/user/login` - 用户登录
- `POST /api/user/logout` - 用户退出登录
- `POST /api/user/refresh-token` - 刷新Token
- `GET /api/user/current` - 获取当前用户信息
- `POST /api/user/change-password` - 修改密码

#### 房间管理
- `POST /api/room/create` - 创建房间
- `GET /api/room/list` - 获取房间列表
- `POST /api/room/join` - 加入房间
- `GET /api/room/info/{roomNo}` - 获取房间详情
- `POST /api/room/leave` - 退出房间
- `POST /api/room/dissolve` - 解散房间

#### 游戏控制
- `POST /api/game/ready` - 准备游戏
- `POST /api/game/start` - 开始游戏
- `GET /api/game/state/{roomId}` - 获取游戏状态

#### 玩家信息
- `GET /api/player/statistics` - 获取玩家统计信息
- `GET /api/player/records` - 获取玩家战绩记录

#### 快速匹配
- `POST /api/match/join` - 加入匹配队列
- `POST /api/match/cancel` - 取消匹配
- `POST /api/match/status` - 查询匹配状态


---

## 认证方式

除登录注册接口外，所有接口需要在请求头中携带Token：

```
Authorization: Bearer {token}
```

Token通过登录接口获取，有效期为24小时。

---

## 数据库设计

### 核心表结构

1. **tb_user** - 用户表
2. **tb_game_room** - 游戏房间表
3. **tb_room_player** - 房间玩家关系表
4. **tb_game_record** - 游戏记录表
5. **tb_player_game** - 玩家游戏记录表
6. **tb_card_play** - 出牌记录表
7. **tb_operation_log** - 操作日志表
8. **tb_match_queue** - 快速匹配队列表
9. **tb_websocket_session** - WebSocket会话表


---

## WebSocket

### 连接地址
```
ws://localhost:8081/ws/game/{playerId}
```

### 消息类型

#### 客户端发送
- `JOIN_ROOM` - 加入房间
- `PLAY_CARD` - 出牌
- `HEARTBEAT` - 心跳
- `RECONNECT` - 重连请求

#### 服务器发送
- `GAME_START` - 游戏开始
- `PLAYER_ACTION` - 玩家行动
- `TURN_CHANGE` - 回合更新
- `RECONNECT_SUCCESS` - 重连成功
- `ERROR` - 错误消息


---

## Docker部署

### 使用Docker Compose

```bash
# 1. 配置环境变量
cp env.example .env
vim .env

# 2. 编译打包
mvn clean package -DskipTests

# 3. 启动服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f backend
```

### 使用部署脚本

```bash
# 部署
chmod +x deploy.sh
./deploy.sh prod 1.0.0

# 回滚
chmod +x rollback.sh
./rollback.sh backups/20260116_120000
```

详细部署说明请参考：`部署文档.md`

---

## 测试

### 单元测试
```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=UserServiceUnitTest
```

### 集成测试
```bash
# 运行集成测试
mvn test -Dtest=UserControllerTest
```

### 接口测试
使用Postman导入 `postman_collection.json` 进行接口测试。

---

## 监控和日志

### 健康检查
```bash
curl http://localhost:8081/actuator/health
```

### 日志位置
- **应用日志**: `logs/guandan-backend.log`
- **日志级别**: 开发环境DEBUG，生产环境INFO

### 日志配置
日志配置在 `application.yml` 中：
- 控制台输出
- 文件输出（自动滚动，最大10MB，保留30天）

---

## 配置说明

### 主要配置项

#### 服务器配置
```yaml
server:
  port: 8081
  servlet:
    context-path: /api
```

#### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guandan_game
    username: root
    password: your_password
    hikari:
      maximum-pool-size: 20
```

#### Redis配置
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_password
```

#### JWT配置
```yaml
jwt:
  secret: your-secret-key
  expiration: 86400000  # 24小时
```

---

## 开发指南

### 代码规范
- 使用Lombok简化代码
- 统一使用Result包装响应
- 使用@LogRecord记录操作日志
- 使用@Valid进行参数校验

### 新增接口步骤
1. 创建DTO类（请求/响应）
2. 在Service层实现业务逻辑
3. 在Controller层添加接口
4. 添加@LogRecord注解（如需要）
5. 更新接口文档

### 数据库操作
- 使用MyBatis-Plus进行CRUD操作
- 复杂查询使用XML映射文件
- 使用@Transactional保证事务

---

## 常见问题

### 1. 端口被占用
```bash
# Windows
netstat -ano | findstr :8081
taskkill /PID <PID> /F

# Linux
lsof -i :8081
kill -9 <PID>
```

### 2. 数据库连接失败
- 检查MySQL服务是否启动
- 检查数据库用户名密码
- 检查数据库是否存在

### 3. Redis连接失败
- Redis连接失败不影响基本功能
- Token验证会进入降级模式
- 建议启动Redis以获得完整功能

### 4. Token验证失败
- 检查Token是否过期
- 检查请求头格式：`Authorization: Bearer {token}`
- 检查JWT密钥配置

---

## 更新日志

### v1.0.0 (2026-01-16)
- 完成用户认证模块
- 完成房间管理模块
- 完成游戏控制模块
- 完成WebSocket实时通信
- 完成玩家信息模块
- 完成快速匹配模块
- 完成操作日志记录
- 完成定时任务调度
- 完成部署脚本编写

---
