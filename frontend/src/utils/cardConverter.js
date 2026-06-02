/**
 * 卡牌转换工具类
 * 实现前后端卡牌格式的互转
 * 后端使用数字ID (0-107) 表示卡牌
 * 前端使用对象格式 {suit: 'hearts', rank: 1} 表示卡牌
 *
 * 联调说明（提升对战页操作体验）：
 * - 手牌展示：BattleView 收到 GAME_START 后调用 idsToCards 批量转换，
 *   前端按 rank 降序 + suit 优先级排序后渲染，逢人配和级牌用 getCardName 标记。
 * - 选牌交互：选中卡牌时前端维护 selectedCards 索引，出牌时调用 cardsToIds
 *   将前端对象数组转回后端 ID 数组后通过 PLAY_CARD 发送。
 * - 出牌反馈：服务端广播 PLAYER_ACTION 带 cardIds，前端调用 idToCard 逐张转换后
 *   渲染到桌面对应槽位，并从我方手牌中移除已出卡牌。
 * - 过牌反馈：服务端广播 PLAYER_ACTION 但 cards 为空，前端在对应槽位显示"不要"指示器。
 * - 注意：idToCard 和 cardToId 互为逆操作，联调时需验证双向转换的一致性。
 *
 * ============================================================
 * 手动测试用例：手牌渲染性能
 * ============================================================
 * TC-CC-001 批量转换性能：
 *   前置：构造 27 个合法 cardId 数组
 *   操作：调用 bulkIdToCard(ids) 10000 次
 *   预期：总耗时 < 500ms（单次 < 0.05ms）
 *
 * TC-CC-002 缓存指纹唯一性：
 *   前置：准备 27 张不同卡牌对象
 *   操作：调用 cardFingerprint(card) 检查返回值
 *   预期：27 张牌的指纹字符串互不相同，长度为 `suit|rank|deck` 格式
 *
 * TC-CC-003 手牌排序一致性：
 *   前置：调用 bulkIdToCard 获得无序卡牌列表
 *   操作：Array.sort 按 rank 降序 + suit 优先级排序
 *   预期：排序后 rank 从大到小，同 rank 按 suit 优先级（黑>红>梅>方）
 *
 * TC-CC-004 idsToCards 空数组防御：
 *   前置：传入 null / undefined / []
 *   操作：调用 idsToCards
 *   预期：返回 []，不抛异常
 *
 * TC-CC-005 bulkIdToCard 含非法 ID：
 *   前置：数组中混入 -1 和 200
 *   操作：调用 bulkIdToCard
 *   预期：跳过滤掉非法 ID，仅返回合法卡牌，不抛异常
 * ============================================================
 */

// 花色映射：后端索引 -> 前端花色名称
const SUIT_MAP = [
  'diamonds',  // 0: 方块
  'clubs',     // 1: 梅花
  'hearts',    // 2: 红桃
  'spades'     // 3: 黑桃
];

// 点数映射：后端索引 -> 前端点数
// 掼蛋规则：后端rank 0=2最小，12=A最大
const RANK_MAP = [
  2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 1  // 后端0-12对应前端2-A
];

/**
 * 将后端卡牌ID转换为前端卡牌对象
 * @param {number} cardId 后端卡牌ID (0-107)
 * @returns {Object} 前端卡牌对象 {suit, rank, deck}
 */
