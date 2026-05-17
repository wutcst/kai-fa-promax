# 掼蛋游戏前端

## 项目简介

掼蛋游戏前端是基于 Vue 3 + Vite 开发的单页应用，提供用户登录注册、游戏大厅、个人主页等核心界面。

### 技术栈
- Vue 3 (Composition API + `<script setup>`)
- Vue Router 4 (Hash 模式路由)
- Element Plus (UI 组件库)
- Axios (HTTP 请求)
- Vite (构建工具)

## 快速开始

### 环境要求
- Node.js 16+
- npm 8+

### 安装依赖
```bash
npm install
```

### 开发模式运行
```bash
npm run dev
```

### 构建生产版本
```bash
npm run build
```

## 项目结构

```
frontend/
├── src/
│   ├── api/              # API 接口封装
│   │   ├── auth.js       # 认证相关接口（登录/注册/退出）
│   │   └── axiosInstance.js  # Axios 实例配置（拦截器）
│   ├── assets/           # 静态资源（图片、样式）
│   ├── components/       # 公共组件
│   ├── router/
│   │   └── index.js      # 路由配置与守卫
│   ├── utils/            # 工具函数
│   └── views/            # 页面组件
│       ├── login/
│       │   └── Login.vue # 登录/注册页
│       ├── lobby/
│       │   └── Lobby.vue # 游戏大厅
│       └── PersonalHome.vue  # 个人主页
├── dist/                 # 构建输出目录
├── package.json
├── vite.config.js
└── README.md
```

## 可用页面

| 路由 | 页面 | 需要登录 |
|------|------|----------|
| `/login` | 登录/注册 | 否 |
| `/lobby` | 游戏大厅 | 是 |
| `/personal-home` | 个人主页 | 是 |

## 环境变量配置

在项目根目录创建 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:8081/api
```

如果不配置，默认使用 `/api` 作为 baseURL（需配置后端代理）。

## 代理配置

在 `vite.config.js` 中添加：

```js
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
```
