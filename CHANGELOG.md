# Changelog

## [v1.2.0] - 2026-06-12

### 新增
- 后端：Docker 多阶段构建，生产镜像体积优化
- 后端：docker-compose 生产环境配置，集成 Nginx 反向代理
- 后端：Nginx SSL 配置、限流保护（API 30r/s + 并发连接限制）
- 后端：日志卷挂载（backend_logs, nginx_logs, backend_data）
- 后端：密码重置流程（邮箱验证码 + 限时 Token 机制）
- 后端：游戏回放记录存储，支持按回合分段查询
- 后端：Jackson 全局序列化配置优化
- 后端：Docker Compose 服务健康检查全覆盖
- 后端：Nginx HSTS 和安全响应头增强

### 优化
- 后端：Jackson 日期格式统一为 yyyy-MM-dd HH:mm:ss
- 后端：Jackson null 字段不输出，减少 payload 体积
- 后端：Jackson Long 转 String 输出，防止前端精度丢失
- 后端：Dockerfile 非 root 用户运行，安全加固
- 后端：Nginx 配置从 HTTP 升级为 HTTPS + HTTP/2
- 后端：Redis 服务增加健康检查

### 文档
- API 文档：新增 Phase 4 接口说明（密码重置、游戏回放、部署配置、序列化规范）
- CHANGELOG：新增 v1.2.0 版本记录

### 验收标准（v1.2.0）
- [x] Docker 多阶段构建通过
- [x] docker-compose 生产环境正常启动
- [x] Nginx HTTPS + 限流配置生效
- [x] 密码重置全流程（发码 → 校验 → 重置）可用
- [x] 游戏回放按回合存储和分段查询正常
- [x] Jackson 序列化规范生效（日期格式、null 处理、字符转义）

---

## [v1.1.0] - 2026-06-07

### 新增
- 后端：Release 自动发布流水线（GitHub Actions + Release）
- 后端：Docker 镜像构建 CI
- 后端：Docker Compose 一键部署
- 后端：Nginx 反向代理配置（含 WebSocket 支持）
- 后端：安全响应头过滤（CSP、XSS、Clickjacking）
- 后端：健康检查端点（/actuator/health）
- 后端：版本号统一管理（v1.1.0）
- 后端：异常路径全覆盖测试清单
- 后端：CI Action 失败重跑机制
- 前端：个人中心战绩详情组件（RecordDetail.vue）
- 前端：个人中心胜率概览、筛选和分页功能
- 前端：个人中心响应式布局适配

### 优化
- 后端：规范统一命名规则，补充维护注释
- 后端：优化异常处理流程
- 后端：密码 BCrypt 加密存储
- 后端：Token 认证拦截器完善
- 前端：优化错误提示和空数据兜底展示

### 文档
- README：补充项目概述、产品目标和验收标准
- API 文档：完善接口异常场景描述
- 测试文档：增加回归验证点和手动测试用例
- 运行指南：补充 Docker 部署说明

### 验收标准（v1.1.0）
- [x] Release 自动发布流水线
- [x] Docker 镜像构建 CI
- [x] Docker Compose 一键部署
- [x] Nginx 反向代理配置
- [x] 安全响应头过滤
- [x] 健康检查端点
- [x] 版本号统一管理
- [x] 异常路径全覆盖测试清单
- [x] CI Action 失败重跑机制

---

## [v1.0.0] - 2026-05-20

### 新增
- 用户注册/登录功能
- 房间管理（创建/加入/退出/解散/转移房主）
- 游戏对战核心逻辑
- WebSocket 实时通信
- 匹配服务
- 后端：Spring Boot 3.1.5 + MyBatis Plus 集成
- 后端：用户实体、Mapper 及数据库表
- 后端：Token 拦截器与用户上下文
- 前端：Vue 3 + Vite + Element Plus 搭建
- 前端：登录/注册页面及表单校验
- 前端：路由守卫与基础页面（大厅、个人主页）

### 文档
- 项目 README
- API 接口文档
- 运行指南

### 验收标准（v1.0.0）
- [x] 用户可注册、登录、退出系统
- [x] 用户可创建/加入房间、进行游戏
- [x] 系统支持 WebSocket 实时通信
- [x] 系统具备基本的异常处理和错误提示
- [x] 项目包含完整的文档和测试
