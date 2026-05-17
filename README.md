# 掼蛋游戏项目

> 武汉理工大学软件工程实践课程项目

## 项目概述

掼蛋游戏是一个基于 Web 的在线掼蛋对战平台，支持用户注册登录、房间管理、游戏对战、实时通信等核心功能。本项目作为软件工程实践课程的综合实训项目，采用前后端分离架构，覆盖从需求分析、系统设计、编码实现到测试部署的完整软件生命周期。

### 项目目标
1. 构建一个功能完整的在线掼蛋游戏平台
2. 实践软件工程全流程：需求分析 → 系统设计 → 编码 → 测试 → 部署
3. 通过多阶段迭代持续提升代码质量、系统可用性和可维护性

### 验收标准
- 用户可注册、登录、退出系统
- 用户可创建/加入房间、进行游戏
- 系统支持 WebSocket 实时通信
- 系统具备基本的异常处理和错误提示
- 项目包含完整的文档和测试

## 项目结构

```
Login+Battle/
├── backend/              # 后端项目
│   ├── src/
│   │   └── main/
│   │       ├── java/     # Java源代码
│   │       └── resources/ # 配置文件
│   ├── pom.xml          # Maven配置
│   └── README.md       # 后端说明文档
├── frontend/            # 前端项目
│   ├── src/
│   │   ├── api/       # API接口
│   │   ├── assets/    # 静态资源
│   │   ├── components/# 组件
│   │   ├── router/    # 路由配置
│   │   ├── utils/     # 工具函数
│   │   └── views/     # 页面组件
│   ├── dist/          # 构建输出
│   ├── package.json    # NPM配置
│   └── vite.config.js # Vite配置
├── 运行指南.md        # 项目运行指南
└── .gitignore        # Git忽略配置
```

## 技术栈

### 后端
- Spring Boot 3.1.5
- MyBatis Plus 3.5.4
- WebSocket
- MySQL

### 前端
- Vue 3
- Vite
- Element Plus
- Axios

## 快速开始

### 后端启动
```bash
cd backend
mvn spring-boot:run
```

### 前端启动
```bash
cd frontend
npm install
npm run dev
```

详细说明请参考 [运行指南.md](运行指南.md)
