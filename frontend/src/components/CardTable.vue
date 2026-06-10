<template>
  <!--
    CardTable.vue — 牌桌渲染组件
    封装牌桌区域渲染、四方向玩家位置布局、出牌轨迹动效
  -->
  <div class="card-table-root">
    <!-- 中央桌子区域 -->
    <div class="desk-center">
      <div class="table-area">
        <!-- 桌面出牌槽位 -->
        <div class="desk-slots">
          <!-- 上家（队友） -->
          <div class="played-slot pos-top">
            <transition-group name="card-fly" tag="div" class="played-cards-group">
              <div v-for="(card, idx) in deskDisplay.teammate || []" :key="'tm-' + idx">
                <div v-if="card.type === 'pass'" class="pass-indicator">不要</div>
                <div v-else class="card small-on-desk" :class="getCardAnimClass(idx, 'top')">
                  <img :src="getCardImage(card)" class="card-img">
                </div>
              </div>
            </transition-group>
          </div>
          <!-- 左对手 -->
          <div class="played-slot pos-left">
            <transition-group name="card-fly" tag="div" class="played-cards-group vertical-group">
              <div v-for="(card, idx) in deskDisplay.leftOpponent || []" :key="'lo-' + idx">
                <div v-if="card.type === 'pass'" class="pass-indicator">不要</div>
                <div v-else class="card small-on-desk" :class="getCardAnimClass(idx, 'left')">
                  <img :src="getCardImage(card)" class="card-img">
                </div>
              </div>
            </transition-group>
          </div>
          <!-- 右对手 -->
          <div class="played-slot pos-right">
            <transition-group name="card-fly" tag="div" class="played-cards-group vertical-group">
              <div v-for="(card, idx) in deskDisplay.rightOpponent || []" :key="'ro-' + idx">
                <div v-if="card.type === 'pass'" class="pass-indicator">不要</div>
                <div v-else class="card small-on-desk" :class="getCardAnimClass(idx, 'right')">
                  <img :src="getCardImage(card)" class="card-img">
                </div>
              </div>
            </transition-group>
          </div>
          <!-- 自己 -->
          <div class="played-slot pos-bottom">
            <transition-group name="card-fly" tag="div" class="played-cards-group">
              <div v-for="(card, idx) in deskDisplay.me || []" :key="'me-' + idx">
                <div v-if="card.type === 'pass'" class="pass-indicator">不要</div>
                <div v-else class="card small-on-desk" :class="getCardAnimClass(idx, 'bottom')">
                  <img :src="getCardImage(card)" class="card-img">
                </div>
              </div>
            </transition-group>
          </div>
        </div>

        <!-- 操作区域（倒计时 + 按钮 + 当前玩家提示） -->
        <div class="action-area">
          <div class="countdown" v-if="currentPlayer === '我'">
            <div class="countdown-circle">{{ countdown }}</div>
          </div>
          <div class="action-buttons" v-show="currentPlayer === '我'">
            <button class="btn btn-pass" @click="onPass">
              <span class="btn-label">不出</span>
              <span class="btn-countdown" v-if="countdown <= 10">{{ countdown }}s</span>
            </button>
            <button class="btn btn-hint" @click="onHint">提示</button>
            <button class="btn btn-play" @click="onPlayCards" :disabled="selectedCards.length === 0">出牌</button>
          </div>
          <div class="current-player" v-if="currentPlayer !== '我'">{{ currentPlayer }} 的回合</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * CardTable.vue — 牌桌渲染组件
 *
 * 职责：
 * 1. 封装牌桌区域的 DOM 结构和布局（四方向出牌槽位）
 * 2. 提供出牌轨迹动效（基于 transition-group + 卡片飞入动画）
 * 3. 暴露操作按钮事件（出牌/过牌/提示）
 * 4. 支持缩放自适应（通过 CSS 变量 + clamp 函数）
 *
 * Props:
 * - deskDisplay: { me: [], teammate: [], leftOpponent: [], rightOpponent: [] }
 * - currentPlayer: string — 当前回合玩家名称
 * - countdown: number — 倒计时秒数
 * - selectedCards: number[] — 选中卡牌索引
 *
 * Events:
 * - play: 出牌按钮点击
 * - pass: 不出按钮点击
 * - hint: 提示按钮点击
 */
import { computed } from 'vue'

