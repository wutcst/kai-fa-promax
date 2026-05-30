import { ElMessage } from 'element-plus';

/**
 * WebSocket 连接管理模块
 *
 * 从 BattleView.vue 中提取的独立 WebSocket 连接逻辑，
 * 负责建立/断开连接、消息收发、事件注册。
 *
 * 用法：
 *   import webSocketService, { WS_MESSAGE_TYPES } from '../api/websocket'
 *   webSocketService.connect(userId, roomId)
 *   webSocketService.on(WS_MESSAGE_TYPES.GAME_START, handler)
 *   webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, { cards: [...] })
 *   webSocketService.disconnect()
 */

// 动态获取WebSocket服务器URL
const getWebSocketURL = (playerId, token) => {
  const currentOrigin = window.location.origin
  if (currentOrigin.includes('localhost') || currentOrigin.includes('127.0.0.1')) {
    return `ws://localhost:8081/ws/game/${playerId}?token=${token}`
  }
  const url = new URL(currentOrigin)
  return `${url.protocol === 'https:' ? 'wss:' : 'ws:'}//${url.hostname}:8081/ws/game/${playerId}?token=${token}`
}

/**
 * WebSocket 消息类型常量
 */
export const WS_MESSAGE_TYPES = {
  // 连接和房间
  JOIN_ROOM: 'JOIN_ROOM',
  JOIN_ROOM_SUCCESS: 'JOIN_ROOM_SUCCESS',
  LEAVE_ROOM: 'LEAVE_ROOM',
  ROOM_UPDATE: 'ROOM_UPDATE',
  START_GAME: 'START_GAME',
  GAME_START: 'GAME_START',
  GAME_END: 'GAME_END',

  // 游戏操作
  PLAY_CARD: 'PLAY_CARD',
  PLAYER_ACTION: 'PLAYER_ACTION',
  TURN_CHANGE: 'TURN_CHANGE',
  SUGGEST_CARDS: 'SUGGEST_CARDS',
  SUGGEST_CARDS_SUCCESS: 'SUGGEST_CARDS_SUCCESS',
  TABLE_CLEAR: 'TABLE_CLEAR',

  // 连接管理
  HEARTBEAT: 'HEARTBEAT',
  RECONNECT: 'RECONNECT',
  RECONNECT_SUCCESS: 'RECONNECT_SUCCESS',

  // 聊天
  CHAT_MESSAGE: 'CHAT_MESSAGE',

  // 通用
  ERROR: 'ERROR'
}

class WebSocketService {
  constructor() {
    this.socket = null
    this.isConnected = false
    this.playerId = null
    this.roomId = null
    this.token = null
    this.listeners = {}
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectInterval = 3000
    this.heartbeatInterval = 30000
    this.heartbeatTimer = null
    this.reconnectTimer = null
  }

  /**
   * 建立 WebSocket 连接
   * @param {string} playerId - 玩家ID
   * @param {string} roomId - 房间ID（可选）
   * @param {string} token - JWT Token
   */
  connect(playerId, roomId = null, token = null) {
    if (this.socket && this.isConnected) {
      console.log('WebSocket已连接')
      return
    }

    this.playerId = playerId
    this.roomId = roomId

    // 获取JWT Token
    if (!token) {
      token = localStorage.getItem('token') || sessionStorage.getItem('token')
    }
    this.token = token

    if (!token) {
      console.error('未找到JWT Token，无法连接WebSocket')
      ElMessage.error('未登录，请先登录')
      return
    }

    const wsUrl = getWebSocketURL(playerId, token)
    console.log('尝试连接WebSocket:', wsUrl)

    try {
      this.socket = new WebSocket(wsUrl)

      this.socket.onopen = () => {
        console.log('WebSocket连接成功')
        this.isConnected = true
        this.reconnectAttempts = 0

        // 启动心跳检测
        this.startHeartbeat()

        // 加入房间（如果提供了房间ID）
        if (this.roomId) {
          console.log('发送加入房间请求:', this.roomId)
          this.send(WS_MESSAGE_TYPES.JOIN_ROOM, { roomId: this.roomId })
        }

        // 触发连接成功事件
        this.emit('connect')
      }

      this.socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          const { type, data } = message

          if (type) {
            this.emit(type, data || message)
          } else {
            this.emit('message', data || message)
          }
        } catch (parseError) {
          console.error('WebSocket消息解析失败:', parseError, event.data)
        }
      }

      this.socket.onclose = (event) => {
        console.log('WebSocket连接关闭, code:', event.code)
        this.isConnected = false

        // 停止心跳检测
        this.stopHeartbeat()

        // 触发连接关闭事件
        this.emit('disconnect')

        // 如果不是手动关闭，尝试重连
        if (event.code !== 1000) {
          this.attemptReconnect()
        }
      }

