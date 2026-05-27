/**
 * 卡牌转换工具类
 * 实现前后端卡牌格式的互转
 * 后端使用数字ID (0-107) 表示卡牌
 * 前端使用对象格式 {suit: 'hearts', rank: 1} 表示卡牌
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
