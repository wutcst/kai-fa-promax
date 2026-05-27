<template>
  <div class="game-container" @mouseup="stopDragging" @mouseleave="stopDragging">
    <!-- 准备阶段（默认显示） -->
    <transition name="fade">
      <div class="prepare-stage" v-if="gameState === 'prepare'">
        <div class="room-info">房间号：{{ roomId }}</div>
        <div class="room-info">当前人数：{{ playerCount }}/4</div>
        <div class="room-info ai-mode-tip" v-if="isAIMode">
          人机房间：房主可单人开始，系统会自动补齐 AI 玩家
        </div>

        <!-- 玩家信息面板 -->
        <div class="players-panel" v-if="roomPlayers.length > 0">
          <div class="panel-title">房间玩家</div>
          <div class="players-list">
            <div class="player-item" v-for="(player, index) in roomPlayers" :key="index">
              <div class="player-avatar">
                <el-avatar :size="50" :src="getPlayerAvatar(player)">
                  {{ getPlayerNickname(player) }}
                </el-avatar>
              </div>
              <div class="player-info">
                <div class="player-name">{{ getPlayerNickname(player) }}</div>
                <div class="player-account">账号：{{ player.username || player.userId }}</div>
                <div class="player-status">
                  <el-tag :type="player.isReady === 1 ? 'success' : 'info'" size="small">
                    {{ player.isReady === 1 ? '已准备' : '未准备' }}
                  </el-tag>
                  <el-tag :type="player.online === 1 ? 'success' : 'warning'" size="small" style="margin-left: 6px">
                    {{ player.online === 1 ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="prepare-btns">
          <!-- 准备就绪按钮 -->
          <el-button
              :type="isReady ? 'info' : 'primary'"
              size="large"
              @click="toggleReady"
          >
            {{ isReady ? '取消准备' : '准备就绪' }}
          </el-button>

          <!-- 开始游戏按钮 - 只有房主可见 -->
          <el-button
              type="success"
              size="large"
              @click="handleStartGame"
              :disabled="!canStartGame"
              v-if="isRoomOwner"
          >
            {{ isAIMode ? '开始人机游戏' : '开始游戏' }}
          </el-button>

          <el-button type="warning" size="large" @click="goBackToLobby">返回大厅</el-button>
        </div>
      </div>
    </transition>

    <!-- 游戏阶段（准备后显示） -->
    <transition name="fade">
      <div class="game-stage" v-if="gameState !== 'prepare'">
        <!-- 左上角级牌显示框 -->
        <div class="level-card-container">
          <div class="level-card-title">当前级牌</div>
          <div class="level-card-display">
            <div class="level-card-text">{{ getLevelCardName() }}</div>
          </div>
        </div>

        <!-- 顶部退出按钮 -->
        <div class="exit-btn-container">
          <el-button type="danger" plain @click="exitAndDissolveRoom">退出房间</el-button>
        </div>

        <!-- 队友信息和牌 -->
        <div class="player-section top">
          <div class="player-info">
            <div class="player-avatar">
              <el-avatar :size="40" :src="getInGameAvatar('队友')">
                {{ getInGameName('队友').slice(0, 1) }}
              </el-avatar>
              <span v-if="getFinishLabel('队友')" class="finish-badge">{{ getFinishLabel('队友') }}</span>
              <div v-if="chatBubbles['队友']" class="chat-bubble">{{ chatBubbles['队友'] }}</div>
            </div>
            <div class="player-details">
              <div class="player-name">{{ getInGameName('队友') }}</div>
              <div class="player-status">剩余 {{ teammateCards.length }} 张</div>
            </div>
          </div>
          <div class="player-cards">
            <div v-for="(card, index) in teammateCards" :key="'teammate-' + index" class="card back"> </div>
          </div>
        </div>

        <!-- 左对手信息和牌 -->
        <div class="player-section left">
          <div class="player-info">
            <div class="player-avatar">
              <el-avatar :size="40" :src="getInGameAvatar('左对手')">
                {{ getInGameName('左对手').slice(0, 1) }}
              </el-avatar>
              <span v-if="getFinishLabel('左对手')" class="finish-badge">{{ getFinishLabel('左对手') }}</span>
              <div v-if="chatBubbles['左对手']" class="chat-bubble">{{ chatBubbles['左对手'] }}</div>
            </div>
            <div class="player-details">
              <div class="player-name">{{ getInGameName('左对手') }}</div>
              <div class="player-status">剩余 {{ leftOpponentCards.length }} 张</div>
            </div>
          </div>
          <div class="player-cards vertical">
            <div v-for="(card, index) in leftOpponentCards" :key="'left-' + index" class="card back"></div>
          </div>
        </div>

        <!-- 右对手信息和牌 -->
        <div class="player-section right">
          <div class="player-info">
            <div class="player-details">
              <div class="player-name">{{ getInGameName('右对手') }}</div>
              <div class="player-status">剩余 {{ rightOpponentCards.length }} 张</div>
            </div>
            <div class="player-avatar">
              <el-avatar :size="40" :src="getInGameAvatar('右对手')">
                {{ getInGameName('右对手').slice(0, 1) }}
              </el-avatar>
              <span v-if="getFinishLabel('右对手')" class="finish-badge">{{ getFinishLabel('右对手') }}</span>
              <div v-if="chatBubbles['右对手']" class="chat-bubble">{{ chatBubbles['右对手'] }}</div>
            </div>
          </div>
          <div class="player-cards vertical">
            <div v-for="(card, index) in rightOpponentCards" :key="'right-' + index" class="card back"></div>
          </div>
        </div>

        <!-- 中央桌子区域 -->
        <div class="desk-center">
          <div class="table-area">
            <div class="desk-slots">
              <div v-for="(cards, player) in deskDisplay" :key="player" :class="['played-slot', getPlayerPosClass(player)]">
                <transition name="fade">
                  <div class="played-cards-group" v-if="cards.length > 0">
                    <div v-for="(card, index) in cards" :key="index">
                      <div v-if="card.type === 'pass'" class="pass-indicator">不要</div>
                      <div v-else class="card small-on-desk">
                        <img :src="getCardImage(card)" class="card-img">
                      </div>
                    </div>
                  </div>
                </transition>
              </div>
            </div>

            <!-- 倒计时和操作按钮 -->
            <div class="action-area">
              <div class="countdown" v-if="currentPlayer === '我'">
                <div class="countdown-circle">{{ countdown }}</div>
              </div>
              <div class="action-buttons" v-show="currentPlayer === '我'">
                <button class="btn btn-pass" @click="pass">不出</button>
                <button class="btn btn-hint" @click="hint">提示</button>
                <button class="btn btn-play" @click="playCards" :disabled="selectedCards.length === 0">出牌</button>
              </div>
              <div class="current-player" v-if="currentPlayer !== '我'">{{ currentPlayer }}的回合</div>
            </div>
          </div>
        </div>

        <!-- 我的信息、牌和快捷文字 -->
        <div class="player-section bottom">
          <div class="my-info-section">
            <div class="player-avatar">
              <el-avatar :size="40" :src="getInGameAvatar('我')">
                {{ getInGameName('我').slice(0, 1) }}
              </el-avatar>
              <span v-if="getFinishLabel('我')" class="finish-badge">{{ getFinishLabel('我') }}</span>
              <div v-if="chatBubbles['我']" class="chat-bubble">{{ chatBubbles['我'] }}</div>
            </div>
            <div class="player-details">
              <div class="player-name">{{ getInGameName('我') }}</div>
              <div class="player-status">剩余 {{ myCards.length }} 张</div>
            </div>
            <div class="quick-texts">
              <div class="quick-texts-title">快捷文字</div>
              <div class="quick-text-buttons">
                <button class="quick-text-btn" @click="sendQuickText('快点出牌')">快点出牌</button>
                <button class="quick-text-btn" @click="sendQuickText('这牌怎么打')">这牌怎么打</button>
                <button class="quick-text-btn" @click="sendQuickText('我走了')">我走了</button>
              </div>
            </div>
          </div>

          <div class="my-hand" :style="handStyle" @mousedown="startDragging">
            <div v-for="(card, index) in myCards" :key="'my-' + index"
                 class="card"
                 :class="{ selected: selectedCards.includes(index), suggested: suggestedCards.includes(index) }"
                 draggable="true"
                 @mousedown.stop="handleCardMousedown(index, $event)"
                 @mouseup.stop="handleCardMouseup"
                 @mouseleave="handleCardMouseup"
                 @mouseenter="handleCardMouseenter(index, $event)"
                 @dragstart="handleDragStart(index, $event)"
                 @dragover.prevent
                 @drop="handleDragDrop(index, $event)">
              <img :src="getCardImage(card)" :alt="getCardName(card)" class="card-img">
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import '../assets/card.css'
import { idToCard, cardsToIds } from '../utils/cardConverter'
import webSocketService, { WS_MESSAGE_TYPES } from '../api/websocket'
import { getRoomDetail, ready, exitRoom } from '../api/game'
import soundManager from '../utils/soundManager'

// 路由实例
const router = useRouter()
const route = useRoute()

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

const handleTableClear = () => {
  try {
    deskDisplay.value = {
      '我': [], '右对手': [], '队友': [], '左对手': []
    }
  } catch (e) {
  }
}

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

const mapLevelIndexToCardRank = (rankIndex) => {
  if (rankIndex === null || rankIndex === undefined) return null
  const idx = Number(rankIndex)
  if (Number.isNaN(idx)) return null
  if (idx >= 0 && idx <= 10) return idx + 3
  if (idx === 11) return 1
  if (idx === 12) return 2
  return null
}

// 计算属性：判断是否为房主
const isRoomOwner = computed(() => {
  if (roomCreatorId.value === null || roomCreatorId.value === undefined) return false
  return String(roomCreatorId.value) === String(currentUserId.value)
})

// 计算属性：判断是否可以开始游戏
const canStartGame = computed(() => {
  if (!isRoomOwner.value) return false
  if (isAIMode.value) {
    return true
  }
  if (!roomPlayers.value || roomPlayers.value.length < 2) return false
  return roomPlayers.value
      .filter(p => String(p.userId) !== String(currentUserId.value))
      .every(p => p.isReady === 1)
})

// 生成模拟手牌（27张）
const generateMockCards = () => {
  const cards = []
  const suits = ['hearts', 'diamonds', 'clubs', 'spades']
  const ranks = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]
  for (let i = 0; i < 27; i++) {
    const suit = suits[Math.floor(Math.random() * suits.length)]
    const rank = ranks[Math.floor(Math.random() * ranks.length)]
    cards.push({ suit, rank })
  }
  const hasSmallJoker = Math.random() > 0.5
  const hasBigJoker = Math.random() > 0.5
  if (hasSmallJoker) cards.push({ suit: 'jokers', rank: 14 })
  if (hasBigJoker) cards.push({ suit: 'jokers', rank: 15 })
  return cards.slice(0, 27)
}

const myCards = ref(generateMockCards())
const teammateCards = ref(Array(27).fill(null))
const leftOpponentCards = ref(Array(27).fill(null))
const rightOpponentCards = ref(Array(27).fill(null))
const selectedCards = ref([])
const suggestedCards = ref([])
const currentPlayer = ref('我')
const isDragging = ref(false)
const dragType = ref(true)
const mouseDownX = ref(0)
const mouseDownY = ref(0)
const deskDisplay = ref({
  '我': [], '右对手': [], '队友': [], '左对手': []
})

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)

