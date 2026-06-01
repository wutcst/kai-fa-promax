/**
 * useGameState composable
 * 拆分自 BattleView.vue 的页面状态和请求逻辑
 *
 * 职责边界：
 * - 游戏页面所有响应式状态声明
 * - 房间详情请求逻辑
 * - 用户信息获取
 *
 * 调用方需提供：
 * - route — vue-router 的 route 实例
 */

import { ref, computed } from 'vue'

/**
 * 游戏页面状态管理
 * @param {Object} route vue-router route 对象
 */
export function useGameState(route) {
  // 响应式数据
  const roomId = ref(route.query.roomId || '未知房间')
  const isAIMode = computed(() => route.query.mode === 'ai')
  const isReady = ref(false)
  const playerCount = ref(0)
  const roomPlayers = ref([])
  const currentUserId = ref('')
  const wsConnected = ref(false)
  const wsJoined = ref(false)
  const gameState = ref('prepare')
  const roomCreatorId = ref(null)

  // 玩家ID和位置映射
  const myPlayerId = ref(null)
  const playerPositions = ref({
    '我': null,
    '右对手': null,
    '队友': null,
    '左对手': null
  })

  // 计算属性：获取当前用户名
  const username = computed(() => {
    const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo') || '{}')
    return userInfo.nickname || sessionStorage.getItem('nickname') || localStorage.getItem('nickname') || '未知玩家'
  })

  // 玩家名称映射
  const playerNames = ref({
    '我': username.value,
    '右对手': '右对手',
    '队友': '队友',
    '左对手': '左对手'
  })

  const levelCard = ref({
    rankIndex: null
  })

  const getGameRoomId = () => {
    const id = String(roomId.value || '')
    if (!id) return ''
    return id.startsWith('room_') ? id : `room_${id}`
  }

  /**
   * 获取房间详情
   */
  const fetchRoomDetail = async () => {
    try {
      const getRoomDetail = (await import('../api/game')).getRoomDetail
      const response = await getRoomDetail(roomId.value)
      if (response) {
        if (response.playerCount !== undefined) {
          playerCount.value = response.playerCount
        }
        if (response.players) {
          roomPlayers.value = response.players
        }
        if (response.creatorId !== undefined && response.creatorId !== null) {
          roomCreatorId.value = response.creatorId
        }
      }
    } catch (error) {
      console.error('获取房间详情失败:', error)
    }
  }

  return {
    roomId,
    isAIMode,
    isReady,
    playerCount,
    roomPlayers,
    currentUserId,
    wsConnected,
    wsJoined,
    gameState,
    roomCreatorId,
    myPlayerId,
    playerPositions,
    username,
    playerNames,
    levelCard,
    getGameRoomId,
    fetchRoomDetail,
  }
}
