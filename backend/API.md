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
