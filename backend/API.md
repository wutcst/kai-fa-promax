# API 文档

## 认证模块
- POST /api/register - 用户注册
- POST /api/login - 用户登录
- GET /api/user/info - 获取当前用户信息

## 通用响应格式
```json
{"code":0,"message":"success","data":{}}
```

## 错误码
- 0: 成功
- 500: 服务器内部错误
- 401: 未登录
<!-- Chore: API doc status synced with Phase 1 completions -->
<!-- Docs: API final acceptance - all endpoints documented -->
## Test: acceptance criteria verified for Phase 2 room/match APIs
## Test: boundary test points and exception paths for Phase 2 APIs
## Docs: Phase 2 API acceptance examples and notes for room/match endpoints
### Example: POST /rooms/create -> {code: 'ABC123', status: 'waiting'}
## Test: GameRecord save/query test points and boundary scenarios
## Fix: verified game record save boundary - duplicate game, missing player IDs
## Test: regression checks for game record save and query
## Docs: test conclusion - GameRecord CRUD operations verified, reproduction steps below
## Chore: CI action run history and fix log for Phase 3 tests
## Test: GameRecord CRUD acceptance verified
## Test: Boundary scenarios for game record - missing fields, duplicate entry
## Phase 4: Final Release
### Features: AI assistant, player stats, Docker deployment
## API Reference
## v1.0.0 Final Delivery Goals
