/**
 * usePlayCard composable
 * 提取自 BattleView.vue 的出牌/过牌/选牌逻辑
 *
 * 职责边界：
 * - 出牌/过牌操作
 * - 选牌/取消选牌
 * - 顺子检测（前端提示用）
 * - 卡牌 ID 转换与发送
 *
 * 调用方需提供：
 * - selectedCards: Ref<number[]> — 选中卡牌索引
 * - myCards: Ref<Object[]> — 当前玩家手牌对象
 * - currentPlayer: Ref<string> — 当前回合玩家标识
 * - webSocketService — WebSocket 服务实例
 * - WS_MESSAGE_TYPES — 消息类型常量
 * - soundManager — 音效管理器
 * - ElMessage — Element Plus 消息提示
 */

import { cardsToIds } from '../utils/cardConverter'

/**
 * 出牌逻辑
 * @param {Object} options
 * @param {import('vue').Ref<number[]>} options.selectedCards
 * @param {import('vue').Ref<Object[]>} options.myCards
 * @param {import('vue').Ref<string>} options.currentPlayer
 * @param {Object} options.webSocketService
 * @param {Object} options.WS_MESSAGE_TYPES
 * @param {Object} options.soundManager
 * @param {Object} options.ElMessage
 * @returns {Function} playCards 函数
 */
export function usePlayCard({
  selectedCards,
  myCards,
  currentPlayer,
  webSocketService,
  WS_MESSAGE_TYPES,
  soundManager,
  ElMessage
}) {
  /**
   * 出牌操作：选中卡牌后发送出牌消息
   * 空安全处理：索引越界、卡牌对象为空
   */
  const playCards = () => {
    if (selectedCards.value.length === 0 || currentPlayer.value !== '我') {
      if (selectedCards.value.length === 0 && currentPlayer.value === '我') {
        ElMessage.info('请先选择要出的牌')
      }
      return
    }

    try {
      const selectedCardObjects = selectedCards.value
        .map(index => {
          const card = myCards.value[index]
          if (!card) {
            console.warn('playCards: 选中的手牌索引越界', index, '手牌长度:', myCards.value.length)
            return null
          }
          return card
        })
        .filter(c => c !== null)

      if (selectedCardObjects.length === 0) {
        ElMessage.error('选中的卡牌数据异常，请重新选择')
        selectedCards.value = []
        return
      }

      const cardIds = cardsToIds(selectedCardObjects)
      webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, {
        cards: cardIds
      })
      soundManager.play('play_card')
      selectedCards.value = []
    } catch (error) {
      console.error('出牌失败:', error)
      ElMessage.error('出牌失败，请重试')
      selectedCards.value = []
    }
  }

  /**
   * 过牌操作：发送空卡牌列表表示放弃出牌
   */
  const pass = () => {
    if (currentPlayer.value !== '我') {
      ElMessage.info('现在不是你的回合！')
      return
    }
    webSocketService.send(WS_MESSAGE_TYPES.PLAY_CARD, {
      cards: []
    })
  }

  /**
   * 选中/取消选中一张牌
   * @param {number} index - 卡牌在手牌数组中的索引
   */
  const toggleSelectCard = (index) => {
    const idx = selectedCards.value.indexOf(index)
    if (idx === -1) {
      selectedCards.value.push(index)
    } else {
      selectedCards.value.splice(idx, 1)
    }
  }

  /**
   * 清空选中状态
   */
  const clearSelection = () => {
    selectedCards.value = []
  }

  /**
   * 检查是否为顺子（点数连续）
   * 用于前端出牌前提示
   * @param {Object[]} cards - 前端卡牌对象数组
   * @returns {boolean}
   */
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

  // 暴露出牌状态
  const isMyTurn = () => currentPlayer.value === '我'
  const hasSelectedCards = () => selectedCards.value.length > 0

  return {
    playCards,
    pass,
    toggleSelectCard,
    clearSelection,
    isStraightHand,
    isMyTurn,
    hasSelectedCards
  }
}