const props = defineProps({
  deskDisplay: {
    type: Object,
    required: true,
    default: () => ({ me: [], teammate: [], leftOpponent: [], rightOpponent: [] })
  },
  currentPlayer: { type: String, default: '' },
  countdown: { type: Number, default: 30 },
  selectedCards: { type: Array, default: () => [] }
})

const emit = defineEmits(['play', 'pass', 'hint'])

// 卡牌飞入动画管理：记录每张卡牌是否是新加入的（需要飞入动画）
const cardAnimStates = new Map() // key: 'pos-idx', value: boolean

/**
 * 获取卡牌的飞入动画类名
 * 每张新牌在首次渲染时获得飞入动画，后续不再触发
 */
const getCardAnimClass = (idx, position) => {
  const key = `${position}-${idx}`
  if (!cardAnimStates.has(key)) {
    cardAnimStates.set(key, true)
    return `card-fly-in pos-fly-${position}`
  }
  return ''
}

/**
 * 获取卡牌图片路径（通过 CardTable 接收的图片函数或默认路径）
 */
const getCardImage = (card) => {
  if (!card) return ''
  if (typeof card.imageUrl === 'string' && card.imageUrl) {
    return card.imageUrl
  }
  // 使用默认雪花路径作为 fallback
  try {
    if (card.suit === 'jokers') {
      const jokerType = card.rank === 14 ? '小' : '大'
      return new URL(`../assets/joker/${jokerType}.png`, import.meta.url).href
    }
    const suitMap = { hearts: 'hearts', diamonds: 'diamonds', clubs: 'clubs', spades: 'spades' }
    const suitName = suitMap[card.suit]
    if (!suitName) return ''
    return new URL(`../assets/${suitName}/${card.rank}.png`, import.meta.url).href
  } catch (_) {
    return ''
  }
}

const onPass = () => emit('pass')
const onHint = () => emit('hint')
const onPlayCards = () => emit('play')

// ---- 缩放自适应 ----
const containerScale = computed(() => {
  // 根据窗口宽度自适应缩放，最小 0.7，最大 1.0
  if (typeof window === 'undefined') return 1
  const w = window.innerWidth
  if (w >= 1920) return 1
  if (w >= 1440) return 0.95
  if (w >= 1024) return 0.88
  if (w >= 768) return 0.80
  return 0.72
})
</script>

<style scoped>
/* 牌桌根容器 */
.card-table-root {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 20;
}

.desk-center {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 20;
}

/* 桌子主体 */
.table-area {
  position: absolute;
  left: 50%;
  top: 45%;
  width: 62%;
  height: 330px;
  min-height: 0;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  overflow: visible;
  pointer-events: auto;
  z-index: 20;
  transform: translate(-50%, -50%) rotateX(22deg) rotateY(0deg) scale(0.98);
  background-color: hsl(44, 83%, 93%);
  border-radius: 20px;
  border: 3px solid hsla(19, 100%, 9%, 0.6);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

/* 桌面出牌槽位 Grid */
.desk-slots {
  width: 100%;
  height: 210px;
  min-height: 210px;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
  gap: 10px;
  overflow: visible;
}

.played-slot {
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: visible;
}

.played-slot.pos-top { grid-column: 2; grid-row: 1; }
.played-slot.pos-left { grid-column: 1; grid-row: 2; }
.played-slot.pos-right { grid-column: 3; grid-row: 2; }
.played-slot.pos-bottom { grid-column: 2; grid-row: 3; }

/* 底部槽位特殊定位 */
.played-slot.pos-bottom {
  position: absolute;
  left: 50%;
  right: auto;
  top: auto;
  bottom: 2px;
  transform: translateX(-50%);
  width: 520px;
  height: 112px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
  z-index: 80;
  pointer-events: none;
}

.played-cards-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  overflow: visible;
}

.played-cards-group > div {
  margin-left: -14px;
  flex: 0 0 auto;
}

.played-cards-group > div:first-child {
  margin-left: 0;
}

.played-cards-group.vertical-group {
  flex-direction: column;
}

/* 桌面小牌 */
.card.small-on-desk {
  width: 66px;
  height: 94px;
  margin: 0;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 3px 9px rgba(0, 0, 0, 0.26);
}

.card-img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: fill;
}

/* 底部出牌组的额外偏移 */
.played-slot.pos-bottom .played-cards-group {
  position: relative;
  left: auto;
  right: auto;
  top: auto;
  bottom: auto;
  transform: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  overflow: visible;
  margin: 0 auto;
}

.played-slot.pos-bottom .played-cards-group > div {
  margin-left: -12px;
  flex: 0 0 auto;
}