export function idToCard(cardId) {
  // 验证输入
  if (cardId === null || cardId === undefined) {
    console.error('idToCard: 收到 null 或 undefined 的 cardId')
    return { suit: 'unknown', rank: -1, deck: -1 }
  }
  if (!Number.isInteger(cardId) || cardId < 0 || cardId > 107) {
    throw new Error(`无效的卡牌ID: ${cardId}`);
  }

  // 处理大小王
  if (cardId >= 104) {
    const result = {
      suit: 'jokers',
      rank: cardId <= 105 ? 14 : 15,  // 104-105:小王(14), 106-107:大王(15)
      deck: (cardId - 104) % 2  // 0或1，表示第几副牌的王
    };
    // debug 日志仅在开发环境输出，避免生产环境内存泄漏
    if (process.env.NODE_ENV === 'development') {
      console.log(`idToCard: cardId=${cardId} ->`, result, `(deck=${result.deck})`);
    }
    return result;
  }

  // 处理普通牌 (0-103)
  // 每副牌52张：0-51
  const deckIndex = Math.floor(cardId / 52);  // 0或1，表示第几副牌
  const cardInDeck = cardId % 52; // 在单副牌中的位置

  const suitIndex = Math.floor(cardInDeck / 13);   // 花色 (0-3)
  const rankIndex = cardInDeck % 13;   // 点数 (0-12)

  const result = {
    suit: SUIT_MAP[suitIndex],
    rank: RANK_MAP[rankIndex],
    deck: deckIndex // 添加副牌索引，用于正确转换回卡牌ID
  };
  // debug 日志仅在开发环境输出，避免生产环境内存泄漏
  if (process.env.NODE_ENV === 'development') {
    console.log(`idToCard: cardId=${cardId} ->`, result, `(deck=${result.deck})`);
  }
  return result;
}

/**
 * 将前端卡牌对象转换为后端卡牌ID
 * @param {Object} card 前端卡牌对象 {suit, rank, deck}
 * @param {number} defaultDeck 副牌索引 (0或1，默认0)
 * @returns {number} 后端卡牌ID
 */
export function cardToId(card, defaultDeck = 0) {
  // 验证输入
  if (!card || !card.suit || typeof card.rank !== 'number') {
    throw new Error('无效的卡牌对象');
  }

  // 使用卡牌对象中存储的副牌索引，如果没有则使用默认值
  const deck = (card.deck !== undefined && card.deck !== null) ? card.deck : defaultDeck;

  // 处理大小王
  if (card.suit === 'jokers') {
    if (card.rank === 14) {
      return deck === 0 ? 104 : 105; // 小王
    } else if (card.rank === 15) {
      return deck === 0 ? 106 : 107; // 大王
    } else {
      throw new Error('无效的王牌点数');
    }
  }

  // 处理普通牌
  const suitIndex = SUIT_MAP.indexOf(card.suit);
  if (suitIndex === -1) {
    throw new Error(`无效的花色: ${card.suit}`);
  }

  const rankIndex = RANK_MAP.indexOf(card.rank);
  if (rankIndex === -1) {
    throw new Error(`无效的点数: ${card.rank}`);
  }

  // 计算卡牌ID
  const cardInDeck = suitIndex * 13 + rankIndex;
  return deck * 52 + cardInDeck;
}

/**
 * 将后端卡牌ID数组转换为前端卡牌对象数组
 * @param {Array<number>} cardIds 后端卡牌ID数组
 * @returns {Array<Object>} 前端卡牌对象数组
 */
export function idsToCards(cardIds) {
  if (!cardIds || !Array.isArray(cardIds)) return []
  return cardIds.map(id => {
    try { return idToCard(id) } catch { return null }
  }).filter(Boolean);
}

/**
 * 批量转换后端卡牌ID为前端卡牌对象（for循环性能优化版）
 * 拆分自 idsToCards，独立用于 gameStart 等高频批量转换场景，
 * 减少闭包创建和 filter 开销。
 * @param {Array<number>} cardIds 后端卡牌ID数组
 * @returns {Array<Object>} 前端卡牌对象数组
 */
export function bulkIdToCard(cardIds) {
  if (!cardIds || !Array.isArray(cardIds)) return []
  const result = []
  for (let i = 0; i < cardIds.length; i++) {
    try {
      const card = idToCard(cardIds[i])
      if (card) result.push(card)
    } catch (e) {
      console.warn('bulkIdToCard: 跳过无效卡牌ID', cardIds[i], e)
    }
  }
  return result
}

/**
 * 批量转换前端卡牌对象数组为后端卡牌ID数组（性能优化版）
 * 使用 for 循环代替 map 减少函数调用开销
 * @param {Array<Object>} cardObjects 前端卡牌对象数组
 * @returns {Array<number>} 后端卡牌ID数组
 */