const updateWindowWidth = () => {
  if (typeof window !== 'undefined') {
    windowWidth.value = window.innerWidth || 1440
  }
}

const handStyle = computed(() => {
  const count = Math.max(myCards.value.length, 1)
  const width = windowWidth.value || 1440
  const cardWidth = width <= 1024 ? 76 : width <= 1440 ? 88 : 98
  const cardHeight = Math.round(cardWidth * 140 / 98)
  const safeWidth = Math.max(width - 36, cardWidth)
  let step = count <= 1 ? cardWidth : (safeWidth - cardWidth) / (count - 1)
  const minStep = Math.max(20, cardWidth * 0.24)
  const maxStep = cardWidth * 0.60
  step = Math.max(minStep, Math.min(maxStep, step))
  const marginX = (step - cardWidth) / 2
  return {
    '--my-card-width': `${cardWidth}px`,
    '--my-card-height': `${cardHeight}px`,
    '--my-card-margin-x': `${marginX}px`,
    '--my-hand-height': `${cardHeight + 8}px`
  }
})

const finishOrder = ref([])
const finishLabels = ref({})

const getFinishLabel = (position) => {
  return finishLabels.value[position] || ''
}

const updateFinishLabelsFromOrder = () => {
  const labels = {}
  if (finishOrder.value.length >= 1) labels[finishOrder.value[0]] = '头游'
  if (finishOrder.value.length >= 2) labels[finishOrder.value[1]] = '二游'
  if (finishOrder.value.length >= 3) labels[finishOrder.value[2]] = '三游'
  const allPositions = ['我', '右对手', '队友', '左对手']
  const remaining = allPositions.filter(p => !finishOrder.value.includes(p))
  if (finishOrder.value.length >= 3 && remaining.length === 1) {
    labels[remaining[0]] = '末游'
  }
  finishLabels.value = labels
}

const markFinishedIfNeeded = (position) => {
  if (gameState.value !== 'playing') return
  if (!position) return
  if (finishOrder.value.includes(position)) return
  let remaining = null
  if (position === '我') remaining = myCards.value.length
  if (position === '队友') remaining = teammateCards.value.length
  if (position === '左对手') remaining = leftOpponentCards.value.length
  if (position === '右对手') remaining = rightOpponentCards.value.length
  if (remaining !== 0) return
  finishOrder.value.push(position)
  updateFinishLabelsFromOrder()
  if (finishOrder.value.length === 3) {
    gameState.value = 'finished'
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
    ElMessage.success('本局结束')
  }
}

// 倒计时相关
const countdown = ref(30)
let countdownTimer = null
let isAutoPlay = false

// 开始倒计时
const startCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdown.value = 30
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      const isFreePlay = checkIsFreePlay()
      if (isFreePlay) {
        autoPlaySmallestCard()
      } else {
        pass()
      }
    }
  }, 1000)
}

// 检查是否是自由出牌
const checkIsFreePlay = () => {
  const otherPlayers = ['右对手', '队友', '左对手']
  for (const player of otherPlayers) {
    const display = deskDisplay.value[player]
    if (display && display.length > 0 && display[0] && display[0].type !== 'pass') {
      return false
    }
  }
  return true
}

// 自动出最小的牌
const autoPlaySmallestCard = () => {
  if (currentPlayer.value !== '我' || myCards.value.length === 0) {
    return
  }
  isAutoPlay = true
  webSocketService.send(WS_MESSAGE_TYPES.SUGGEST_CARDS, {})
}

// 重置倒计时
const resetCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdown.value = 30
}

// 发送快捷文字
const sendQuickText = (text) => {
  ElMessage.info(`发送了快捷文字：${text}`)
  webSocketService.send(WS_MESSAGE_TYPES.CHAT_MESSAGE, {
    roomId: roomId.value,
    message: text,
    type: 'quick'
  })
}

const getInGameName = (position) => {
  const pid = playerPositions.value?.[position]
  if (!pid) return position
  if (String(pid).includes('ai_player_')) return 'AI玩家'
  return getPlayerNickname(pid)
}

const getInGameAvatar = (position) => {
  const pid = playerPositions.value?.[position]
  if (!pid) return ''
  if (String(pid).includes('ai_player_')) return ''
  return getPlayerAvatar(pid)
}

const chatBubbles = ref({})
const chatBubbleTimers = {}

const showChatBubble = (position, text) => {
  if (!position) return
  if (chatBubbleTimers[position]) {
    clearTimeout(chatBubbleTimers[position])
    chatBubbleTimers[position] = null
  }
  chatBubbles.value = {
    ...chatBubbles.value,
    [position]: text
  }
  chatBubbleTimers[position] = setTimeout(() => {
    const next = { ...chatBubbles.value }
    delete next[position]
    chatBubbles.value = next
    chatBubbleTimers[position] = null
  }, 3000)
}

const handleChatMessage = (data) => {
  if (!data) return
  const pid = data.playerId
  const msg = data.message
  if (!pid || !msg) return
  const position = Object.keys(playerPositions.value).find(
      pos => String(playerPositions.value[pos]) === String(pid)
  )
  if (!position) return
  showChatBubble(position, msg)
}

// 页面挂载时校验登录状态并初始化WebSocket
onMounted(() => {
  updateWindowWidth()
  window.addEventListener('resize', updateWindowWidth)

  const isLogin = sessionStorage.getItem('isLogin') || localStorage.getItem('isLogin')
  if (!isLogin) {
    ElMessage.warning('请先登录后再进入游戏！')
    router.push('/login')
    return
  }

  const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo') || '{}')
  currentUserId.value = String(userInfo.id || sessionStorage.getItem('userId') || localStorage.getItem('userId') || '')

  if (!currentUserId.value || currentUserId.value === 'undefined' || currentUserId.value === 'null') {
    ElMessage.error('无法获取用户信息，请重新登录')
    router.push('/login')
    return
  }

  fetchRoomDetail().then(() => {
    connectWebSocket()
  })

  webSocketService.on(WS_MESSAGE_TYPES.GAME_START, handleGameStart)
  webSocketService.on(WS_MESSAGE_TYPES.PLAYER_ACTION, handlePlayerAction)
  webSocketService.on(WS_MESSAGE_TYPES.TURN_CHANGE, handleTurnChange)
  webSocketService.on(WS_MESSAGE_TYPES.SUGGEST_CARDS_SUCCESS, handleSuggestCardsSuccess)
  webSocketService.on(WS_MESSAGE_TYPES.ERROR, handleError)
  webSocketService.on(WS_MESSAGE_TYPES.ROOM_UPDATE, handleRoomUpdate)
  webSocketService.on(WS_MESSAGE_TYPES.CHAT_MESSAGE, handleChatMessage)
  webSocketService.on(WS_MESSAGE_TYPES.GAME_END, handleGameEnd)
  webSocketService.on(WS_MESSAGE_TYPES.TABLE_CLEAR, handleTableClear)

  webSocketService.on('connect', () => {
    wsConnected.value = true
  })

  webSocketService.on('disconnect', () => {
    wsConnected.value = false
    wsJoined.value = false
  })

  webSocketService.on(WS_MESSAGE_TYPES.JOIN_ROOM_SUCCESS, () => {
    wsJoined.value = true
  })
})

const connectWebSocket = () => {
  try {
    wsConnected.value = false
    wsJoined.value = false
    webSocketService.connect(currentUserId.value, getGameRoomId())
  } catch (error) {
    console.error('WebSocket连接失败:', error)
    ElMessage.error('网络连接失败，请检查网络状态')
  }
}