      this.socket.onerror = (error) => {
        console.error('WebSocket错误:', error)
        this.emit('error', error)
      }
    } catch (error) {
      console.error('WebSocket连接失败:', error)
      this.emit('error', error)

      // 尝试重连
      this.attemptReconnect()
    }
  }

  /**
   * 断开 WebSocket 连接
   */
  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    this.reconnectAttempts = this.maxReconnectAttempts // 阻止重连

    if (this.socket) {
      this.socket.close(1000)
      this.socket = null
      this.isConnected = false
    }

    // 停止心跳检测
    this.stopHeartbeat()

    // 清除状态
    this.playerId = null
    this.roomId = null
    this.listeners = {}

    console.log('WebSocket已手动断开')
  }

  /**
   * 发送消息
   * @param {string} type - 消息类型
   * @param {object} data - 消息数据
   */
  send(type, data = {}) {
    if (!this.socket) {
      console.error('WebSocket未初始化，无法发送消息')
      return false
    }

    if (this.socket.readyState !== WebSocket.OPEN) {
      console.error('WebSocket未就绪，当前状态:', this.socket.readyState)
      return false
    }

    try {
      const message = JSON.stringify({ type, data, playerId: this.playerId })
      this.socket.send(message)
      return true
    } catch (error) {
      console.error('发送WebSocket消息失败:', error)
      return false
    }
  }

  /**
   * 加入房间
   * @param {string} roomId - 房间ID
   */
  joinRoom(roomId) {
    this.roomId = roomId
    return this.send(WS_MESSAGE_TYPES.JOIN_ROOM, { roomId })
  }

  /**
   * 出牌
   * @param {Array<number>} cards - 要出的牌ID数组
   */
  playCard(cards) {
    return this.send(WS_MESSAGE_TYPES.PLAY_CARD, { cards })
  }

  /**
   * 发送心跳
   */
  sendHeartbeat() {
    return this.send(WS_MESSAGE_TYPES.HEARTBEAT, { timestamp: Date.now() })
  }

  /**
   * 启动心跳检测
   */
  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat()
    }, this.heartbeatInterval)
  }

  /**
   * 停止心跳检测
   */
  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /**
   * 尝试重连
   */
  attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket重连失败，已达到最大重连次数')
      ElMessage.error('网络连接失败，请刷新页面重试')
      return
    }

    this.reconnectAttempts++
    console.log(`WebSocket尝试重连(${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)

    this.reconnectTimer = setTimeout(() => {
      if (this.playerId) {
        this.connect(this.playerId, this.roomId)
      }
    }, this.reconnectInterval)
  }

  /**
   * 注册事件监听器
   * @param {string} event - 事件类型
   * @param {Function} callback - 回调函数
   */
  on(event, callback) {
    if (!this.listeners[event]) {
      this.listeners[event] = []
    }
    this.listeners[event].push(callback)
  }

  /**
   * 移除事件监听器
   * @param {string} event - 事件类型
   * @param {Function} callback - 回调函数
   */
  off(event, callback) {
    if (!this.listeners[event]) return
    if (!callback) {
      delete this.listeners[event]
      return
    }
    this.listeners[event] = this.listeners[event].filter(cb => cb !== callback)
  }

  /**
   * 触发事件
   * @param {string} event - 事件名称
   * @param {*} data - 事件数据
   */
  emit(event, data) {
    const handlers = this.listeners[event]
    if (!handlers) return
    handlers.forEach(callback => {
      try {
        callback(data)
      } catch (error) {
        console.error(`WebSocket事件处理出错 [${event}]:`, error)
      }
    })
  }

  /**
   * 获取连接状态
   */
  getConnectionStatus() {
    if (this.isConnected && this.socket && this.socket.readyState === WebSocket.OPEN) {
      return 'connected'
    }
    return 'disconnected'
  }

  // ============================================================
  //  测试验证点：WebSocket 消息序列化和反序列化
  // ============================================================

  /**
   * 序列化待发送消息（供测试验证用）
   * @param {string} type - 消息类型
   * @param {object} data - 消息数据
   * @returns {string} 序列化后的 JSON 字符串
   */
  serializeMessage(type, data = {}) {
    return JSON.stringify({ type, data, playerId: this.playerId })
  }

  /**
   * 反序列化收到的消息（供测试验证用）
   * @param {string} json - 收到的 JSON 字符串
   * @returns {{type: string, data: object}|null} 解析后的消息对象，解析失败返回 null
   */
  deserializeMessage(json) {
    try {
      const parsed = JSON.parse(json)
      if (!parsed || typeof parsed !== 'object') return null
      if (!parsed.type || typeof parsed.type !== 'string') return null
      return { type: parsed.type, data: parsed.data || parsed }
    } catch {
      return null
    }
  }

  /**
   * 验证消息结构完整性（供测试验证用）
   * @param {object} message - 消息对象
   * @returns {{valid: boolean, errors: string[]}}
   */
  validateMessage(message) {
    const errors = []
    if (!message || typeof message !== 'object') {
      errors.push('消息必须是非空对象')
      return { valid: false, errors }
    }
    if (!message.type || typeof message.type !== 'string') {
      errors.push('消息类型(type)必须是非空字符串')
    }
    if (message.type && message.type.length > 50) {
      errors.push('消息类型(type)长度不能超过50个字符')
    }
    if (message.data !== undefined && message.data !== null
        && typeof message.data !== 'object') {
      errors.push('消息数据(data)必须是对象类型')
    }
    if (message.playerId !== undefined && message.playerId !== null
        && typeof message.playerId !== 'string') {
      errors.push('玩家ID(playerId)必须是字符串')
    }
    return { valid: errors.length === 0, errors }
  }
}

// 创建单例实例
const webSocketService = new WebSocketService()

export default webSocketService
