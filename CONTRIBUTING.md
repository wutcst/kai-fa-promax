# Contributing Guide

## 产品目标

1. 构建一个功能完整的在线掼蛋游戏平台
2. 实践软件工程全流程：需求分析 → 系统设计 → 编码 → 测试 → 部署
3. 通过多阶段迭代持续提升代码质量、系统可用性和可维护性
4. 建立自动化的发布和部署流水线，降低人工操作风险

## 验收标准

### Phase 1 验收标准
- 用户可注册、登录、退出系统
- 用户可创建/加入房间、进行游戏
- 系统支持 WebSocket 实时通信
- 系统具备基本的异常处理和错误提示
- 项目包含完整的文档和测试

### Phase 2 验收标准
- Release 自动发布流水线配置完成
- Docker 镜像构建和 Compose 一键部署可用
- Nginx 反向代理（含 WebSocket）配置正确
- 安全响应头过滤功能生效
- 健康检查端点可正常访问
- 版本号统一管理（v1.1.0）
- 异常路径测试全覆盖
- CI Action 失败重跑机制配置完成

## 阶段边界

### Phase 1（v1.0.0）
**范围**：用户认证 + 房间管理 + 基础游戏对战
**边界**：
- 不包含 AI 出牌建议
- 不包含智能辅助功能
- 胜率统计和战绩分析为 Phase 2 内容

### Phase 2（v1.1.0）
**范围**：项目提升——质量优化 + 文档完善 + 测试覆盖 + 部署流水线
**边界**：
- 不修改核心游戏逻辑
- 不新增功能模块
- 专注于可验收性提升

### Phase 3（v2.0.0，待规划）
**范围**：待规划——智能辅助、AI 出牌建议等

## 开发流程

1. 从 master 分支创建 feature 分支
2. 在 feature 分支上完成开发
3. 提交 PR 至 develop 分支进行代码审查
4. 审查通过后合并至 master
5. 打 Tag 触发 Release 流水线

## Commit 规范

格式：`<type>: <简短描述>`

类型：
- `feat` — 新功能
- `fix` — 修复
- `docs` — 文档变更
- `style` — 代码格式（不影响功能）
- `refactor` — 重构
- `test` — 测试相关
- `chore` — 构建/工具/配置变更

## 分支策略

- `master` — 稳定版本，只接受 PR 合并
- `develop` — 开发分支，功能分支合并目标
- `feature/*` — 功能开发分支
- `fix/*` — 修复分支

## CI/CD

### GitHub Actions
- `ci.yml` — 后端 Maven 构建 + 单元测试
- `docker-build.yml` — Docker 镜像构建
- `release.yml` — Release 自动发布

### 本地开发
```bash
# 后端
cd backend
mvn spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```