// 获取房间详情
const fetchRoomDetail = async () => {
  try {
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

// 获取玩家昵称
const getPlayerNickname = (userId) => {
  if (userId && typeof userId === 'object') {
    if (userId.nickname) return userId.nickname
    if (userId.userId !== undefined && userId.userId !== null) {
      return getPlayerNickname(userId.userId)
    }
  }

  const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo') || '{}')
  if (userInfo.id == userId) {
    return userInfo.nickname || sessionStorage.getItem('nickname') || localStorage.getItem('nickname') || '未知玩家'
  }
  const matched = roomPlayers.value.find(p => String(p.userId) === String(userId))
  return matched?.nickname || matched?.username || `玩家${userId}`
}

// 获取玩家头像
const getPlayerAvatar = (userId) => {
  if (userId && typeof userId === 'object') {
    if (userId.avatar) return userId.avatar
    if (userId.userId !== undefined && userId.userId !== null) {
      return getPlayerAvatar(userId.userId)
    }
  }

  const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo') || '{}')
  if (userInfo.id == userId) {
    return userInfo.avatar || ''
  }
  const matched = roomPlayers.value.find(p => String(p.userId) === String(userId))
  return matched?.avatar || ''
}

// 生成新的级牌
const generateNewLevelCard = () => {
  levelCard.value.rankIndex = 0
  return getLevelCardName()
}

// 切换准备状态
const toggleReady = () => {
  if (!wsConnected.value) {
    ElMessage.info('网络连接中，请稍后再操作')
    return
  }
  if (!wsJoined.value) {
    ElMessage.info('正在加入房间，请稍后...')
    return
  }
  ready(roomId.value)
      .then(() => {
        isReady.value = !isReady.value
        ElMessage.success(isReady.value ? '已准备' : '已取消准备')
      })
      .catch((e) => {
        ElMessage.error(e?.message || '操作失败')
      })
}

// 房主开始游戏
const handleStartGame = () => {
  if (!isRoomOwner.value) {
    ElMessage.warning('只有房主可以开始游戏')
    return
  }
  if (!canStartGame.value) {
    const unreadyPlayers = roomPlayers.value
        .filter(p => String(p.userId) !== String(currentUserId.value))
        .filter(p => p.isReady !== 1)
        .map(p => getPlayerNickname(p))
        .join('、')
    if (unreadyPlayers) {
      ElMessage.warning(`以下玩家还未准备：${unreadyPlayers}，无法开始游戏`)
    } else {
      ElMessage.warning('等待更多玩家加入或准备')
    }
    return
  }
  if (!wsConnected.value) {
    ElMessage.info('网络连接中，请稍后...')
    return
  }
  ElMessage.success(isAIMode.value ? '人机游戏即将开始...' : '游戏即将开始...')
  webSocketService.send(WS_MESSAGE_TYPES.START_GAME, {
    roomId: roomId.value,
    aiMode: isAIMode.value
  })
}

// 返回大厅
const goBackToLobby = () => {
  ElMessageBox.confirm(
      '确定要返回大厅吗？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(() => {
    tryExitRoomAndDisconnect()
    router.push('/lobby')
  })
}

// 退出并解散房间
const exitAndDissolveRoom = () => {
  ElMessageBox.confirm(
      '确定要退出房间吗？',
      '警告',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'error' }
  ).then(() => {
    tryExitRoomAndDisconnect()
    router.push('/lobby')
  })
}

const tryExitRoomAndDisconnect = () => {
  try {
    exitRoom(getGameRoomId())
  } catch (e) {
  }
  try {
    webSocketService.disconnect()
  } catch (e) {
  }
}

const handleBeforeUnload = () => {
  tryExitRoomAndDisconnect()
}

window.addEventListener('beforeunload', handleBeforeUnload)

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateWindowWidth)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  try {
    webSocketService.off(WS_MESSAGE_TYPES.CHAT_MESSAGE, handleChatMessage)
  } catch (e) {
  }
  try {
    webSocketService.off(WS_MESSAGE_TYPES.TABLE_CLEAR, handleTableClear)
  } catch (e) {
  }
  try {
    Object.keys(chatBubbleTimers).forEach((k) => {
      if (chatBubbleTimers[k]) {
        clearTimeout(chatBubbleTimers[k])
        chatBubbleTimers[k] = null
      }
    })
  } catch (e) {
  }
  chatBubbles.value = {}
  tryExitRoomAndDisconnect()
})

// 获取卡牌图片路径
const getCardImage = (card) => {
  if (!card) return ''
  try {
    if (card.suit === 'jokers') {
      const jokerType = card.rank === 14 ? '小' : '大'
      return new URL(`../assets/joker/${jokerType}.png`, import.meta.url).href
    }
    const suitMap = {
      hearts: 'hearts',
      diamonds: 'diamonds',
      clubs: 'clubs',
      spades: 'spades'
    }
    const suitName = suitMap[card.suit]
    if (!suitName) {
      console.error('未知花色:', card.suit)
      return ''
    }
    const rank = card.rank
    if (rank < 1 || rank > 15) {
      console.error('无效点数:', rank)
      return ''
    }
    return new URL(`../assets/${suitName}/${rank}.png`, import.meta.url).href
  } catch (error) {
    console.error('获取卡牌图片失败:', error, '卡牌信息:', card)
    return ''
  }
}

// 获取级牌图片
const getLevelCardImage = () => {
  try {
    const cardRank = mapLevelIndexToCardRank(levelCard.value.rankIndex)
    if (cardRank === null) return ''
    return new URL(`../assets/spades/${cardRank}.png`, import.meta.url).href
  } catch (error) {
    console.error('获取级牌图片失败:', error)
    return ''
  }
}

// 获取级牌名称
const getLevelCardName = () => {
  const idx = levelCard.value.rankIndex
  if (idx === null || idx === undefined) return ''
  const rankMap = {
    0: '2', 1: '3', 2: '4', 3: '5', 4: '6', 5: '7', 6: '8', 7: '9',
    8: '10', 9: 'J', 10: 'Q', 11: 'K', 12: 'A'
  }
  return rankMap[idx] || ''
}

// 获取卡牌名称
const getCardName = (card) => {
  if (!card) return ''
  if (card.suit === 'jokers') {
    return card.rank === 14 ? '小王' : '大王'
  }
  const rankMap = { 1: 'A', 2: '2', 3: '3', 4: '4', 5: '5', 6: '6', 7: '7', 8: '8', 9: '9', 10: '10', 11: 'J', 12: 'Q', 13: 'K' }
  const suitMap = { hearts: '红桃', diamonds: '方块', clubs: '梅花', spades: '黑桃' }
  return `${suitMap[card.suit]}${rankMap[card.rank]}`
}

const sortCards = () => {
  const suitPriority = { spades: 4, hearts: 3, clubs: 2, diamonds: 1, jokers: 0 }
  const order = [15, 14, 2, 1, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3]
  const rankOrderValue = (rank) => {
    const idx = order.indexOf(Number(rank))
    return idx === -1 ? order.length : idx
  }
  myCards.value = [...myCards.value].sort((a, b) => {
    const ra = rankOrderValue(a.rank)
    const rb = rankOrderValue(b.rank)
    if (ra !== rb) return ra - rb
    return (suitPriority[b.suit] || 0) - (suitPriority[a.suit] || 0)
  })
  selectedCards.value = []
  suggestedCards.value = []
}

// 拖拽相关方法
const startDragging = () => { isDragging.value = true }
const stopDragging = () => { isDragging.value = false }
const handleCardMousedown = (index, event) => {
  if (currentPlayer.value !== '我') return
  mouseDownX.value = event.clientX
  mouseDownY.value = event.clientY
  isDragging.value = true
  dragType.value = !selectedCards.value.includes(index)
  toggleCardLogic(index)
}
const handleCardMouseenter = (index, event) => {
  if (!isDragging.value || currentPlayer.value !== '我') return
  if (Math.abs(event.clientX - mouseDownX.value) < 5 && Math.abs(event.clientY - mouseDownY.value) < 5) {
    return
  }
  const isIncluded = selectedCards.value.includes(index)
  if (dragType.value && !isIncluded) {
    selectedCards.value.push(index)
  } else if (!dragType.value && isIncluded) {
    selectedCards.value = selectedCards.value.filter(i => i !== index)
  }
}
const handleCardMouseup = () => {
  stopDragging()
}
const toggleCardLogic = (index) => {
  if (suggestedCards.value.length > 0) {
    suggestedCards.value = []
  }
  if (selectedCards.value.includes(index)) {
    selectedCards.value = selectedCards.value.filter(i => i !== index)
  } else {
    selectedCards.value.push(index)
  }
}
const toggleCard = (index) => {
  if (currentPlayer.value !== '我') {
    ElMessage.info('现在不是你的回合！')
    return
  }
}

// 卡牌拖拽排序功能
const draggedCardIndex = ref(null)
const handleDragStart = (index, event) => {
  draggedCardIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
}
const handleDragDrop = (targetIndex, event) => {
  event.preventDefault()
  if (draggedCardIndex.value === null || draggedCardIndex.value === targetIndex) return
  const newCards = [...myCards.value]
  const [draggedCard] = newCards.splice(draggedCardIndex.value, 1)
  newCards.splice(targetIndex, 0, draggedCard)
  myCards.value = newCards
  draggedCardIndex.value = null
}

// 出牌逻辑
const playCards = () => {
  if (selectedCards.value.length === 0 || currentPlayer.value !== '我') return
  try {
    const selectedCardObjects = selectedCards.value.map(index => {
      const card = myCards.value[index]
      return card
    })
    const cardIds = cardsToIds(selectedCardObjects)
    webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, {
      cards: cardIds
    })
    soundManager.play('play_card')
    selectedCards.value = []
  } catch (error) {
    console.error('出牌失败:', error)
    ElMessage.error('出牌失败，请重试')
  }
}

// 检查是否为顺子（点数连续）
const isStraightHand = (cards) => {
  if (cards.length < 5) return false
  const validCards = cards.filter(card => card.rank < 14)
  if (validCards.length !== cards.length) return false
  const ranks = validCards.map(card => card.rank).sort((a, b) => a - b)
  for (let i = 1; i < ranks.length; i++) {
    if (ranks[i] !== ranks[i - 1] + 1) {
      return false
    }
  }
  return true
}

// 不出
const pass = () => {
  if (currentPlayer.value !== '我') {
    ElMessage.info('现在不是你的回合！')
    return
  }
  webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, {
    cards: []
  })
}

// 提示
const hint = () => {
  if (currentPlayer.value !== '我') {
    ElMessage.info('现在不是你的回合！')
    return
  }
  suggestedCards.value = []
  webSocketService.send(WS_MESSAGE_TYPES.SUGGEST_CARDS, {})
}