.played-slot.pos-bottom .played-cards-group > div:first-child {
  margin-left: 0;
}

.played-slot.pos-bottom .card.small-on-desk {
  width: 70px;
  height: 100px;
  margin: 0;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 4px 11px rgba(0, 0, 0, 0.30);
}

/* Pass 指示器 */
.pass-indicator {
  background-color: rgba(255, 255, 255, 0.7);
  padding: 5px 15px;
  border-radius: 15px;
  font-size: 14px;
  font-weight: bold;
  color: #333;
}

.played-slot.pos-bottom .pass-indicator {
  margin: 0;
  min-width: 120px;
  text-align: center;
}

/* ---- 操作区域 ---- */
.action-area {
  position: fixed;
  left: 50%;
  bottom: calc(var(--my-hand-height, 148px) + 18px);
  transform: translateX(-50%);
  width: auto;
  min-width: 300px;
  height: 54px;
  margin: 0;
  padding: 4px 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: transparent;
  border-radius: 26px;
  z-index: 260;
  pointer-events: auto;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  position: relative;
  z-index: 221;
  pointer-events: auto;
}

.countdown {
  margin-right: 10px;
  position: relative;
  z-index: 221;
}

.countdown-circle {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: rgba(255, 194, 26, 0.911);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  animation: pulse-scale 1s infinite;
}

@keyframes pulse-scale {
  0% { transform: scale(1); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}

.current-player {
  margin: 0;
  padding: 6px 18px;
  background: rgba(255, 248, 232, 0.78);
  backdrop-filter: blur(4px);
  border-radius: 18px;
  position: relative;
  z-index: 221;
}

/* 按钮样式 */
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

.btn .btn-label { display: inline-block; }
.btn .btn-countdown {
  display: inline-block;
  margin-left: 4px;
  padding: 0 4px;
  background: rgba(255, 80, 80, 0.85);
  color: #fff;
  border-radius: 4px;
  font-size: 11px;
  line-height: 16px;
  animation: pulse-countdown 1s ease-in-out infinite;
}

@keyframes pulse-countdown {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* 出牌按钮 */
.btn-play {
  background-color: #4CAF50 !important;
  color: white !important;
  border-color: #45a049 !important;
  font-size: 16px !important;
  padding: 10px 22px !important;
  border-radius: 10px !important;
  box-shadow: 0 2px 8px rgba(76, 175, 80, 0.4) !important;
}

.btn-play:hover:not(:disabled) {
  background-color: #45a049 !important;
  transform: scale(1.05) !important;
  box-shadow: 0 4px 14px rgba(76, 175, 80, 0.6) !important;
}

.btn-pass {
  background-color: #ff9800 !important;
  color: white !important;
  border-color: #e68900 !important;
}

.btn-hint {
  background-color: #2196F3 !important;
  color: white !important;
  border-color: #1976D2 !important;
}

/* ---- 响应式适配 ---- */
@media (max-width: 1440px) {
  .table-area {
    width: 66%;
    height: 300px;
  }
  .desk-slots { height: 185px; min-height: 185px; }
  .played-slot.pos-bottom { width: 460px; height: 100px; }
  .played-slot.pos-bottom .card.small-on-desk { width: 62px; height: 88px; }
}

@media (max-width: 1024px) {
  .table-area {
    width: 72%;
    height: 270px;
    top: 44%;
  }
  .desk-slots { height: 165px; min-height: 165px; }
  .card.small-on-desk { width: 54px; height: 76px; }
  .played-slot.pos-bottom { width: 360px; height: 88px; }
  .played-slot.pos-bottom .card.small-on-desk { width: 54px; height: 76px; }
  .action-area { bottom: calc(var(--my-hand-height, 128px) + 12px) !important; }
}

/* ---- 飞入动画 ---- */
.card-fly-enter-active {
  animation: card-fly-to-desk 0.45s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
.card-fly-leave-active {
  transition: opacity 0.25s ease;
}
.card-fly-enter-from,
.card-fly-leave-to {
  opacity: 0;
}

@keyframes card-fly-to-desk {
  0% { opacity: 0; transform: translateY(60px) scale(0.6) rotateZ(-6deg); }
  40% { opacity: 1; transform: translateY(-8px) scale(1.06) rotateZ(2deg); }
  70% { transform: translateY(4px) scale(0.98) rotateZ(-1deg); }
  100% { opacity: 1; transform: translateY(0) scale(1) rotateZ(0deg); }
}
</style>
