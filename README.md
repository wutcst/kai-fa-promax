# 掼蛋游戏平台

> 武汉理工大学 软件工程实践课程项目
> **版本**: v1.1.0 | **状态**: 开发中 | **里程碑**: Phase 4

[![Java CI](https://img.shields.io/badge/Java%20CI-Passing-brightgreen)](https://github.com/xiyu1296/kai-fa-promax/actions)
[![Vue CI](https://img.shields.io/badge/Vue%20CI-Passing-brightgreen)](https://github.com/xiyu1296/kai-fa-promax/actions)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)](https://www.docker.com/)

---

## 项目架构图

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Vue 3 + Vite)                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ 登录/注册 │ │  游戏大厅 │ │  个人中心 │ │  游戏桌   │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘  │
│       │            │            │            │         │
│  ┌────┴────────────┴────────────┴────────────┴────┐    │
│  │              Axios / WebSocket                  │    │
│  └────────────────────┬───────────────────────────┘    │
└───────────────────────┼───────────────────────────────┘
                        │
┌───────────────────────┼───────────────────────────────┐
│                 Nginx 反向代理                         │
│           ┌───────────┴───────────┐                   │
│           │  HTTPS + HTTP/2       │                   │
│           │  限流 30r/s + 安全头   │                   │
│           └───────────┬───────────┘                   │
└───────────────────────┼───────────────────────────────┘
                        │
┌───────────────────────┼───────────────────────────────┐
│                  后端 (Spring Boot 3.1.5)              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ 认证模块  │ │ 房间管理  │ │ 游戏引擎  │ │ 匹配服务  │  │
│  │ JWT/Token │ │ CRUD操作 │ │ 出牌逻辑 │ │  队列    │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘  │
│       │            │            │            │         │
│  ┌────┴────────────┴────────────┴────────────┴────┐    │
│  │             MyBatis Plus / WebSocket             │    │
│  └────────────────────┬───────────────────────────┘    │
└───────────────────────┼───────────────────────────────┘
                        │
┌───────────────────────┼───────────────────────────────┐
│        ┌──────────────┴──────────────┐                 │
│        │           MySQL             │                 │
│        │  用户表 / 房间表 / 游戏记录   │                 │
│        └─────────────────────────────┘                 │
│        ┌─────────────────────────────┐                 │
│        │           Redis             │                 │
│        │  Token 缓存 / 匹配队列       │                 │
│        └─────────────────────────────┘                 │
└───────────────────────────────────────────────────────┘
```

## CI/CD 状态

| 流水线 | 状态 | 说明 |
|--------|------|------|
| 后端构建 | [![Backend Build](https://img.shields.io/badge/Backend-Passing-brightgreen)](https://github.com/xiyu1296/kai-fa-promax/actions) | Maven 编译 + 单元测试 |
| 前端构建 | [![Frontend Build](https://img.shields.io/badge/Frontend-Passing-brightgreen)](https://github.com/xiyu1296/kai-fa-promax/actions) | Vite 构建 + Lint 检查 |
| Docker 镜像 | [![Docker Image](https://img.shields.io/badge/Docker-OK-blue)](https://github.com/xiyu1296/kai-fa-promax/actions) | 多阶段构建 + 安全扫描 |
| 自动发布 | [![Release](https://img.shields.io/badge/Release-Automated-orange)](https://github.com/xiyu1296/kai-fa-promax/releases) | GitHub Actions 自动发布 |

## 功能总览

### 用户认证模块
- 用户注册（昵称 + 密码），系统自动分配 6 位账号
- 用户登录（账号 + 密码），返回 JWT Token
- Token 刷新机制，支持自动续期
- 密码重置流程（邮箱验证码 + 限时 Token）
- 密码 BCrypt 加密存储

### 房间管理模块
- 创建房间（自动生成 6 位房间号）
- 加入 / 退出房间
- 房主解散房间 / 踢出玩家
- 转移房主权限
- 房间人数限制（2-4 人）

### 游戏对战模块
- 玩家准备 / 取消准备
- 房主开始游戏（需全员准备）
- 回合制出牌（WebSocket 实时同步）
- 游戏状态查询
- 游戏回放记录存储与分段查询

### 匹配服务
- 快速匹配（满 4 人自动创建房间）
- 取消匹配
- 匹配结果轮询（最大 60 秒超时）
- 断线重连后需重新加入队列

### 个人中心
- 个人资料展示
- 战绩详情（RecordDetail 组件）
- 胜率统计与筛选
- 分页浏览对战记录

## 技术栈

### 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.1.5 | 应用框架 |
| MyBatis Plus | 3.5.4 | ORM 框架 |
| MySQL | 8.0 | 数据库 |
| Redis | 7.0 | 缓存与队列 |
| WebSocket | - | 实时通信 |
| Docker | 24.0 | 容器化部署 |
| Nginx | 1.24 | 反向代理 |

### 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.4 | 前端框架 |
| Vite | 5.0 | 构建工具 |
| Element Plus | 2.5 | UI 组件库 |
| Axios | 1.6 | HTTP 客户端 |
| Pinia | 2.1 | 状态管理 |

## 快速开始向导

### 环境要求
- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+（可选，影响 Token 缓存）

### 1. 克隆项目
```bash
git clone https://github.com/xiyu1296/kai-fa-promax.git
cd kai-fa-promax
```

### 2. 初始化数据库
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS guandan_game DEFAULT CHARACTER SET utf8mb4;"

# 导入表结构
mysql -u root -p guandan_game < Login+Battle/backend/sql/schema.sql
```

### 3. 启动后端
```bash
cd Login+Battle/backend
mvn clean install -DskipTests
mvn spring-boot:run
```
后端默认启动在 `http://localhost:8080`

### 4. 启动前端
```bash
cd Login+Battle/frontend
npm install
npm run dev
```
前端默认启动在 `http://localhost:5173`

### 5. Docker 部署（可选）
```bash
docker compose up -d
```

### 6. 访问应用
打开浏览器访问 `http://localhost:5173`

## 项目结构

```
Login+Battle/
├── backend/                  # 后端项目
│   ├── src/main/java/        # Java 源代码
│   │   └── com/guandan/
│   │       ├── config/       # 配置类
│   │       ├── controller/   # 接口控制器
│   │       ├── service/      # 业务逻辑
│   │       ├── mapper/       # 数据访问
│   │       ├── entity/       # 实体类
│   │       ├── dto/          # 数据传输对象
│   │       └── util/         # 工具类
│   ├── src/main/resources/   # 配置文件
│   ├── Dockerfile            # 后端 Dockerfile
│   └── pom.xml               # Maven 配置
├── frontend/                 # 前端项目
│   ├── src/
│   │   ├── api/              # API 接口
│   │   ├── assets/           # 静态资源
│   │   ├── components/       # 公共组件
│   │   ├── router/           # 路由配置
│   │   ├── stores/           # 状态管理
│   │   ├── utils/            # 工具函数
│   │   └── views/            # 页面组件
│   ├── dist/                 # 构建输出
│   ├── Dockerfile            # 前端 Dockerfile
│   └── package.json          # NPM 配置
├── docker-compose.yml        # Docker Compose 配置
├── nginx/                    # Nginx 配置
├── 运行指南.md               # 详细运行指南
└── .gitignore                # Git 忽略配置
```

## 团队分工

| 成员 | 角色 | 主要负责 |
|------|------|----------|
| 杨丝婳 | 项目负责人 / 全栈开发 | 系统架构、后端核心开发、文档管理 |
| xiyu1296 | 全栈开发 | 前端开发、测试、部署运维 |

## 版本历史

| 版本 | 日期 | 主要内容 |
|------|------|----------|
| v1.0.0 | 2026-05-20 | 基础功能：用户认证、房间管理、游戏对战、WebSocket 通信 |
| v1.1.0 | 2026-06-07 | 阶段提升：Docker 部署、CI/CD、安全加固、文档完善 |
| v1.2.0 | 2026-06-12 | 增强特性：密码重置、游戏回放、Docker 多阶段构建、Nginx 安全配置 |

## 许可证

本项目仅供教育学习使用。