// 处理出牌提示成功响应
const handleSuggestCardsSuccess = (data) => {
  if (data.message) {
    if (data.message === '没有合适的牌可出') {
      ElMessage.info('没有合适的牌可出，请选择不出')
      suggestedCards.value = []
      if (isAutoPlay) {
        isAutoPlay = false
        pass()
      }
      return
    }
  }
  if (data.cards && data.cards.length > 0) {
    const suggestedCardObjects = data.cards.map(id => idToCard(id))
    const suggestedIndices = []
    suggestedCardObjects.forEach(suggestedCard => {
      const index = myCards.value.findIndex(card =>
          card.suit === suggestedCard.suit && card.rank === suggestedCard.rank &&
          ((card.deck !== undefined && card.deck !== null && suggestedCard.deck !== undefined && suggestedCard.deck !== null)
              ? card.deck === suggestedCard.deck
              : true)
      )
      if (index !== -1) {
        suggestedIndices.push(index)
      }
    })
    selectedCards.value = []
    suggestedCards.value = suggestedIndices
    if (isAutoPlay) {
      isAutoPlay = false
      const cardIds = data.cards
      webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, {
        cards: cardIds
      })
      selectedCards.value = []
      ElMessage.info('自动出牌：' + suggestedCardObjects.map(card => getCardName(card)).join(', '))
    } else {
      selectedCards.value = [...suggestedIndices]
      const cardNames = suggestedCardObjects.map(card => getCardName(card)).join(', ')
      const cardType = data.cardType || ''
      ElMessage.success(`建议出牌：${cardType} (${cardNames})`)
    }
  }
}

// 处理回合更新消息
const handleTurnChange = (data) => {
  if (data) {
    const isMyTurn = data.myTurn !== undefined ? data.myTurn : data.isMyTurn
    if (isMyTurn) {
      currentPlayer.value = '我'
      ElMessage.info('轮到你出牌了！')
      startCountdown()
      return
    }
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
    if (data.currentPlayerId) {
      const position = Object.keys(playerPositions.value).find(
          pos => String(playerPositions.value[pos]) === String(data.currentPlayerId)
      )
      if (position) {
        currentPlayer.value = position
        ElMessage.info(`轮到 ${position} 出牌`)
      } else {
        const name = getPlayerNickname(data.currentPlayerId)
        currentPlayer.value = name
        ElMessage.info(`轮到 ${name} 出牌`)
      }
    }
  }
}

// 处理游戏开始消息
const handleGameStart = (data) => {
  try {
    if (!data) {
      return
    }
    if (!data.myCards || !Array.isArray(data.myCards) || data.myCards.length === 0) {
      return
    }
    gameState.value = 'playing'
    finishOrder.value = []
    finishLabels.value = {}
    if (data.playerId) {
      myPlayerId.value = data.playerId
    }
    if (data.playerPositions) {
      playerPositions.value = data.playerPositions
      Object.keys(playerPositions.value).forEach(position => {
        const playerId = playerPositions.value[position]
        if (playerId) {
          if (playerId.includes('ai_player_')) {
            playerNames.value[position] = 'AI玩家'
          } else if (position === '我') {
            playerNames.value[position] = username.value
          } else {
            playerNames.value[position] = '玩家'
          }
        }
      })
    } else {
      const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo') || '{}')
      const userId = userInfo.id || sessionStorage.getItem('userId') || localStorage.getItem('userId') || ''
      playerPositions.value = {
        '我': userId,
        '右对手': null,
        '队友': null,
        '左对手': null
      }
    }
    if (data.levelCard !== undefined && data.levelCard !== null) {
      levelCard.value.rankIndex = data.levelCard
    }
    let levelCardName = ''
    levelCardName = getLevelCardName()
    const handCards = data.myCards.map(id => {
      try {
        const card = idToCard(id)
        return card
      } catch (error) {
        return null
      }
    }).filter(card => card !== null)
    if (handCards.length !== 27) {
      ElMessage.warning(`手牌数量异常: ${handCards.length} 张，应为27张`)
    }
    myCards.value = handCards
    sortCards()
    teammateCards.value = Array(27).fill(null)
    leftOpponentCards.value = Array(27).fill(null)
    rightOpponentCards.value = Array(27).fill(null)
    if (!levelCardName) {
      levelCardName = getLevelCardName()
    }
    soundManager.play('game_start')
    ElMessage.success(`游戏开始！当前级牌：${levelCardName}，已为您自动理牌`)
  } catch (error) {
    console.error('处理游戏开始消息失败:', error)
    ElMessage.error('游戏开始处理失败，请重试')
  }
}

const checkWin = () => {
  markFinishedIfNeeded('我')
}

