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

/**
 * 错误码映射表（与后端 ErrorCode 枚举同步）
 *
 * 编码规则：
 *   1xxx — 通用错误
 *   2xxx — 用户认证
 *   3xxx — 房间相关
 *   4xxx — 游戏相关
 *   5xxx — WebSocket
 *   6xxx — 数据相关
 *   7xxx — 观战相关
 */
const ERROR_CODE_MAP = {
  // ── 通用 (1xxx) ──
  200: 'success',
  400: '请求参数错误',
  401: '未登录或Token已过期',
  403: '无权限访问',
  404: '资源不存在',
  405: '请求方法不允许',
  409: '资源冲突',
  429: '请求过于频繁',
  500: '服务器内部错误',
  503: '服务暂不可用',

  // ── 用户认证 (2xxx) ──
  2001: '账号不存在',
  2002: '该账号已被注册',
  2003: '该账号已登录，请先退出',
  2004: '密码错误',
  2005: '密码长度必须在6-10位之间',
  2006: 'Token格式无效',
  2007: 'Token已过期',
  2008: '登录失败，请检查账号和密码',
  2009: '注册失败，请稍后重试',

  // ── 房间相关 (3xxx) ──
  3001: '房间不存在',
  3002: '房间已满，无法加入',
  3003: '游戏已开始，无法加入',
  3004: '房间不在等待状态',
  3005: '玩家不在该房间中',
  3006: '玩家已在房间中',
  3007: '只有房主可以开始游戏',
  3008: '人数不足',
  3009: '还有玩家未准备',
  3010: '房间已解散',

  // ── 游戏相关 (4xxx) ──
  4001: '游戏未开始或已结束',
  4002: '游戏已开始',
  4003: '现在不是你的回合',
  4004: '牌型不合法',
  4005: '无法管住上一手牌',
  4006: '手牌中不包含指定的卡牌',
  4007: '出牌失败，请检查牌型或回合',
  4008: '过牌处理失败',
  4009: '游戏已结束',

  // ── WebSocket (5xxx) ──
  5001: 'WebSocket连接失败',
  5002: '会话已过期',
  5003: '消息格式无效',
  5004: '重连失败，请刷新页面重试',

  // ── 数据相关 (6xxx) ──
  6001: '数据不存在',
  6002: '数据已过期',
  6003: '操作失败',
  6004: '重复操作，请勿重复提交',

  // ── 观战相关 (7xxx) ──
  7001: '观战人数已满',
  7002: '当前房间不允许观战',
  7003: '已在观战中',
  7004: '房间游戏已结束',
}

/**
 * 根据后端错误码获取中文消息文本
 * @param {number} code - 后端返回的错误码
 * @param {string} fallback - 备用文本
 * @returns {string} 中文消息
 */
function getErrorMessageByCode(code, fallback) {
  const msg = ERROR_CODE_MAP[code]
  if (msg) return msg
  if (fallback) return fallback
  return `未知错误(${code})`
}

/**
 * 标准化错误对象，附加 errorCode、localizedMessage 字段
 * @param {Error} error - axios 错误对象
 * @param {number} errorCode - 后端返回的错误码
 * @returns {Error} 增强后的错误对象
 */
function normalizeError(error, errorCode) {
  if (errorCode) {
    error.errorCode = errorCode
    error.localizedMessage = getErrorMessageByCode(errorCode)
  }
  const serverMsg = error.response?.data?.message
  if (!error.localizedMessage && serverMsg) {
    error.localizedMessage = serverMsg
  }
  error.localizedMessage = error.localizedMessage || error.message || '网络异常'
  return error
}

apiClient.interceptors.response.use(
  response => {
    const body = response.data

    // 后端标准响应格式：{ code, message, data }
    if (body && typeof body === 'object' && body.code !== undefined) {
      // 非 200 视为业务异常，reject 出去让调用方 catch
      if (body.code !== 200) {
        const normalized = new Error(body.message || getErrorMessageByCode(body.code))
        normalized.errorCode = body.code
        normalized.localizedMessage = getErrorMessageByCode(body.code, body.message)
        normalized.response = response
        return Promise.reject(normalized)
      }
      // 解包 data 字段，调用方直接拿到业务数据
      return body.data !== undefined ? body.data : body
    }

    // 非标准响应（第三方接口等），原样透传
    return body
  },
  error => {
    const response = error.response

    // ── 无响应（网络断开、超时） ──
    if (!response) {
      if (error.code === 'ECONNABORTED') {
        error.localizedMessage = '请求超时，请检查网络连接'
      } else {
        error.localizedMessage = '网络异常，无法连接到服务器'
      }
      error.errorCode = -1
      return Promise.reject(error)
    }

    // ── 有响应 ──
    const status = response.status
    const body = response.data
    let errorCode = status

    // 优先取后端返回的 code
    if (body && body.code !== undefined) {
      errorCode = body.code
    }

    // 401 → 清除登录态
    if (status === 401 || errorCode === 401 || errorCode === 2007) {
      localStorage.clear()
      sessionStorage.clear()
      // 防止重复跳转
      if (window.location.pathname !== '/' && window.location.pathname !== '/login') {
        window.location.href = '/'
      }
    }

    normalizeError(error, errorCode)

    // 403 → 无权限提示
    if (status === 403 || errorCode === 403) {
      console.warn('[Auth] 无权限访问:', error.config?.url)
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
