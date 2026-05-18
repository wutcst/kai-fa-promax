/**
 * Axios 实例配置
 *
 * ── 功能说明 ──
 * 创建 axios 实例，配置 baseURL、超时时间、请求/响应拦截器。
 *
 * ── 联调说明 ──
 * 1. 请求拦截器自动从 localStorage 读取 token 并注入请求头
 * 2. 响应拦截器自动解包 response.data
 * 3. 401 未认证时自动清理存储并跳转登录页
 *
 * ── 请求拦截 ──
 * - 从 localStorage 获取 'token'
 * - 存在则设置 Authorization: Bearer {token}
 *
 * ── 响应拦截 ──
 * - 成功：返回 response.data（解包一层）
 * - 失败：401状态 → 清除登录状态 → 跳转首页
 * - 其他错误：原样返回 Promise.reject(error)
 *
 * ── 配置选项 ──
 * - baseURL: '/api'（需配合 vite proxy 或 nginx 反向代理）
 * - timeout: 10000ms（10秒超时）
 * - Content-Type: application/json
 *
 * ── 环境配置 ──
 * 开发环境：vite.config.js 配置 proxy → localhost:8081
 * 生产环境：nginx 反向代理 /api → 后端服务
 */

import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

apiClient.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.clear()
      window.location.href = '/'
    }
    return Promise.reject(error)
  }
)

export default apiClient


// ── Phase 1 联调验证记录 ──
// [2026-05] Token 注入拦截器验证通过
// [2026-05] 401 自动跳转逻辑验证通过
// [2026-05] 响应拦截器解包通过

// ── 手动测试用例 ──
// [TC-101] 请求拦截器正确注入 Authorization 头
// [TC-102] 无 token 时请求不加 Authorization 头
// [TC-103] 响应拦截器正确解包 response.data
// [TC-104] 401 响应触发自动清理和跳转
// [TC-105] 网络异常时 Promise.reject 正确传递