// 处理玩家行动消息
const handlePlayerAction = (data) => {
  if (!data) return
  const { playerId, cards } = data
  const position = Object.keys(playerPositions.value).find(
      pos => String(playerPositions.value[pos]) === String(playerId)
  )
  if (!position) {
    return
  }
  if (cards && cards.length > 0) {
    if (position === '队友') {
      deskDisplay.value['队友'] = cards.map(id => idToCard(id))
      const nextLen = Math.max(0, teammateCards.value.length - cards.length)
      teammateCards.value = Array(nextLen).fill(null)
      markFinishedIfNeeded('队友')
    } else if (position === '左对手') {
      deskDisplay.value['左对手'] = cards.map(id => idToCard(id))
      const nextLen = Math.max(0, leftOpponentCards.value.length - cards.length)
      leftOpponentCards.value = Array(nextLen).fill(null)
      markFinishedIfNeeded('左对手')
    } else if (position === '右对手') {
      deskDisplay.value['右对手'] = cards.map(id => idToCard(id))
      const nextLen = Math.max(0, rightOpponentCards.value.length - cards.length)
      rightOpponentCards.value = Array(nextLen).fill(null)
      markFinishedIfNeeded('右对手')
    } else if (position === '我') {
      const cardIds = cards
      const cardsToPlay = cardIds.map(id => idToCard(id))
      if (cardsToPlay.length >= 5 && isStraightHand(cardsToPlay)) {
        cardsToPlay.sort((a, b) => {
          if (a.rank !== b.rank) {
            return a.rank - b.rank
          }
          const suitPriority = { spades: 4, hearts: 3, clubs: 2, diamonds: 1, jokers: 0 }
          return (suitPriority[b.suit] || 0) - (suitPriority[a.suit] || 0)
        })
      }
      deskDisplay.value['我'] = cardsToPlay
      myCards.value = myCards.value.filter(card => {
        const cardId = cardsToIds([card])[0]
        return !cardIds.includes(cardId)
      })
      checkWin()
      if (position === '我') {
        currentPlayer.value = null
        if (countdownTimer) {
          clearInterval(countdownTimer)
          countdownTimer = null
        }
      }
    }
  } else {
    if (position === '队友') {
      deskDisplay.value['队友'] = [{ type: 'pass' }]
    } else if (position === '左对手') {
      deskDisplay.value['左对手'] = [{ type: 'pass' }]
    } else if (position === '右对手') {
      deskDisplay.value['右对手'] = [{ type: 'pass' }]
    } else if (position === '我') {
      deskDisplay.value['我'] = [{ type: 'pass' }]
      currentPlayer.value = null
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }
}

// 处理房间更新消息
const handleRoomUpdate = (data) => {
  if (!data) return
  if (data.playerCount !== undefined) {
    playerCount.value = data.playerCount
  }
  if (data.players) {
    roomPlayers.value = data.players
  }
  if (data.creatorId !== undefined && data.creatorId !== null) {
    roomCreatorId.value = data.creatorId
  } else {
    if (roomCreatorId.value !== null && roomCreatorId.value !== undefined) {
      const exists = roomPlayers.value.some(p => String(p.userId) === String(roomCreatorId.value))
      if (!exists && roomPlayers.value.length > 0) {
        const idx = Math.floor(Math.random() * roomPlayers.value.length)
        roomCreatorId.value = roomPlayers.value[idx]?.userId ?? roomCreatorId.value
      }
    }
  }
}

// 处理游戏结束消息
const handleGameEnd = (data) => {
  if (!data) return
  const winnerId = data.winnerId
  const score = data.score
  const levelTeamA = data.levelTeamA
  const levelTeamB = data.levelTeamB
  const isWinner = String(winnerId) === String(currentUserId.value)
  const myPosition = Object.keys(playerPositions.value).find(
    pos => String(playerPositions.value[pos]) === String(currentUserId.value)
  )
  const isTeamA = myPosition === '我' || myPosition === '右对手'
  const myTeamLevel = isTeamA ? levelTeamA : levelTeamB
  const otherTeamLevel = isTeamA ? levelTeamB : levelTeamA
  const getLevelName = (level) => {
    if (level === null || level === undefined) return ''
    const rankMap = {
      0: '2', 1: '3', 2: '4', 3: '5', 4: '6', 5: '7', 6: '8', 7: '9',
      8: '10', 9: 'J', 10: 'Q', 11: 'K', 12: 'A'
    }
    return rankMap[level] || level
  }
  if (isWinner) {
    soundManager.play('victory')
    let message = `恭喜你！游戏结束，你获得了胜利！得分：${score}`
    if (myTeamLevel) {
      message += `\n你的队伍升级到 ${getLevelName(myTeamLevel)} 级！`
    }
    ElMessage.success(message)
  } else {
    soundManager.play('defeat')
    let message = `游戏结束！玩家 ${winnerId} 获得了胜利。得分：${score}`
    if (myTeamLevel) {
      message += `\n你的队伍当前等级：${getLevelName(myTeamLevel)}`
    }
    ElMessage.info(message)
  }
}

// 获取玩家位置样式
const getPlayerPosClass = (player) => {
  const map = { '我': 'pos-bottom', '右对手': 'pos-right', '队友': 'pos-top', '左对手': 'pos-left' }
  return map[player]
}

// 处理错误消息
const handleError = (data) => {
  if (!data) return
  if (data.message && data.message.includes('出牌')) {
    ElMessage.error(data.message)
  } else if (data.message) {
    ElMessage.error(data.message)
  }
}
</script>

<style scoped>
/* 字体：统一使用ChillRoundGothic字体 */
@font-face {
  font-family: "ChillRoundGothic";
  src:
      url('../assets/fonts/ChillRoundGothic_Heavy.ttf') format('truetype'),
      local('Microsoft YaHei'),
      local('SimHei'),
      local('sans-serif');
  font-weight: normal;
  font-style: normal;
  font-display: swap;
}

@font-face {
  font-family: "ChillRoundGothic";
  src:
      url('../assets/fonts/ChillRoundGothic_Heavy.ttf') format('truetype'),
      local('Microsoft YaHei Bold'),
      local('SimHei Bold'),
      local('sans-serif');
  font-weight: bold;
  font-style: normal;
  font-display: swap;
}

/* 全局字体应用 */
*:not([class*="el-icon-"]):not(svg):not(path):not(rect):not(text) {
  font-family: "ChillRoundGothic", sans-serif !important;
}

/* 过渡动画 */
.fade-enter-from, .fade-leave-to { opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.slide-fade-enter-from { transform: translateY(30px); opacity: 0; }
.slide-fade-enter-active { transition: all 0.3s ease; }

.game-container {
  position: relative;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background:
      linear-gradient(rgba(44, 14, 0, 0.6), rgba(44, 14, 0, 0.6)),
      #d6ccc2 radial-gradient(circle, #e3d5ca 0%, #d5bdaf 100%);
  background-image:
      url('../assets/images/bg.jpg'),
      linear-gradient(rgba(44, 14, 0, 0.6), rgba(44, 14, 0, 0.6)),
      radial-gradient(circle, #e3d5ca 0%, #d5bdaf 100%);
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  background-blend-mode: overlay;
}
/* 中央桌子区域 */
.table-area {
  background-color: hsl(44, 83%, 93%);
  border-radius: 20px;
  padding: 20px;
  border: 3px solid hsla(19, 100%, 9%, 0.6);
  width: 60%;
  min-height: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) rotateX(25deg) rotateY(0deg) scale(0.95);
  transform-origin: center center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

/* 桌子出牌槽位 */
.desk-slots {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
  gap: 20px;
  width: 100%;
  height: 200px;
}

/* 出牌位置 */
.played-slot {
  display: flex;
  justify-content: center;
  align-items: center;
}

.played-slot.pos-top {
  grid-column: 2;
  grid-row: 1;
}

.played-slot.pos-left {
  grid-column: 1;
  grid-row: 2;
}

.played-slot.pos-right {
  grid-column: 3;
  grid-row: 2;
}

.played-slot.pos-bottom {
  grid-column: 2;
  grid-row: 3;
}

/* 出牌组 */
.played-cards-group {
  display: flex;
  gap: -10px;
}
.played-slot.pos-bottom .played-cards-group {
  transform: translateY(80px);
}

/* 小牌样式 */
.card.small-on-desk {
  width: 50px;
  height: 70px;
}

/* 准备阶段样式 */
.prepare-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
  background: linear-gradient(to bottom, #f5e8d3, #e8d4b8);
  padding: 50px 80px;
  border-radius: 15px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}
.room-info {
  font-size: 20px;
  color: #2c3e50;
  font-weight: bold;
}
.ai-mode-tip {
  font-size: 16px;
  color: #7D4E2F;
  background: rgba(232, 218, 197, 0.78);
  border: 2px solid #915C39;
  border-radius: 12px;
  padding: 10px 18px;
  box-shadow:
      0 4px 0 #8C6B48,
      0 6px 12px rgba(0, 0, 0, 0.16),
      inset 0 1px 0 rgba(255, 255, 255, 0.55);
  text-shadow: 1px 1px 1px rgba(255,255,255,0.8);
}
.prepare-btns {
  display: flex;
  gap: 20px;
}

/* 玩家信息面板样式 */
.players-panel {
  background: rgba(232, 218, 197, 0.6);
  border-radius: 15px;
  padding: 20px;
  margin-top: 20px;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
}

.panel-title {
  font-size: 18px;
  font-weight: bold;
  color: #2c3e50;
  margin-bottom: 15px;
  text-align: center;
}

.players-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.player-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 10px;
  transition: all 0.3s;
}

.player-item:hover {
  background: #e9ecef;
  transform: translateX(5px);
}

.player-avatar {
  flex-shrink: 0;
}

.player-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.player-name {
  font-size: 16px;
  font-weight: bold;
  color: #2c3e50;
}

.player-account {
  font-size: 14px;
  color: #606266;
}

.player-status {
  align-self: flex-start;
}

/* 游戏阶段样式 */
.exit-btn-container {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
}

/* 级牌显示框样式 */
.level-card-container {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 10;
  background-color: rgba(255, 255, 255, 0.9);
  border-radius: 10px;
  padding: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.level-card-title {
  font-size: 14px;
  font-weight: bold;
  margin-bottom: 5px;
  color: #333;
}

.level-card-display {
  padding: 5px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.level-card-display .card {
  width: 80px;
  height: 115px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.level-card-text {
  width: 60px;
  height: 85px;
  background: linear-gradient(135deg, #fff 0%, #f5f5f5 100%);
  border-radius: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 32px;
  font-weight: bold;
  color: #333;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  border: 2px solid #e0e0e0;
}

/* 玩家区域布局 */
.player-section {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 5px;
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  backdrop-filter: blur(5px);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

/* 队友（顶部） */
.player-section.top {
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
}

/* 左对手（左侧） */
.player-section.left {
  top: 50%;
  left: 10px;
  transform: translateY(-50%);
  width: 120px;
}

/* 右对手（右侧） */
.player-section.right {
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  width: 120px;
}

/* 我（底部） */
.player-section.bottom {
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  width: 80%;
  max-height: 180px;
}

/* 玩家信息样式 */
.player-info {
  display: flex;
  align-items: center;
  margin-bottom: 5px;
  background-color: rgba(255,255,255,0.2);
  padding: 3px 8px;
  border-radius: 10px;
  width: 100%;
  justify-content: center;
}

/* 右侧玩家信息（右对齐） */
.player-section.right .player-info {
  flex-direction: row-reverse;
}

/* 头像样式 */
.player-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: hsla(19, 100%, 9%, 0.6);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  margin: 0 10px;
  border: 2px solid hsla(19, 100%, 9%, 0.6);
}

.finish-badge {
  position: absolute;
  top: -10px;
  right: -12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e74c3c;
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  line-height: 18px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
  border: 2px solid rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}

.chat-bubble {
  position: absolute;
  bottom: 46px;
  left: 50%;
  transform: translateX(-50%);
  max-width: 180px;
  padding: 6px 10px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  z-index: 120;
}

.chat-bubble::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -6px;
  transform: translateX(-50%);
  border-width: 6px 6px 0 6px;
  border-style: solid;
  border-color: rgba(0, 0, 0, 0.75) transparent transparent transparent;
}

/* 玩家详情 */
.player-details {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

/* 右侧玩家详情（左对齐） */
.player-section.right .player-details {
  align-items: center;
}

/* 玩家名称和状态 */
.player-name {
  font-weight: bold;
  font-size: 14px;
  color: hsla(19, 100%, 9%, 0.6);
  margin-bottom: 2px;
}

.player-status {
  font-size: 12px;
  color: hsla(19, 100%, 9%, 0.6);
}

/* 玩家卡牌区域 */
.player-cards {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap;
  height: 120px;
  overflow: hidden;
}

/* 顶部队友卡牌定位和折叠 */
.player-section.top .player-cards {
  position: relative;
  height: 110px;
  width: 600px;
}

.player-section.top .player-cards .card {
  position: absolute;
  transition: all 0.3s ease;
}

/* 为27张牌设置水平位置和z-index，居中显示 */
.player-section.top .player-cards .card:nth-child(1) { transform: translateX(-240px); z-index: 1; }
.player-section.top .player-cards .card:nth-child(2) { transform: translateX(-220px); z-index: 2; }
.player-section.top .player-cards .card:nth-child(3) { transform: translateX(-200px); z-index: 3; }
.player-section.top .player-cards .card:nth-child(4) { transform: translateX(-180px); z-index: 4; }
.player-section.top .player-cards .card:nth-child(5) { transform: translateX(-160px); z-index: 5; }
.player-section.top .player-cards .card:nth-child(6) { transform: translateX(-140px); z-index: 6; }
.player-section.top .player-cards .card:nth-child(7) { transform: translateX(-120px); z-index: 7; }
.player-section.top .player-cards .card:nth-child(8) { transform: translateX(-100px); z-index: 8; }
.player-section.top .player-cards .card:nth-child(9) { transform: translateX(-80px); z-index: 9; }
.player-section.top .player-cards .card:nth-child(10) { transform: translateX(-60px); z-index: 10; }
.player-section.top .player-cards .card:nth-child(11) { transform: translateX(-40px); z-index: 11; }
.player-section.top .player-cards .card:nth-child(12) { transform: translateX(-20px); z-index: 12; }
.player-section.top .player-cards .card:nth-child(13) { transform: translateX(0); z-index: 13; }
.player-section.top .player-cards .card:nth-child(14) { transform: translateX(20px); z-index: 14; }
.player-section.top .player-cards .card:nth-child(15) { transform: translateX(40px); z-index: 15; }
.player-section.top .player-cards .card:nth-child(16) { transform: translateX(60px); z-index: 16; }
.player-section.top .player-cards .card:nth-child(17) { transform: translateX(80px); z-index: 17; }
.player-section.top .player-cards .card:nth-child(18) { transform: translateX(100px); z-index: 18; }
.player-section.top .player-cards .card:nth-child(19) { transform: translateX(120px); z-index: 19; }
.player-section.top .player-cards .card:nth-child(20) { transform: translateX(140px); z-index: 20; }
.player-section.top .player-cards .card:nth-child(21) { transform: translateX(160px); z-index: 21; }
.player-section.top .player-cards .card:nth-child(22) { transform: translateX(180px); z-index: 22; }
.player-section.top .player-cards .card:nth-child(23) { transform: translateX(200px); z-index: 23; }
.player-section.top .player-cards .card:nth-child(24) { transform: translateX(220px); z-index: 24; }
.player-section.top .player-cards .card:nth-child(25) { transform: translateX(240px); z-index: 25; }
.player-section.top .player-cards .card:nth-child(26) { transform: translateX(260px); z-index: 26; }
.player-section.top .player-cards .card:nth-child(27) { transform: translateX(280px); z-index: 27; }

/* 垂直排列的卡牌（左右对手） */
.player-cards.vertical {
  flex-direction: column;
  height: 300px;
  gap: -10px;
}

/* 垂直排列的卡牌间距 */
.player-cards.vertical .card {
  margin: -100px 0;
}

/* 卡牌样式 */
.card {
  width: 70px;
  height: 100px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  transition: all 0.2s ease;
  margin: 0 -5px;
}

.card.back {
  background: url('../assets/back.jpg');
  background-repeat: no-repeat;
  background-size: cover;
  background-position: center;
}

.card.selected {
  transform: translateY(-10px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
  z-index: 100;
}

.card.suggested {
  animation: pulse 1.5s ease-in-out infinite;
  box-shadow: 0 0 15px rgba(255, 215, 0, 0.8);
  z-index: 99;
}

@keyframes pulse {
  0%, 100% {
    transform: translateY(-5px);
    box-shadow: 0 0 15px rgba(255, 215, 0, 0.8);
  }
  50% {
    transform: translateY(-10px);
    box-shadow: 0 0 25px rgba(255, 215, 0, 1);
  }
}

.card-img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

/* 我的手牌样式 */
.my-hand {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap;
  padding: 10px;
  width: 100%;
  margin-top: 10px;
}

.my-hand .card {
  width: 80px;
  height: 115px;
  margin: 0 -20px;
  position: relative;
  z-index: 1;
}

.my-hand .card.selected {
  transform: translateY(-10px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
}

/* 我的信息区域 */
.my-info-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background-color: rgba(255, 255, 255, 0.2);
  padding: 5px 15px;
  border-radius: 15px;
}

/* 倒计时和操作区域 */
.action-area {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 50px !important;
  background-color: rgba(255, 255, 255, 0);
  padding: 10px 20px;
  border-radius: 25px;
  width: 100%;
}

/* 倒计时样式 */
.countdown {
  margin-right: 20px;
}

.countdown-circle {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background-color: rgba(255, 194, 26, 0.911);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 10px;
}

.btn {
  background-color: rgba(255, 255, 255, 0.9);
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  padding: 8px 16px;
  margin: 0 5px;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
  color: #333;
}

.btn:hover {
  background-color: rgba(51, 51, 51, 0.8);
  color: white;
  border-color: rgba(255, 255, 255, 0.8);
}

.btn:disabled {
  background-color: rgba(204, 204, 204, 0.5);
  cursor: not-allowed;
  border-color: rgba(153, 153, 153, 0.5);
  color: rgba(102, 102, 102, 0.7);
}

.btn:disabled:hover {
  background-color: rgba(204, 204, 204, 0.5);
  color: rgba(102, 102, 102, 0.7);
}

/* 快捷文字样式 */
.quick-texts {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-left: 20px;
}

.quick-texts-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 5px;
}

.quick-text-buttons {
  display: flex;
  gap: 5px;
}

.quick-text-btn {
  padding: 3px 8px;
  background-color: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 10px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-text-btn:hover {
  background-color: rgba(51, 51, 51, 0.7);
  color: white;
}

/* 不要指示器 */
.pass-indicator {
  background-color: rgba(255, 255, 255, 0.7);
  padding: 5px 15px;
  border-radius: 15px;
  font-size: 14px;
  font-weight: bold;
  color: #333;
  margin-top:20px;
}

/* 当前玩家提示 */
.current-player {
  background-color: rgba(255, 255, 255, 0);
  padding: 5px 15px;
  border-radius: 20px;
  font-size: 16px;
  font-weight: bold;
  margin: 50px 0;
  color: hsla(19, 100%, 9%, 0.6);
}
/* 响应式适配 */
@media (max-width: 768px) {
  .card { width: 60px; height: 85px; }
  .prepare-stage { padding: 30px 50px; }
}
@media (max-width: 480px) {
  .card { width: 50px; height: 70px; }
  .prepare-stage { padding: 20px 30px; }
  .prepare-btns { flex-direction: column; }
}

/* ========== 手牌横向自适应 ========== */
.game-stage {
  position: relative !important;
  width: 100vw !important;
  height: 100vh !important;
  overflow: hidden !important;
}

.game-container {
  overflow: hidden !important;
}

.desk-center {
  position: absolute !important;
  inset: 0 !important;
  pointer-events: none !important;
  z-index: 20 !important;
}

.table-area {
  position: absolute !important;
  left: 50% !important;
  top: 45% !important;
  width: 62% !important;
  height: 330px !important;
  min-height: 0 !important;
  padding: 18px !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: flex-start !important;
  overflow: visible !important;
  pointer-events: auto !important;
  z-index: 20 !important;
  transform: translate(-50%, -50%) rotateX(22deg) rotateY(0deg) scale(0.98) !important;
}

.desk-slots {
  width: 100% !important;
  height: 210px !important;
  min-height: 210px !important;
  display: grid !important;
  grid-template-columns: 1fr 1fr 1fr !important;
  grid-template-rows: 1fr 1fr 1fr !important;
  gap: 10px !important;
  overflow: visible !important;
}

.played-slot {
  display: flex !important;
  justify-content: center !important;
  align-items: center !important;
  overflow: visible !important;
}

.played-cards-group {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 0 !important;
  overflow: visible !important;
}

.played-cards-group > div {
  margin-left: -14px !important;
  flex: 0 0 auto !important;
}

.played-cards-group > div:first-child {
  margin-left: 0 !important;
}

.card.small-on-desk {
  width: 66px !important;
  height: 94px !important;
  margin: 0 !important;
  border-radius: 6px !important;
  overflow: hidden !important;
  box-shadow: 0 3px 9px rgba(0, 0, 0, 0.26) !important;
}

.played-slot.pos-bottom .played-cards-group {
  transform: translateY(8px) !important;
}

.action-area {
  position: fixed !important;
  left: 50% !important;
  bottom: calc(var(--my-hand-height, 148px) + 8px) !important;
  transform: translateX(-50%) !important;
  width: auto !important;
  min-width: 300px !important;
  height: 54px !important;
  margin: 0 !important;
  padding: 4px 14px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 12px !important;
  background: transparent !important;
  border-radius: 26px !important;
  z-index: 220 !important;
  pointer-events: auto !important;
}

.action-buttons {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 12px !important;
  position: relative !important;
  z-index: 221 !important;
  pointer-events: auto !important;
}

.countdown {
  margin-right: 10px !important;
  position: relative !important;
  z-index: 221 !important;
}

.countdown-circle {
  width: 48px !important;
  height: 48px !important;
}

.current-player {
  margin: 0 !important;
  padding: 6px 18px !important;
  background: rgba(255, 248, 232, 0.78) !important;
  backdrop-filter: blur(4px) !important;
  border-radius: 18px !important;
  position: relative !important;
  z-index: 221 !important;
}

.player-section.top {
  top: 2px !important;
  left: 50% !important;
  width: 54% !important;
  min-width: 520px !important;
  max-height: 132px !important;
  padding: 2px 6px !important;
  transform: translateX(-50%) !important;
  overflow: visible !important;
  z-index: 70 !important;
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.player-section.top .player-info {
  margin-bottom: 0 !important;
  padding: 1px 8px !important;
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.player-section.top .player-cards {
  position: relative !important;
  width: 560px !important;
  height: 70px !important;
  overflow: hidden !important;
}

.player-section.top .player-cards .card {
  width: 48px !important;
  height: 68px !important;
  margin: 0 !important;
  border-radius: 4px !important;
  box-shadow: none !important;
}

.player-section.top .player-cards .card:nth-child(1) { transform: translateX(-252px) !important; z-index: 1; }
.player-section.top .player-cards .card:nth-child(2) { transform: translateX(-232px) !important; z-index: 2; }
.player-section.top .player-cards .card:nth-child(3) { transform: translateX(-212px) !important; z-index: 3; }
.player-section.top .player-cards .card:nth-child(4) { transform: translateX(-192px) !important; z-index: 4; }
.player-section.top .player-cards .card:nth-child(5) { transform: translateX(-172px) !important; z-index: 5; }
.player-section.top .player-cards .card:nth-child(6) { transform: translateX(-152px) !important; z-index: 6; }
.player-section.top .player-cards .card:nth-child(7) { transform: translateX(-132px) !important; z-index: 7; }
.player-section.top .player-cards .card:nth-child(8) { transform: translateX(-112px) !important; z-index: 8; }
.player-section.top .player-cards .card:nth-child(9) { transform: translateX(-92px) !important; z-index: 9; }
.player-section.top .player-cards .card:nth-child(10) { transform: translateX(-72px) !important; z-index: 10; }
.player-section.top .player-cards .card:nth-child(11) { transform: translateX(-52px) !important; z-index: 11; }
.player-section.top .player-cards .card:nth-child(12) { transform: translateX(-32px) !important; z-index: 12; }
.player-section.top .player-cards .card:nth-child(13) { transform: translateX(-12px) !important; z-index: 13; }
.player-section.top .player-cards .card:nth-child(14) { transform: translateX(8px) !important; z-index: 14; }
.player-section.top .player-cards .card:nth-child(15) { transform: translateX(28px) !important; z-index: 15; }
.player-section.top .player-cards .card:nth-child(16) { transform: translateX(48px) !important; z-index: 16; }
.player-section.top .player-cards .card:nth-child(17) { transform: translateX(68px) !important; z-index: 17; }
.player-section.top .player-cards .card:nth-child(18) { transform: translateX(88px) !important; z-index: 18; }
.player-section.top .player-cards .card:nth-child(19) { transform: translateX(108px) !important; z-index: 19; }
.player-section.top .player-cards .card:nth-child(20) { transform: translateX(128px) !important; z-index: 20; }
.player-section.top .player-cards .card:nth-child(21) { transform: translateX(148px) !important; z-index: 21; }
.player-section.top .player-cards .card:nth-child(22) { transform: translateX(168px) !important; z-index: 22; }
.player-section.top .player-cards .card:nth-child(23) { transform: translateX(188px) !important; z-index: 23; }
.player-section.top .player-cards .card:nth-child(24) { transform: translateX(208px) !important; z-index: 24; }
.player-section.top .player-cards .card:nth-child(25) { transform: translateX(228px) !important; z-index: 25; }
.player-section.top .player-cards .card:nth-child(26) { transform: translateX(248px) !important; z-index: 26; }
.player-section.top .player-cards .card:nth-child(27) { transform: translateX(268px) !important; z-index: 27; }

.player-section.left,
.player-section.right {
  top: 50% !important;
  width: 110px !important;
  max-height: 390px !important;
  padding: 4px !important;
  overflow: visible !important;
  z-index: 75 !important;
  background-color: rgba(255, 255, 255, 0.10) !important;
}

.player-section.left {
  left: 10px !important;
}

.player-section.right {
  right: 10px !important;
}

.player-cards.vertical {
  height: 270px !important;
  width: 86px !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  overflow: hidden !important;
}

.player-cards.vertical .card {
  width: 56px !important;
  height: 80px !important;
  margin: -66px 0 !important;
  border-radius: 4px !important;
  box-shadow: none !important;
}

.player-section.top .card.back,
.player-section.left .card.back,
.player-section.right .card.back {
  background-size: cover !important;
  background-position: center !important;
}

.player-section.bottom {
  position: fixed !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  width: 100vw !important;
  height: var(--my-hand-height, 148px) !important;
  max-height: none !important;
  padding: 0 !important;
  margin: 0 !important;
  transform: none !important;
  z-index: 120 !important;
  overflow: visible !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
  pointer-events: none !important;
}

.my-info-section {
  position: fixed !important;
  left: 34px !important;
  right: 34px !important;
  bottom: calc(var(--my-hand-height, 148px) + 14px) !important;
  width: auto !important;
  min-height: 42px !important;
  height: 42px !important;
  padding: 0 !important;
  margin: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  z-index: 140 !important;
  background: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
  pointer-events: none !important;
}

.my-info-section .player-avatar,
.my-info-section .player-details,
.my-info-section .quick-texts,
.my-info-section button {
  pointer-events: auto !important;
}

.my-info-section .player-details {
  position: fixed !important;
  left: 104px !important;
  bottom: calc(var(--my-hand-height, 148px) + 20px) !important;
  padding: 4px 10px !important;
  border-radius: 12px !important;
  background: rgba(255, 248, 232, 0.68) !important;
  backdrop-filter: blur(3px) !important;
}

.my-info-section .player-avatar {
  position: fixed !important;
  left: 38px !important;
  bottom: calc(var(--my-hand-height, 148px) + 12px) !important;
  z-index: 145 !important;
}

.quick-texts {
  position: fixed !important;
  right: 28px !important;
  bottom: calc(var(--my-hand-height, 148px) + 18px) !important;
  margin: 0 !important;
  padding: 4px 10px !important;
  border-radius: 14px !important;
  background: rgba(255, 248, 232, 0.54) !important;
  backdrop-filter: blur(3px) !important;
  z-index: 145 !important;
}

.quick-texts-title {
  color: rgba(80, 45, 22, 0.72) !important;
}

/* 自己手牌：横向自适应核心 */
.my-hand {
  position: fixed !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  width: 100vw !important;
  height: var(--my-hand-height, 148px) !important;
  min-height: var(--my-hand-height, 148px) !important;
  box-sizing: border-box !important;
  padding: 4px 18px 0 !important;
  margin: 0 !important;
  display: flex !important;
  justify-content: center !important;
  align-items: flex-end !important;
  flex-wrap: nowrap !important;
  overflow: visible !important;
  z-index: 130 !important;
  pointer-events: auto !important;
}

.my-hand .card {
  width: var(--my-card-width, 98px) !important;
  height: var(--my-card-height, 140px) !important;
  margin: 0 var(--my-card-margin-x, -20px) !important;
  flex: 0 0 auto !important;
  border-radius: 7px !important;
  overflow: hidden !important;
  position: relative !important;
  z-index: 1 !important;
  pointer-events: auto !important;
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.30) !important;
}

.my-hand .card:hover {
  transform: translateY(-6px) scale(1.035) !important;
  z-index: 190 !important;
}

.my-hand .card.selected {
  transform: translateY(-18px) scale(1.035) !important;
  z-index: 200 !important;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.38) !important;
}

.my-hand .card.suggested {
  z-index: 199 !important;
}

.card-img {
  width: 100% !important;
  height: 100% !important;
  display: block !important;
  object-fit: fill !important;
}

.level-card-container,
.exit-btn-container {
  z-index: 180 !important;
}

@media (max-width: 1024px) {
  .table-area {
    width: 68% !important;
    height: 285px !important;
    top: 44% !important;
  }

  .desk-slots {
    height: 175px !important;
    min-height: 175px !important;
  }

  .card.small-on-desk {
    width: 54px !important;
    height: 76px !important;
  }

  .my-info-section .player-details,
  .quick-texts {
    display: none !important;
  }
}

/* ========== BRO 来财：顶部玩家卡牌重叠显示 V8 ========== */
.player-section.top {
  width: min(78vw, 980px) !important;
  min-width: 0 !important;
  max-height: 132px !important;
  overflow: visible !important;
}

.player-section.top .player-cards {
  --top-card-width: clamp(34px, 2.35vw, 46px);
  --top-card-height: calc(var(--top-card-width) * 1.43);
  --top-card-overlap: calc(var(--top-card-width) * -0.42);

  position: relative !important;
  width: auto !important;
  max-width: 100% !important;
  height: calc(var(--top-card-height) + 6px) !important;
  min-height: calc(var(--top-card-height) + 6px) !important;
  display: flex !important;
  flex-direction: row !important;
  justify-content: center !important;
  align-items: center !important;
  gap: 0 !important;
  overflow: visible !important;
  padding: 0 12px !important;
  box-sizing: border-box !important;
}

.player-section.top .player-cards .card,
.player-section.top .player-cards .card:nth-child(n) {
  position: relative !important;
  left: auto !important;
  top: auto !important;
  transform: none !important;
  width: var(--top-card-width) !important;
  height: var(--top-card-height) !important;
  min-width: var(--top-card-width) !important;
  flex: 0 0 var(--top-card-width) !important;
  margin: 0 0 0 var(--top-card-overlap) !important;
  border-radius: 3px !important;
  overflow: hidden !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.22) !important;
}

.player-section.top .player-cards .card:first-child {
  margin-left: 0 !important;
}

.player-section.top .player-cards .card:nth-child(1) { z-index: 1 !important; }
.player-section.top .player-cards .card:nth-child(2) { z-index: 2 !important; }
.player-section.top .player-cards .card:nth-child(3) { z-index: 3 !important; }
.player-section.top .player-cards .card:nth-child(4) { z-index: 4 !important; }
.player-section.top .player-cards .card:nth-child(5) { z-index: 5 !important; }
.player-section.top .player-cards .card:nth-child(6) { z-index: 6 !important; }
.player-section.top .player-cards .card:nth-child(7) { z-index: 7 !important; }
.player-section.top .player-cards .card:nth-child(8) { z-index: 8 !important; }
.player-section.top .player-cards .card:nth-child(9) { z-index: 9 !important; }
.player-section.top .player-cards .card:nth-child(10) { z-index: 10 !important; }
.player-section.top .player-cards .card:nth-child(11) { z-index: 11 !important; }
.player-section.top .player-cards .card:nth-child(12) { z-index: 12 !important; }
.player-section.top .player-cards .card:nth-child(13) { z-index: 13 !important; }
.player-section.top .player-cards .card:nth-child(14) { z-index: 14 !important; }
.player-section.top .player-cards .card:nth-child(15) { z-index: 15 !important; }
.player-section.top .player-cards .card:nth-child(16) { z-index: 16 !important; }
.player-section.top .player-cards .card:nth-child(17) { z-index: 17 !important; }
.player-section.top .player-cards .card:nth-child(18) { z-index: 18 !important; }
.player-section.top .player-cards .card:nth-child(19) { z-index: 19 !important; }
.player-section.top .player-cards .card:nth-child(20) { z-index: 20 !important; }
.player-section.top .player-cards .card:nth-child(21) { z-index: 21 !important; }
.player-section.top .player-cards .card:nth-child(22) { z-index: 22 !important; }
.player-section.top .player-cards .card:nth-child(23) { z-index: 23 !important; }
.player-section.top .player-cards .card:nth-child(24) { z-index: 24 !important; }
.player-section.top .player-cards .card:nth-child(25) { z-index: 25 !important; }
.player-section.top .player-cards .card:nth-child(26) { z-index: 26 !important; }
.player-section.top .player-cards .card:nth-child(27) { z-index: 27 !important; }

.player-section.top .player-cards .card.back {
  background-size: 100% 100% !important;
  background-position: center center !important;
  background-repeat: no-repeat !important;
}

@media (max-width: 1440px) {
  .player-section.top {
    width: min(80vw, 860px) !important;
  }

  .player-section.top .player-cards {
    --top-card-width: clamp(30px, 2.1vw, 40px);
    --top-card-overlap: calc(var(--top-card-width) * -0.46);
  }
}

@media (max-width: 1024px) {
  .player-section.top {
    width: min(84vw, 700px) !important;
  }

  .player-section.top .player-cards {
    --top-card-width: clamp(24px, 2vw, 32px);
    --top-card-overlap: calc(var(--top-card-width) * -0.50);
  }
}

/* ========== 右侧玩家信息完整显示修正 V9 ========== */
.player-section.right {
  right: 28px !important;
  width: 132px !important;
  min-width: 132px !important;
  max-width: 132px !important;
  padding: 6px 6px !important;
  box-sizing: border-box !important;
  overflow: visible !important;
  align-items: center !important;
  z-index: 70 !important;
}

.player-section.right .player-info {
  width: 118px !important;
  min-width: 118px !important;
  max-width: 118px !important;
  display: flex !important;
  flex-direction: column-reverse !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 4px !important;
  margin-bottom: 6px !important;
  padding: 6px 4px !important;
  box-sizing: border-box !important;
  overflow: visible !important;
}

.player-section.right .player-avatar {
  margin: 0 !important;
  flex-shrink: 0 !important;
}

.player-section.right .player-details {
  width: 100% !important;
  min-width: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  text-align: center !important;
  overflow: visible !important;
}

.player-section.right .player-name,
.player-section.right .player-status {
  width: 100% !important;
  max-width: 112px !important;
  display: block !important;
  text-align: center !important;
  white-space: nowrap !important;
  overflow: visible !important;
  text-overflow: clip !important;
  line-height: 18px !important;
}

.player-section.right .player-cards.vertical {
  width: 72px !important;
  align-items: center !important;
  overflow: visible !important;
}

@media (max-width: 1440px) {
  .player-section.right {
    right: 18px !important;
    width: 118px !important;
    min-width: 118px !important;
    max-width: 118px !important;
  }

  .player-section.right .player-info {
    width: 108px !important;
    min-width: 108px !important;
    max-width: 108px !important;
  }

  .player-section.right .player-name,
  .player-section.right .player-status {
    max-width: 104px !important;
    font-size: 12px !important;
  }
}

/* ========== BRO 来财：右侧玩家牌墙 V10 ========== */
.player-section.left,
.player-section.right {
  top: 50% !important;
  width: 112px !important;
  min-width: 112px !important;
  max-width: 112px !important;
  max-height: 390px !important;
  padding: 5px 4px !important;
  box-sizing: border-box !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: flex-start !important;
  overflow: visible !important;
  z-index: 90 !important;
  background-color: rgba(255, 255, 255, 0.12) !important;
  border-radius: 10px !important;
}

.player-section.left {
  left: 12px !important;
  right: auto !important;
  transform: translateY(-50%) !important;
}

.player-section.right {
  right: 12px !important;
  left: auto !important;
  transform: translateY(-50%) !important;
}

.player-section.right .player-info {
  width: 100% !important;
  min-width: 0 !important;
  max-width: 100% !important;
  height: auto !important;
  min-height: 96px !important;
  margin: 0 0 6px 0 !important;
  padding: 6px 4px !important;
  box-sizing: border-box !important;
  display: flex !important;
  flex-direction: column-reverse !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 4px !important;
  overflow: visible !important;
  background-color: rgba(255, 255, 255, 0.16) !important;
  border-radius: 10px !important;
}

.player-section.left .player-info {
  width: 100% !important;
  min-width: 0 !important;
  max-width: 100% !important;
  height: auto !important;
  min-height: 96px !important;
  margin: 0 0 6px 0 !important;
  padding: 6px 4px !important;
  box-sizing: border-box !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 4px !important;
  overflow: visible !important;
  background-color: rgba(255, 255, 255, 0.16) !important;
  border-radius: 10px !important;
}

.player-section.left .player-avatar,
.player-section.right .player-avatar {
  margin: 0 !important;
  flex: 0 0 auto !important;
}

.player-section.left .player-details,
.player-section.right .player-details {
  width: 100% !important;
  min-width: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: center !important;
  text-align: center !important;
  overflow: visible !important;
}

.player-section.left .player-name,
.player-section.left .player-status,
.player-section.right .player-name,
.player-section.right .player-status {
  width: 100% !important;
  max-width: 104px !important;
  display: block !important;
  text-align: center !important;
  white-space: nowrap !important;
  overflow: visible !important;
  text-overflow: clip !important;
  line-height: 18px !important;
}

.player-section.left .player-cards.vertical,
.player-section.right .player-cards.vertical {
  width: 86px !important;
  height: 272px !important;
  min-height: 272px !important;
  max-height: 272px !important;
  padding: 2px 0 0 0 !important;
  box-sizing: border-box !important;
  display: flex !important;
  flex-direction: column !important;
  align-items: center !important;
  justify-content: flex-start !important;
  gap: 0 !important;
  overflow: hidden !important;
}

.player-section.left .player-cards.vertical .card,
.player-section.right .player-cards.vertical .card {
  position: relative !important;
  left: auto !important;
  right: auto !important;
  top: auto !important;
  bottom: auto !important;
  transform: none !important;
  width: 56px !important;
  height: 80px !important;
  min-width: 56px !important;
  min-height: 80px !important;
  flex: 0 0 80px !important;
  margin: -54px 0 0 0 !important;
  border-radius: 4px !important;
  overflow: hidden !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.22) !important;
}

.player-section.left .player-cards.vertical .card:first-child,
.player-section.right .player-cards.vertical .card:first-child {
  margin-top: 0 !important;
}

.player-section.left .player-cards.vertical .card.back,
.player-section.right .player-cards.vertical .card.back {
  background-size: 100% 100% !important;
  background-position: center center !important;
  background-repeat: no-repeat !important;
}

.played-slot.pos-bottom {
  position: absolute !important;
  left: 50% !important;
  right: auto !important;
  top: auto !important;
  bottom: 2px !important;
  transform: translateX(-50%) !important;
  width: 520px !important;
  height: 112px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  overflow: visible !important;
  z-index: 80 !important;
  pointer-events: none !important;
}

.played-slot.pos-bottom .played-cards-group {
  position: relative !important;
  left: auto !important;
  right: auto !important;
  top: auto !important;
  bottom: auto !important;
  transform: none !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 0 !important;
  overflow: visible !important;
  margin: 0 auto !important;
}

.played-slot.pos-bottom .played-cards-group > div {
  margin-left: -12px !important;
  flex: 0 0 auto !important;
}

.played-slot.pos-bottom .played-cards-group > div:first-child {
  margin-left: 0 !important;
}

.played-slot.pos-bottom .card.small-on-desk {
  width: 70px !important;
  height: 100px !important;
  margin: 0 !important;
  border-radius: 6px !important;
  overflow: hidden !important;
  box-shadow: 0 4px 11px rgba(0, 0, 0, 0.30) !important;
}

.played-slot.pos-bottom .pass-indicator {
  margin: 0 !important;
  min-width: 120px !important;
  text-align: center !important;
}

.action-area {
  position: fixed !important;
  left: 50% !important;
  bottom: calc(var(--my-hand-height, 148px) + 18px) !important;
  transform: translateX(-50%) !important;
  z-index: 260 !important;
}

@media (max-width: 1440px) {
  .player-section.left,
  .player-section.right {
    width: 106px !important;
    min-width: 106px !important;
    max-width: 106px !important;
  }

  .player-section.left {
    left: 8px !important;
  }

  .player-section.right {
    right: 8px !important;
  }

  .player-section.left .player-cards.vertical,
  .player-section.right .player-cards.vertical {
    width: 80px !important;
    height: 250px !important;
    min-height: 250px !important;
    max-height: 250px !important;
  }

  .player-section.left .player-cards.vertical .card,
  .player-section.right .player-cards.vertical .card {
    width: 52px !important;
    height: 74px !important;
    min-width: 52px !important;
    min-height: 74px !important;
    flex-basis: 74px !important;
    margin-top: -50px !important;
  }

  .player-section.left .player-cards.vertical .card:first-child,
  .player-section.right .player-cards.vertical .card:first-child {
    margin-top: 0 !important;
  }

  .played-slot.pos-bottom {
    bottom: 0 !important;
    width: 460px !important;
    height: 100px !important;
  }

  .played-slot.pos-bottom .card.small-on-desk {
    width: 62px !important;
    height: 88px !important;
  }
}

@media (max-width: 1024px) {
  .player-section.left,
  .player-section.right {
    width: 92px !important;
    min-width: 92px !important;
    max-width: 92px !important;
  }

  .player-section.left .player-info,
  .player-section.right .player-info {
    min-height: 86px !important;
  }

  .player-section.left .player-name,
  .player-section.left .player-status,
  .player-section.right .player-name,
  .player-section.right .player-status {
    max-width: 88px !important;
    font-size: 12px !important;
  }

  .player-section.left .player-cards.vertical,
  .player-section.right .player-cards.vertical {
    width: 70px !important;
    height: 220px !important;
    min-height: 220px !important;
    max-height: 220px !important;
  }

  .player-section.left .player-cards.vertical .card,
  .player-section.right .player-cards.vertical .card {
    width: 46px !important;
    height: 66px !important;
    min-width: 46px !important;
    min-height: 66px !important;
    flex-basis: 66px !important;
    margin-top: -45px !important;
  }

  .played-slot.pos-bottom {
    width: 360px !important;
    height: 88px !important;
  }

  .played-slot.pos-bottom .card.small-on-desk {
    width: 54px !important;
    height: 76px !important;
  }

  .played-slot.pos-bottom .played-cards-group > div {
    margin-left: -10px !important;
  }
}
</style>