export function bulkCardsToIds(cardObjects) {
  if (!cardObjects || !Array.isArray(cardObjects)) return []
  const result = []
  for (let i = 0; i < cardObjects.length; i++) {
    try {
      result.push(cardToId(cardObjects[i], cardObjects[i].deck))
    } catch (e) {
      console.warn('bulkCardsToIds: 跳过无效卡牌', cardObjects[i], e)
    }
  }
  return result
}

/**
 * 将前端卡牌对象数组转换为后端卡牌ID数组
 * @param {Array<Object>} cards 前端卡牌对象数组
 * @returns {Array<number>} 后端卡牌ID数组
 */
export function cardsToIds(cards) {
  const result = cards.map(card => cardToId(card, card.deck));
  // debug 日志仅在开发环境输出
  if (process.env.NODE_ENV === 'development') {
    console.log('cardsToIds: cards=', cards, '-> ids=', result);
  }
  return result;
}

/**
 * 获取卡牌的可读名称
 * @param {Object} card 前端卡牌对象
 * @param {number} levelCardRank 级牌点数 (0-12对应2-A)
 * @returns {string} 可读名称
 */
export function getCardName(card, levelCardRank = null) {
  if (card.suit === 'jokers') {
    return card.rank === 14 ? '小王' : '大王';
  }

  const suitNames = {
    hearts: '红桃',
    diamonds: '方块',
    clubs: '梅花',
    spades: '黑桃'
  };

  const rankNames = {
    1: 'A', 2: '2', 3: '3', 4: '4', 5: '5', 6: '6', 7: '7',
    8: '8', 9: '9', 10: '10', 11: 'J', 12: 'Q', 13: 'K'
  };

  let name = `${suitNames[card.suit]}${rankNames[card.rank]}`;

  // 添加级牌和逢人配标记
  if (levelCardRank !== null) {
    // 判断是否为级牌
    const isLevelCard = (card.rank === levelCardRank + 2);
    if (isLevelCard) {
      name += '(级)';
    }

    // 判断是否为逢人配（红桃的级牌）
    const isWildCard = (card.suit === 'hearts' && card.rank === levelCardRank + 2);
    if (isWildCard) {
      name += '(逢人配)';
    }
  }

  return name;
}

/**
 * 判断卡牌是否为级牌
 * @param {Object} card 前端卡牌对象
 * @param {number} levelCardRank 级牌点数 (0-12对应2-A)
 * @returns {boolean} 是否为级牌
 */
export function isLevelCard(card, levelCardRank) {
  if (!card || card.suit === 'jokers') {
    return false;
  }
  return card.rank === levelCardRank + 2;
}

/**
 * 判断卡牌是否为逢人配（万能牌）
 * @param {Object} card 前端卡牌对象
 * @param {number} levelCardRank 级牌点数 (0-12对应2-A)
 * @returns {boolean} 是否为逢人配
 */
export function isWildCard(card, levelCardRank) {
  if (!card || card.suit === 'jokers') {
    return false;
  }
  return isLevelCard(card, levelCardRank) && card.suit === 'hearts';
}

/**
 * 判断两张卡牌是否相同（按 suit + rank + deck 比较）
 * 用于手牌渲染时去重和对比
 * @param {Object} a 卡牌A
 * @param {Object} b 卡牌B
 * @returns {boolean}
 */
export function isSameCards(a, b) {
  if (!a || !b) return false
  return a.suit === b.suit && a.rank === b.rank && a.deck === b.deck
}

/**
 * 卡牌渲染缓存指纹（优化渲染性能）
 * 为每张卡牌生成唯一字符串 key，减少 BattleView 排序时的对象比较
 * @param {Object} card 前端卡牌对象
 * @returns {string} 缓存指纹
 */
export function cardFingerprint(card) {
  if (!card) return ''
  return `${card.suit}|${card.rank}|${card.deck}`
}
