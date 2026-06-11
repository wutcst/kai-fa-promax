import { ElMessage } from 'element-plus';

/**
 * WebSocket 连接管理模块
 *
 * 从 BattleView.vue 中提取的独立 WebSocket 连接逻辑，
 * 负责建立/断开连接、消息收发、事件注册。
 *
 * 联调说明（提升对战页操作体验）：
 * - 手牌展示：服务端通过 GAME_START 消息的 myCards 字段下发手牌 ID 数组，
 *   前端调用 idToCard 转换为前端对象后按 rank 排序渲染。
 * - 选牌交互：前端维护 selectedCards 索引数组，点击/拖拽时切换选中状态，
 *   选中的卡牌通过 CSS .selected 类上移 10px 突出显示。
 * - 出牌流程：前端通过 PLAY_CARD 消息发送选中的 cardIds 至服务端，
 *   服务端校验合法性后广播 PLAYER_ACTION 给房间所有玩家。
 * - 过牌反馈：前端通过 PLAY_CARD 发送空数组表示"不出"，
 *   服务端处理后广播 PLAYER_ACTION（含 pass 标记）。
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
    this.maxReconnectAttempts = 10
    this.reconnectInterval = 3000
    this.heartbeatInterval = 30000
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.lastNotReadyToastAt = 0

    // 指数退避重连参数
    this.baseDelay = 1000       // 初始延迟 1s
    this.maxDelay = 30000       // 最大延迟 30s
    this.jitterFactor = 0.3     // 随机抖动因子 ±30%
    this.lastReconnectAt = 0    // 上次重连时间
    this.healthCheckTimer = null
    this.healthCheckInterval = 15000  // 健康检查间隔 15s
    this.lastMessageAt = 0      // 最后收到消息的时间
    this.connectionStartTime = 0
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
      this.connectionStartTime = Date.now()

      this.socket.onopen = () => {
        console.log('WebSocket连接成功')
        this.isConnected = true
        this.reconnectAttempts = 0
        this.lastMessageAt = Date.now()

        // 启动心跳检测
        this.startHeartbeat()

        // 启动健康检查
        this.startHealthCheck()

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

          // 更新最后消息接收时间
          this.lastMessageAt = Date.now()

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

        // 停止心跳检测和健康检查
        this.stopHeartbeat()
        this.stopHealthCheck()

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

    // 停止心跳检测和健康检查
    this.stopHeartbeat()
    this.stopHealthCheck()

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
      // 空状态防御：socket 不存在时不弹重复 toast
      if (this.lastNotReadyToastAt === 0 || Date.now() - this.lastNotReadyToastAt > 5000) {
        ElMessage.error('网络未连接，请稍后重试')
        this.lastNotReadyToastAt = Date.now()
      }
      return false
    }

    if (this.socket.readyState !== WebSocket.OPEN) {
      console.error('WebSocket未就绪，当前状态:', this.socket.readyState)
      const now = Date.now()
      if (this.socket.readyState === WebSocket.CONNECTING) {
        if (now - this.lastNotReadyToastAt > 1500) {
          ElMessage.info('网络连接中，请稍后…')
          this.lastNotReadyToastAt = now
        }
      } else {
        if (now - this.lastNotReadyToastAt > 1500) {
          ElMessage.error('网络连接未就绪，请稍后重试')
          this.lastNotReadyToastAt = now
        }
      }
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
   * 启动健康检查
   *
   * 定期检查 WebSocket 连接的健康状况。如果在健康检查间隔内
   * 没有收到任何消息（包括心跳 pong），则认为连接已失效，
   * 触发主动重连。
   */
  startHealthCheck() {
    this.stopHealthCheck()
    this.lastMessageAt = Date.now()
    this.healthCheckTimer = setInterval(() => {
      const now = Date.now()
      const elapsed = now - this.lastMessageAt

      // 如果超过心跳间隔的2倍仍未收到任何消息，判定为健康检查超时
      if (elapsed > this.heartbeatInterval * 2) {
        console.warn(`WebSocket健康检查超时: 已${Math.round(elapsed / 1000)}秒无消息`)
        this.emit('healthCheckTimeout', { elapsed })

        // 尝试发送一个 ping 探测
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
          try {
            this.socket.send(JSON.stringify({ type: 'ping', data: { timestamp: now } }))
          } catch (e) {
            console.error('健康检查ping发送失败:', e)
          }
        }
      }
    }, this.healthCheckInterval)
  }

  /**
   * 停止健康检查
   */
  stopHealthCheck() {
    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer)
      this.healthCheckTimer = null
    }
  }

  /**
   * 计算指数退避延迟（带随机抖动）
   *
   * 退避策略：baseDelay * 2^attempt，上限 maxDelay
   * 随机抖动：在计算值的基础上 ±jitterFactor 范围内的随机偏移
   *
   * @param {number} attempt 当前重连尝试次数（从0开始）
   * @returns {number} 计算后的延迟毫秒数
   */
  getBackoffDelay(attempt) {
    // 指数退避：1s, 2s, 4s, 8s, 16s, 30s, 30s...
    const exponentialDelay = Math.min(
      this.baseDelay * Math.pow(2, attempt),
      this.maxDelay
    )

    // 随机抖动：±30%
    const jitter = exponentialDelay * this.jitterFactor * (Math.random() * 2 - 1)
    const finalDelay = Math.round(exponentialDelay + jitter)

    // 确保不小于 baseDelay 的一半，不大于 maxDelay
    return Math.max(Math.round(this.baseDelay / 2), Math.min(finalDelay, this.maxDelay))
  }

  /**
   * 尝试重连（指数退避 + 随机抖动）
   */
  attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket重连失败，已达到最大重连次数')
      ElMessage.error('网络连接失败，请刷新页面重试')
      return
    }

    this.reconnectAttempts++

    // 计算指数退避延迟
    const attemptIndex = this.reconnectAttempts - 1
    const delay = this.getBackoffDelay(attemptIndex)

    // 防止短时间内多次重连
    const now = Date.now()
    const timeSinceLastReconnect = now - this.lastReconnectAt
    const effectiveDelay = Math.max(delay, timeSinceLastReconnect < delay ? delay - timeSinceLastReconnect : 0)

    console.log(
      `WebSocket尝试重连(${this.reconnectAttempts}/${this.maxReconnectAttempts})...` +
      ` 退避延迟: ${delay}ms, 实际延迟: ${effectiveDelay}ms`
    )

    this.lastReconnectAt = now + effectiveDelay

    this.reconnectTimer = setTimeout(() => {
      if (this.playerId) {
        this.connect(this.playerId, this.roomId)
      }
    }, effectiveDelay)
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

  // ============================================================
  //  手动测试用例：提升对战页操作体验
  // ============================================================

  /**
   * 测试用例 TC-WS-001：手牌展示
   * 前置条件：连接 WebSocket 并收到 GAME_START 消息
   * 操作步骤：
   *   1. webSocketService.on(WS_MESSAGE_TYPES.GAME_START, handler) 注册监听
   *   2. 服务端发送 { type: "GAME_START", data: { myCards: [0,1,2,...] } }
   *   3. handler 调用 idsToCards 转换 → sortCards 排序 → 渲染
   * 预期结果：手牌区域按 rank 降序正确渲染 27 张牌，无重复或丢失
   */
  // TC-WS-001 手牌展示

  /**
   * 测试用例 TC-WS-002：选牌交互
   * 前置条件：已进入游戏阶段且 currentPlayer === '我'
   * 操作步骤：
   *   1. 点击第一张牌 → handleCardMousedown(0) → toggleCardLogic(0)
   *   2. 再次点击第一张牌 → toggleCardLogic(0) 取消选中
   *   3. 拖拽经过多张牌（mousedown → mouseenter 连续选中）
   * 预期结果：选中牌上移 10px 高亮；快速点选防抖在 150ms 内生效；
   *          拖拽选牌正确切换选中状态
   */
  // TC-WS-002 选牌交互

  /**
   * 测试用例 TC-WS-003：出牌反馈
   * 前置条件：已选中至少一张牌
   * 操作步骤：
   *   1. 选中卡牌 → 点击"出牌"按钮 → playCards()
   *   2. playCards 调用 send(PLAY_CARD, { cards: [cardIds] })
   *   3. 服务端广播 PLAYER_ACTION 和 TURN_CHANGE
   * 预期结果：手牌中已出牌移除；桌面显示打出的牌；回合切换为下一玩家
   */
  // TC-WS-003 出牌反馈

  /**
   * 测试用例 TC-WS-004：过牌反馈
   * 前置条件：已进入游戏阶段且 currentPlayer === '我'，非自由出牌
   * 操作步骤：
   *   1. 点击"不出"按钮 → pass() → send(PLAY_CARD, { cards: [] })
   *   2. 服务端广播 PLAYER_ACTION 带空 cards
   *   3. 倒计时归零自动过牌（autoPlaySmallestCard / pass）
   * 预期结果：桌面显示"不要"指示器；回合切换到下一玩家；
   *          倒计时归零后自动触发过牌
   */
  // TC-WS-004 过牌反馈
}

// 创建单例实例
const webSocketService = new WebSocketService()

export default webSocketService
