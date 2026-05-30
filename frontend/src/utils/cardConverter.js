/**
 * 卡牌转换工具类
 * 实现前后端卡牌格式的互转
 * 后端使用数字ID (0-107) 表示卡牌
 * 前端使用对象格式 {suit: 'hearts', rank: 1} 表示卡牌
 *
 * @module cardConverter
 *
 * @example
 * // 后端ID转前端对象
 * const card = idToCard(0);
 * // => { suit: 'diamonds', rank: 2, deck: 0 }
 *
 * @example
 * // 前端对象转后端ID
 * const id = cardToId({ suit: 'hearts', rank: 1, deck: 0 });
 * // => 40
 *
 * @example
 * // 批量转换
 * const cards = idsToCards([0, 1, 2]);
 * const ids = cardsToIds(cards);
 *
 * @since 1.0.0
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
  if (cardId < 0 || cardId > 107) {
    throw new Error(`无效的卡牌ID: ${cardId}`);
  }

  // 处理大小王
  if (cardId >= 104) {
    const result = {
      suit: 'jokers',
      rank: cardId <= 105 ? 14 : 15,  // 104-105:小王(14), 106-107:大王(15)
      deck: (cardId - 104) % 2  // 0或1，表示第几副牌的王
    };
    return result;
  }

  // 处理普通牌 (0-103)
  const deckIndex = Math.floor(cardId / 52);  // 0或1，表示第几副牌
  const cardInDeck = cardId % 52; // 在单副牌中的位置

  const suitIndex = Math.floor(cardInDeck / 13);   // 花色 (0-3)
  const rankIndex = cardInDeck % 13;   // 点数 (0-12)

  const result = {
    suit: SUIT_MAP[suitIndex],
    rank: RANK_MAP[rankIndex],
    deck: deckIndex // 添加副牌索引，用于正确转换回卡牌ID
  };
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
  return cardIds.map(idToCard);
}

/**
 * 将前端卡牌对象数组转换为后端卡牌ID数组
 * @param {Array<Object>} cards 前端卡牌对象数组
 * @returns {Array<number>} 后端卡牌ID数组
 */
export function cardsToIds(cards) {
  const result = cards.map(card => cardToId(card, card.deck));
  return result;
}

/**
 * 手牌自动排序（收到发牌后自动调用）
 * 排序规则：先按点数从大到小，同点数按花色（黑桃>红桃>梅花>方块），级牌和逢人配优先
 * @param {Array<Object>} cards 前端卡牌对象数组
 * @param {number} levelCardRank 级牌点数 (0-12对应2-A)
 * @returns {Array<Object>} 排序后的手牌
 */
export function sortHandCards(cards, levelCardRank = null) {
  if (!cards || !Array.isArray(cards)) return [];

  return [...cards].sort((a, b) => {
    // 逢人配（红桃级牌）最优先
    if (levelCardRank !== null) {
      const aIsWild = isWildCard(a, levelCardRank);
      const bIsWild = isWildCard(b, levelCardRank);
      if (aIsWild && !bIsWild) return -1;
      if (!aIsWild && bIsWild) return 1;

      // 级牌优先
      const aIsLevel = isLevelCard(a, levelCardRank);
      const bIsLevel = isLevelCard(b, levelCardRank);
      if (aIsLevel && !bIsLevel) return -1;
      if (!aIsLevel && bIsLevel) return 1;
    }

    // 大小王优先
    if (a.suit === 'jokers' && b.suit !== 'jokers') return -1;
    if (a.suit !== 'jokers' && b.suit === 'jokers') return 1;
    if (a.suit === 'jokers' && b.suit === 'jokers') return b.rank - a.rank;

    // 按点数从大到小
    if (b.rank !== a.rank) return b.rank - a.rank;

    // 同点数按花色（黑桃3 > 红桃2 > 梅花1 > 方块0）
    const suitOrder = { spades: 3, hearts: 2, clubs: 1, diamonds: 0 };
    return (suitOrder[b.suit] || 0) - (suitOrder[a.suit] || 0);
  });
}

/**
 * 获取卡牌的可读名称
 * @param {Object} card 前端卡牌对象
 * @param {number} [levelCardRank=null] 级牌点数 (0-12对应2-A)
 * @returns {string} 可读名称，如 "红桃A"、"小王"、"红桃2(级)(逢人配)"
 *
 * @example
 * getCardName({ suit: 'hearts', rank: 1 }); // => "红桃A"
 * getCardName({ suit: 'jokers', rank: 14 }); // => "小王"
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
 *
 * @example
 * isLevelCard({ suit: 'hearts', rank: 4 }, 2); // => true (级牌点数为2时，rank=4对应级牌)
 * isLevelCard({ suit: 'jokers', rank: 14 }, 2); // => false (大小王不是级牌)
 */
export function isLevelCard(card, levelCardRank) {
  if (!card || card.suit === 'jokers') {
    return false;
  }
  return card.rank === levelCardRank + 2;
}

/**
 * 判断卡牌是否为逢人配（万能牌）
 * 逢人配是红桃的级牌，可以作为任意牌使用。
 * 前置条件：先调用 isLevelCard 判断是否为级牌。
 *
 * @param {Object} card 前端卡牌对象
 * @param {number} levelCardRank 级牌点数 (0-12对应2-A)
 * @returns {boolean} 是否为逢人配
 *
 * @example
 * isWildCard({ suit: 'hearts', rank: 4 }, 2); // => true (红桃级牌)
 * isWildCard({ suit: 'spades', rank: 4 }, 2); // => false (黑桃级牌不是逢人配)
 */
export function isWildCard(card, levelCardRank) {
  if (!card || card.suit === 'jokers') {
    return false;
  }
  return isLevelCard(card, levelCardRank) && card.suit === 'hearts';
}

/**
 * 批量转换卡牌ID到前端对象（含空值过滤优化）
 * 相比 idsToCards，此方法过滤无效ID避免 throw 中断批量操作
 * @param {Array<number>} cardIds 后端卡牌ID数组
 * @returns {Array<Object>} 有效的卡牌对象数组
 */
export function bulkIdToCard(cardIds) {
  if (!cardIds || !Array.isArray(cardIds)) return [];
  const result = [];
  for (let i = 0; i < cardIds.length; i++) {
    const id = cardIds[i];
    if (id === null || id === undefined) continue;
    if (id < 0 || id > 107) continue;
    const card = idToCard(id);
    if (card) result.push(card);
  }
  return result;
}

/**
 * 比较两组卡牌是否完全相同（用于虚拟列表 key 生成和 DOM 复用判定）
 * @param {Array<Object>} a 卡牌数组A
 * @param {Array<Object>} b 卡牌数组B
 * @returns {boolean} 两组卡牌是否完全相同
 */
export function isSameCards(a, b) {
  if (a === b) return true;
  if (!a || !b) return false;
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const ca = a[i];
    const cb = b[i];
    if (!ca || !cb) return false;
    if (ca.suit !== cb.suit || ca.rank !== cb.rank || ca.deck !== cb.deck) return false;
  }
  return true;
}
