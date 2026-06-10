# 🃏 掼蛋 (Guandan)

> 在线掼蛋卡牌对战游戏 · Spring Boot + Vue 3 全栈项目

[![CI Status](https://img.shields.io/badge/CI-passing-brightgreen)](https://github.com/wutcst/kai-fa-promax/actions)
[![Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/wutcst/kai-fa-promax/releases/tag/v1.0.0)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6db33f)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.x-42b883)](https://vuejs.org/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

---

## 📋 项目简介

掼蛋（Guandan）是一种流行于中国的四人扑克牌游戏。本项目实现了完整的在线对战功能，包括用户系统、房间匹配、牌型规则判定、WebSocket 实时通信以及 AI 辅助等模块。

---

## ✨ 功能特性

| 模块 | 功能 |
|------|------|
| 🔐 **账号体系** | 注册登录、JWT 鉴权、Token 拦截 |
| 🏠 **房间系统** | 创建/加入房间、座位管理、准备状态 |
| 🔍 **快速匹配** | 匹配队列、取消匹配、结果轮询 |
| 🎮 **对战核心** | 发牌、出牌/过牌、牌型识别与比较 |
| 📡 **实时通信** | WebSocket 连接管理、广播同步、断线清理 |
| 🤖 **AI 辅助** | 出牌建议、规则问答 |
| 📊 **个人中心** | 胜率统计、战绩分页、对局详情 |
| 🐳 **部署发布** | Docker 容器化、Nginx 反向代理、CI/CD 流水线 |

---

## 🏗️ 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **后端框架** | Spring Boot 3.2 | RESTful API + WebSocket |
| **语言** | Java 17 | LTS 版本 |
| **安全** | JWT | JJWT 0.12.3 |
| **构建** | Maven | 多模块管理 |
| **前端框架** | Vue 3 + Vite | Composition API |
| **前端组件** | Element Plus | UI 组件库 |
| **实时通信** | WebSocket | JSR 356 标准 |
| **容器化** | Docker + Nginx | 多阶段构建、反向代理 |
| **CI/CD** | GitHub Actions | 5 条流水线 |

---

## 🚀 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Node.js 20+
- Docker（可选）

### 后端启动

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

### Docker 部署

```bash
docker-compose up -d
```

---

## 📁 项目结构

```
kai-fa-promax/
├── backend/                     # 后端 Spring Boot
│   ├── src/main/java/com/guandan/
│   │   ├── config/              # 配置（WebConfig, ScheduleConfig）
│   │   ├── controller/          # REST 控制器 + AI 助手
│   │   ├── dto/                 # 数据传输对象
│   │   ├── entity/              # 数据实体（User, Room, GameRecord）
│   │   ├── filter/              # 安全过滤器
│   │   ├── game/                # 游戏核心
│   │   │   ├── model/           # 游戏模型（GameRoom）
│   │   │   ├── service/         # 游戏逻辑（GameLogic, AI）
│   │   │   ├── util/            # 牌型工具（CardUtils）
│   │   │   └── websocket/       # WebSocket 服务器
│   │   ├── interceptor/         # Token 拦截器
│   │   ├── mapper/              # MyBatis 映射器
│   │   ├── model/               # 枚举模型（CardType）
│   │   ├── service/             # 业务服务层
│   │   └── util/                # 工具类（JWT, 密码, 用户上下文）
│   ├── src/test/                # 单元测试
│   ├── src/main/resources/      # 配置与 SQL
│   ├── docs/                    # 文档
│   ├── nginx/                   # Nginx 配置
│   └── Dockerfile               # 后端 Docker 镜像
├── frontend/                    # 前端 Vue 3
│   └── src/
│       ├── api/                 # API 请求封装
│       ├── assets/              # 静态资源
│       ├── components/          # 公共组件（AIAssistant, RecordDetail）
│       ├── router/              # 路由守卫
│       ├── utils/               # 工具函数
│       └── views/               # 页面（Login, Lobby, BattleView, PersonalHome）
├── docker-compose.yml           # 容器编排
├── .github/workflows/           # CI/CD 流水线
│   ├── backend-ci.yml
│   ├── frontend-ci.yml
│   ├── websocket-check.yml
│   ├── docker-build.yml
│   └── release.yml
├── CHANGELOG.md                 # 变更日志
├── CONTRIBUTING.md              # 贡献指南
└── README.md
```

---

## 🔄 CI/CD 流水线

| 流水线 | 触发条件 | 说明 |
|--------|---------|------|
| `backend-ci` | PR / Push → master | Maven 编译检查 |
| `frontend-ci` | PR / Push → master | npm 构建检查 |
| `websocket-check` | PR → master (game 路径) | WebSocket 文件静态检查 |
| `docker-build` | PR → master (Dockerfile 路径) | Docker 镜像构建验证 |
| `release-draft` | Tag `v*.*.*` | 自动生成 Release Notes |

---

## 📦 版本历史

| 版本 | 日期 | 阶段 | 状态 |
|------|------|------|------|
| [v1.0.0](https://github.com/wutcst/kai-fa-promax/releases/tag/v1.0.0) | 2026-06-10 | Phase 4 - 正式版 | ✅ |
| [v0.3.0-beta](https://github.com/wutcst/kai-fa-promax/releases/tag/v0.3.0-beta) | 2026-06-07 | Phase 3 - 对战核心 | ✅ |
| [v0.2.0-alpha](https://github.com/wutcst/kai-fa-promax/releases/tag/v0.2.0-alpha) | 2026-06-03 | Phase 2 - 房间系统 | ✅ |
| [v0.1.0-alpha](https://github.com/wutcst/kai-fa-promax/releases/tag/v0.1.0-alpha) | 2026-05-31 | Phase 1 - 账号体系 | ✅ |

---

## 👥 团队

| 角色 | 成员 | GitHub | 职责 |
|------|------|--------|------|
| 🖥️ 后端 | 何涛 | [@xiyu1296](https://github.com/xiyu1296) | Spring Boot、API、WebSocket、部署 |
| 🎨 前端 | 陈懋任 | [@Rain-cmr](https://github.com/Rain-cmr) | Vue 3 页面、交互、联调、性能 |
| 📋 PM | 杨丝婳 | [@yangsheep123](https://github.com/yangsheep123) | Milestone、版本管理、Release、验收 |
| 🧪 测试 | 王玉珏 | [@Whole-Fall](https://github.com/Whole-Fall) | 测试、Review、CI 验证、质量保障 |

---

## 📄 许可证

MIT License
