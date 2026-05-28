/**
 * WebSocket 连接管理模块
 *
 * 从 BattleView.vue 中提取的独立 WebSocket 连接逻辑，
 * 负责建立/断开连接、消息收发、事件注册。
 *
 * 【提取来源】本文件于 refactor commit 中从 BattleView.vue 提取为独立 hook，
 *   原 BattleView 中的 WebSocket 连接/重连/消息分发代码统一收敛至此。
 *
 * 用法：
 *   import webSocketService, { WS_MESSAGE_TYPES } from '../api/websocket'
 *   webSocketService.connect(userId, roomId)
 *   webSocketService.on(WS_MESSAGE_TYPES.GAME_START, handler)
 *   webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, { cards: [...] })
 *   webSocketService.disconnect()
 */

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/game'

/**
 * WebSocket 消息类型常量
 */
export const WS_MESSAGE_TYPES = {
  // 连接和房间
  JOIN_ROOM: 'joinRoom',
  JOIN_ROOM_SUCCESS: 'joinRoomSuccess',
  LEAVE_ROOM: 'leaveRoom',
  ROOM_UPDATE: 'roomUpdate',
  START_GAME: 'startGame',
  GAME_START: 'gameStart',
  GAME_END: 'gameEnd',

  // 游戏操作
  PLAY_CARD: 'playCard',
  PLAYER_ACTION: 'playerAction',
  TURN_CHANGE: 'turnChange',
  SUGGEST_CARDS: 'suggestCards',
  SUGGEST_CARDS_SUCCESS: 'suggestCardsSuccess',
  TABLE_CLEAR: 'tableClear',

  // 聊天
  CHAT_MESSAGE: 'chatMessage',

  // 通用
  ERROR: 'error',
}

class WebSocketService {
  constructor() {
    this.ws = null
    this.userId = null
    this.roomId = null
    this.listeners = {}
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectTimer = null
    this.isConnected = false
    this.isConnecting = false
  }

  /**
   * 建立 WebSocket 连接
   * @param {string} userId 用户ID
   * @param {string} roomId 房间ID
   */
  connect(userId, roomId) {
    if (this.isConnecting || this.isConnected) {
      console.warn('WebSocket 正在连接或已连接，跳过重复连接')
      return
    }

    this.userId = userId
    this.roomId = roomId
    this.isConnecting = true

    try {
      const wsUrl = `${WS_URL}?userId=${encodeURIComponent(userId)}&roomId=${encodeURIComponent(roomId)}`
      console.log('WebSocket 连接中...', wsUrl)

      this.ws = new WebSocket(wsUrl)

      this.ws.onopen = () => {
        console.log('WebSocket 已连接')
        this.isConnected = true
        this.isConnecting = false
        this.reconnectAttempts = 0
        this.emit('connect')
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          const { type, payload } = data
          if (type) {
            this.emit(type, payload || data)
          } else {
            // 兼容无 type 字段的原始消息
            this.emit('message', data)
          }
        } catch (parseError) {
          console.error('WebSocket 消息解析失败:', parseError, event.data)
        }
      }

      this.ws.onclose = (event) => {
        console.log('WebSocket 已断开:', event.code, event.reason)
        this.isConnected = false
        this.isConnecting = false
        this.emit('disconnect')
        this.tryReconnect()
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket 连接错误:', error)
        this.isConnecting = false
        this.emit('error', { message: 'WebSocket 连接错误' })
      }
    } catch (error) {
      console.error('WebSocket 连接失败:', error)
      this.isConnecting = false
      this.emit('error', { message: `WebSocket 连接失败: ${error.message}` })
    }
  }

  /**
   * 尝试重连
   */
  tryReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket 重连次数已达上限')
      return
    }

    this.reconnectAttempts++
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 10000)
    console.log(`WebSocket 将在 ${delay}ms 后重连 (第 ${this.reconnectAttempts} 次)`)

    this.reconnectTimer = setTimeout(() => {
      if (this.userId && this.roomId) {
        this.connect(this.userId, this.roomId)
      }
    }, delay)
  }

  /**
   * 发送消息
   * @param {string} type 消息类型
   * @param {object} payload 消息内容
   */
  send(type, payload = {}) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket 未连接，无法发送消息')
      return
    }

    const message = JSON.stringify({
      type,
      payload,
      userId: this.userId,
      roomId: this.roomId,
    })

    try {
      this.ws.send(message)
    } catch (error) {
      console.error('WebSocket 发送消息失败:', error)
    }
  }

  /**
   * 注册事件监听
   * @param {string} event 事件名称
   * @param {function} callback 回调函数
   */
  on(event, callback) {
    if (!this.listeners[event]) {
      this.listeners[event] = []
    }
    this.listeners[event].push(callback)
  }

  /**
   * 移除事件监听
   * @param {string} event 事件名称
   * @param {function} callback 回调函数
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
   * @param {string} event 事件名称
   * @param {object} data 事件数据
   */
  emit(event, data) {
    const handlers = this.listeners[event]
    if (!handlers) return
    handlers.forEach(callback => {
      try {
        callback(data)
      } catch (error) {
        console.error(`WebSocket 事件处理出错 [${event}]:`, error)
      }
    })
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

    if (this.ws) {
      try {
        this.ws.close()
      } catch (error) {
        console.error('WebSocket 关闭失败:', error)
      }
      this.ws = null
    }

    this.isConnected = false
    this.isConnecting = false
    this.userId = null
    this.roomId = null
    this.listeners = {}
  }

  /**
   * 获取连接状态
   */
  getConnectionStatus() {
    if (this.isConnected && this.ws && this.ws.readyState === WebSocket.OPEN) {
      return 'connected'
    }
    if (this.isConnecting) return 'connecting'
    return 'disconnected'
  }
}

const webSocketService = new WebSocketService()
export default webSocketService
