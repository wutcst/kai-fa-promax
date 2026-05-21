/**
 * 认证相关 API 接口
 *
 * ── 功能说明 ──
 * 提供登录、注册、退出、用户信息查询等认证相关接口封装。
 * 所有请求统一使用 apiClient 实例，自动携带 Token 和 Content-Type。
 *
 * ── 联调说明 ──
 * 1. 登录接口
 *    - 请求方式: POST
 *    - 请求路径: /api/login
 *    - 请求参数: { username: string, password: string }
 *    - 成功响应: { code: 200, data: { token, userId, username, nickname } }
 *    - 失败场景: 账号不存在 / 密码错误 → 401 { code: 401, message: "..." }
 *
 * 2. 注册接口
 *    - 请求方式: POST
 *    - 请求路径: /api/register
 *    - 请求参数: { nickname: string, password: string }
 *    - 成功响应: { code: 200, data: { userId, username, nickname } }
 *    - 失败场景: 昵称重复 / 格式错误 → 400/409
 *
 * 3. 退出登录
 *    - 请求方式: POST
 *    - 请求路径: /api/user/logout
 *    - 请求头: Authorization: Bearer {token}
 *
 * 4. 获取用户信息
 *    - 请求方式: GET
 *    - 请求路径: /api/user/info
 *    - 请求头: Authorization: Bearer {token}
 *
 * ── 前端保存数据说明 ──
 * - localStorage 保存：token、username、nickname、userId、isLogin
 * - sessionStorage 保存：token、isLogin（页面会话有效）
 * - 退出登录时清理所有存储
 *
 * ── 表单校验要点 ──
 * - 账号：必填，6位纯数字（/^\d{6}$/）
 * - 密码：必填，最长10位
 * - 昵称（注册）：必填，最长10位
 * - 确认密码（注册）：必填，须与密码一致
 *
 * ── 错误展示 ──
 * - 表单校验失败：el-form 内联显示错误提示
 * - 接口返回失败：ElMessage.error() 弹出提示
 * - 网络异常：控制台 error + 用户提示
 *
 * ── 登录后跳转 ──
 * - 登录成功 → 1秒后跳转 /lobby（游戏大厅）
 * - 注册成功 → 切换至登录页，自动填充账号
 */

import apiClient from './axiosInstance'

/**
 * 获取当前存储的 Token
 * 优先从 sessionStorage 读取，降级到 localStorage
 */
export function getStoredToken() {
  return sessionStorage.getItem('token') || localStorage.getItem('token')
}

/**
 * 保存 Token 到两个存储层
 * @param {string} token - JWT Token
 * @param {boolean} rememberMe - 是否持久化保存
 */
export function saveToken(token, rememberMe = false) {
  if (!token) return
  if (rememberMe) {
    localStorage.setItem('token', token)
  }
  sessionStorage.setItem('token', token)
}

/**
 * 清除所有存储的 Token 和用户信息
 */
export function clearAuth() {
  const keys = ['token', 'username', 'nickname', 'userId', 'isLogin']
  keys.forEach(key => {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  })
}

/**
 * 检查 Token 是否存在
 * @returns {boolean}
 */
export function hasToken() {
  return !!getStoredToken()
}

/**
 * 刷新 Token：调用刷新接口并用新 Token 更新存储
 * @returns {Promise<boolean>} 刷新是否成功
 */
export async function refreshTokenAndStore() {
  try {
    const response = await apiClient.post('/user/refresh')
    if (response.data && response.data.code === 200 && response.data.data) {
      const newToken = response.data.data.token
      if (newToken) {
        saveToken(newToken, !!localStorage.getItem('token'))
        return true
      }
    }
    return false
  } catch (error) {
    console.error('Token 刷新失败:', error)
    clearAuth()
    return false
  }
}

/**
 * 获取认证请求头
 * @returns {object} Authorization 头对象
 */
export function getAuthHeader() {
  const token = getStoredToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const login = (params) => {
  return apiClient.post('/login', params)
}

export const register = (params) => {
  return apiClient.post('/register', params)
}

export const logout = () => {
  clearAuth()
  return apiClient.post('/user/logout')
}

export const getUserInfo = () => {
  return apiClient.get('/user/info')
}

export const refreshToken = () => {
  return apiClient.post('/user/refresh')
}

export const changePassword = (params) => {
  return apiClient.post('/user/change-password', params)
}


// ── Phase 1 联调验证记录 ──
// [2026-05] 登录/注册/退出全流程联调完成
// [2026-05] 表单校验规则已与后端对齐
// [2026-05] 登录后跳转 /lobby 已验证

// ── 手动测试用例 ──
// [TC-001] 正确账号密码登录 → 成功返回 token
// [TC-002] 错误密码登录 → 401 错误提示
// [TC-003] 不存在的账号登录 → 401 错误提示
// [TC-004] 空表单提交 → 校验不通过
// [TC-005] 注册成功 → 切换至登录页并填充账号
// [TC-006] 重复昵称注册 → 409 错误提示
// [TC-007] 退出登录 → 清除所有存储并跳转
// [TC-008] Token 过期 → 401 → 自动跳转登录页
