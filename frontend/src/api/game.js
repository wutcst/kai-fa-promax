import axiosInstance from './axiosInstance'

/**
 * 加入房间
 *
 * 空值保护：username 不存在时返回错误，不发起请求。
 * 防止未登录用户误操作。
 */
export const joinRoom = (roomNo) => {
  const username = sessionStorage.getItem('username') || localStorage.getItem('username')
  if (!username) {
    return Promise.reject(new Error('用户未登录，请先登录'))
  }
  return axiosInstance.post('/room/join', { roomNo, username })
}

/**
 * 退出房间
 *
 * 空值保护：roomId 为空时直接拒绝，避免无效请求。
 */
export const exitRoom = (roomId) => {
  if (!roomId) {
    return Promise.reject(new Error('房间ID不能为空'))
  }
  return axiosInstance.post('/room/exit', { roomId })
}

/**
 * 获取房间详细信息
 *
 * 空值保护：roomNo 为空时直接拒绝。
 */
export const getRoomDetail = (roomNo) => {
  if (!roomNo) {
    return Promise.reject(new Error('房间号不能为空'))
  }
  return axiosInstance.get(`/room/${roomNo}/detail`)
}

/**
 * 解散房间
 *
 * 空值保护：roomNo 为空时直接拒绝。
 */
export const dissolveRoom = (roomNo) => {
  if (!roomNo) {
    return Promise.reject(new Error('房间号不能为空'))
  }
  return axiosInstance.post(`/room/${roomNo}/dissolve`)
}

// 准备就绪
export const ready = (roomId) => {
  return axiosInstance.post('/game/ready', { roomId })
}

// 开始游戏
export const startGame = (roomId) => {
  return axiosInstance.post('/game/start', { roomId })
}

// 获取游戏状态
export const getGameState = (roomId) => {
  return axiosInstance.get(`/game/${roomId}/state`)
}

// 创建房间
export const createRoom = (request) => {
  return axiosInstance.post('/new-game', request)
}

// 获取房间列表
export const getRooms = () => {
  return axiosInstance.get('/rooms').catch(error => {
    console.error('获取房间列表失败:', error)
    // 网络错误时返回空列表，便于调用方统一处理空状态
    return { data: [] }
  })
}

// 获取用户当前所在房间
export const getCurrentRoom = () => {
  return axiosInstance.get('/room/current')
}

// ========== 快速匹配模块 ==========

// 加入匹配队列
export const joinMatch = () => {
  return axiosInstance.post('/match/join')
}

// 取消匹配
export const cancelMatch = () => {
  return axiosInstance.post('/match/cancel')
}

// 查询匹配状态
export const getMatchStatus = () => {
  return axiosInstance.post('/match/status')
}

// 查询匹配结果（获取房间号）
export const getMatchResult = () => {
  return axiosInstance.post('/match/result')
}

// ========== 玩家信息模块 ==========

// 获取玩家统计信息
export const getPlayerStatistics = () => {
  return axiosInstance.get('/player/statistics')
}

// 获取玩家战绩记录
export const getPlayerRecords = (params) => {
  return axiosInstance.get('/player/records', { params })
}

// ========== 大厅交互模块 ==========

/**
 * 本地存储工具 —— 保存房间状态到 localStorage
 * 提取重复的 localStorage 存取逻辑
 */
const saveRoomState = (roomNo, state) => {
  if (roomNo) {
    localStorage.setItem('currentRoomNo', roomNo)
  }
  if (state) {
    Object.entries(state).forEach(([key, value]) => {
      localStorage.setItem(key, value)
    })
  }
}

const clearRoomState = (keys) => {
  (keys || ['currentRoomNo', 'roomCreatedAt', 'roomJoinedAt']).forEach(key => {
    localStorage.removeItem(key)
  })
}

/**
 * 获取大厅完整数据（房间列表 + 用户状态）
 * 用于优化大厅交互体验，减少前端多次调用的开销
 */
export const getLobbyData = () => {
  return axiosInstance.get('/lobby/data')
}

/**
 * 创建房间并保存状态
 * 封装创建房间逻辑，创建成功后自动保存房间号到本地存储
 * 异常处理：创建失败时不清除本地状态，保留之前有效记录
 */
export const createRoomAndSave = async (request) => {
  try {
    const response = await axiosInstance.post('/new-game', request)
    if (response.data && response.data.roomNo) {
      // 创建成功后保存房间状态到本地存储
      saveRoomState(response.data.roomNo, {
        roomCreatedAt: Date.now().toString()
      })
    }
    return response
  } catch (error) {
    // 创建失败时不清除已有房间状态，保留之前有效记录
    console.error('创建房间失败:', error)
    throw error
  }
}

/**
 * 加入房间并保存状态
 * 封装加入房间逻辑，加入成功后自动保存房间状态
 * 异常处理：加入失败时清除临时存储的加入状态
 */
export const joinRoomAndSave = async (roomNo) => {
  if (!roomNo) {
    return Promise.reject(new Error('房间号不能为空'))
  }
  try {
    const username = sessionStorage.getItem('username') || localStorage.getItem('username')
    if (!username) {
      return Promise.reject(new Error('用户未登录，请先登录'))
    }
    const response = await axiosInstance.post('/room/join', { roomNo, username })
    if (response.data && response.data.roomNo) {
      saveRoomState(response.data.roomNo, {
        roomJoinedAt: Date.now().toString()
      })
    }
    return response
  } catch (error) {
    // 加入失败时清除可能残留的加入状态
    clearRoomState()
    throw error
  }
}
