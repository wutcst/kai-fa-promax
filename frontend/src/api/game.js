import axiosInstance from './axiosInstance'

// 加入房间
export const joinRoom = (roomNo) => {
  const username = sessionStorage.getItem('username') || localStorage.getItem('username')
  return axiosInstance.post('/room/join', { roomNo, username })
}

// 退出房间
export const exitRoom = (roomId) => {
  return axiosInstance.post('/room/exit', { roomId })
}

// 获取房间详细信息
export const getRoomDetail = (roomNo) => {
  return axiosInstance.get(`/room/${roomNo}/detail`)
}

// 解散房间
export const dissolveRoom = (roomNo) => {
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
  return axiosInstance.get('/rooms')
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
 * 获取大厅完整数据（房间列表 + 用户状态）
 * 用于优化大厅交互体验，减少前端多次调用的开销
 */
export const getLobbyData = () => {
  return axiosInstance.get('/lobby/data')
}

/**
 * 创建房间并保存状态
 * 封装创建房间逻辑，创建成功后自动保存房间号到本地存储
 */
export const createRoomAndSave = async (request) => {
  const response = await axiosInstance.post('/new-game', request)
  if (response.data && response.data.roomNo) {
    // 创建成功后保存房间状态到本地存储
    localStorage.setItem('currentRoomNo', response.data.roomNo)
    localStorage.setItem('roomCreatedAt', Date.now().toString())
  }
  return response
}

/**
 * 加入房间并保存状态
 * 封装加入房间逻辑，加入成功后自动保存房间状态
 */
export const joinRoomAndSave = async (roomNo) => {
  const username = sessionStorage.getItem('username') || localStorage.getItem('username')
  const response = await axiosInstance.post('/room/join', { roomNo, username })
  if (response.data && response.data.roomNo) {
    localStorage.setItem('currentRoomNo', response.data.roomNo)
    localStorage.setItem('roomJoinedAt', Date.now().toString())
  }
  return response
}
