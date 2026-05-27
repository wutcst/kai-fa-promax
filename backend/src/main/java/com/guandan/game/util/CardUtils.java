package com.guandan.game.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * 卡牌工具类
 * 负责人：成员A（核心引擎与逻辑）
 *
 * 将卡牌ID (0-107) 转换为可读字符串，用于调试
 * 卡牌ID范围：0-107 (两副牌，每副54张)
 */
public class CardUtils {

    // 花色映射：0=方块, 1=梅花, 2=红桃, 3=黑桃
    private static final String[] SUITS = {"方块", "梅花", "红桃", "黑桃"};

    // 点数映射：掼蛋规则中2最小，所以 0-12 对应 2,3,4,5,6,7,8,9,10,J,Q,K,A,小王,大王
    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10",
                                           "J", "Q", "K", "A", "小王", "大王"};

    // 缓存映射，提高性能
    private static final Map<Integer, String> CARD_CACHE = new HashMap<>();

    static {
        // 初始化缓存
        for (int i = 0; i < 108; i++) {
            CARD_CACHE.put(i, convertIdToString(i));
        }
    }

    /**
     * 将卡牌ID转换为可读字符串
     * @param cardId 卡牌ID (0-107)
     * @return 可读字符串，如 "方块3", "红桃A", "小王" 等
     */
    public static String idToString(int cardId) {
        if (cardId < 0 || cardId > 107) {
            return "未知卡牌(" + cardId + ")";
        }
        return CARD_CACHE.get(cardId);
    }

    /**
     * 内部方法：将ID转换为字符串
     * 规则：
     * - 0-51: 第一副牌 (52张普通牌)
     * - 52-103: 第二副牌 (52张普通牌)
     * - 104-107: 四张大小王 (104,105=小王, 106,107=大王)
     */
    private static String convertIdToString(int cardId) {
        // 处理大小王
        if (cardId >= 104) {
            if (cardId <= 105) {
                return "小王";
            } else {
                return "大王";
            }
        }

        // 处理普通牌 (0-103)
        // 每副牌52张：0-51
        int deckIndex = cardId / 52;  // 0或1，表示第几副牌
        int cardInDeck = cardId % 52; // 在单副牌中的位置

        int suit = cardInDeck / 13;   // 花色 (0-3)
        int rank = cardInDeck % 13;   // 点数 (0-12)

        return SUITS[suit] + RANKS[rank];
    }

    /**
     * 批量转换卡牌ID数组为字符串数组
     * @param cardIds 卡牌ID数组
     * @return 字符串数组
     */
    public static String[] idsToStrings(int[] cardIds) {
        if (cardIds == null) {
            return new String[0];
        }
        String[] result = new String[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            result[i] = idToString(cardIds[i]);
        }
        return result;
    }
    
    /**
     * 获取点数名称
     * @param rankIndex 点数索引 (0-12对应3-A, 13-14对应小王/大王)
     * @return 点数名称
     */
    public static String getRankName(int rankIndex) {
        if (rankIndex >= 0 && rankIndex < RANKS.length) {
            return RANKS[rankIndex];
        }
        return "未知点数";
    }

    /**
     * 获取牌型
     * @param cardIds 卡牌ID列表
     * @return 牌型字符串
     */
    public static String getCardType(List<Integer> cardIds) {
        return getCardType(cardIds, 2); // 默认打2
    }

    /**
     * 获取牌型
     * @param cardIds 卡牌ID列表
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 牌型字符串
     */
    public static String getCardType(List<Integer> cardIds, int levelCardRank) {
        if (cardIds == null || cardIds.isEmpty()) {
            return null;
        }

        // 统计每个点数的数量
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Integer cardId : cardIds) {
            int rank = getRank(cardId);
            rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
        }

        // 检查是否为炸弹（4张及以上同点数）
        if (rankCount.values().stream().anyMatch(count -> count >= 4)) {
            return "炸弹";
        }

        // 检查牌的数量
        switch (cardIds.size()) {
            case 1:
                return "单张";
            case 2:
                // 检查是否为对子
                if (rankCount.size() == 1) {
                    return "对子";
                }
                return "无法识别";
            case 3:
                // 检查是否为三张
                if (rankCount.size() == 1) {
                    return "三张";
                }
                return "无法识别";
            case 5:
                // 检查是否为顺子、三带二、同花顺
                if (isStraight(cardIds)) {
                    return "顺子";
                }
                if (isThreeWithTwo(cardIds)) {
                    return "三带二";
                }
                if (isStraightFlush(cardIds)) {
                    return "同花顺";
                }
                return "无法识别";
            case 6:
                // 检查是否为钢板（连续三张）
                if (isSteelPlate(cardIds)) {
                    return "钢板";
                }
                return "无法识别";
            default:
                // 检查是否为顺子（5张以上）
                if (cardIds.size() >= 5 && isStraight(cardIds)) {
                    return "顺子";
                }
                return "无法识别";
        }
    }

    /**
     * 获取牌值（用于比较大小）
     * @param cardIds 卡牌ID列表
     * @return 牌值
     */
    public static Integer getCardValue(List<Integer> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return null;
        }

        // 统计每个点数的数量
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Integer cardId : cardIds) {
            int rank = getRank(cardId);
            rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
        }

        // 炸弹：返回最大点数
        if (rankCount.values().stream().anyMatch(count -> count >= 4)) {
            return rankCount.keySet().stream().max(Integer::compare).orElse(null);
        }

        // 单张：返回点数
        if (cardIds.size() == 1) {
            return getRank(cardIds.get(0));
        }

        // 对子：返回点数
        if (cardIds.size() == 2 && rankCount.size() == 1) {
            return rankCount.keySet().iterator().next();
        }

        // 三张：返回点数
        if (cardIds.size() == 3 && rankCount.size() == 1) {
            return rankCount.keySet().iterator().next();
        }

        // 顺子：返回最大点数
        if (isStraight(cardIds)) {
            return cardIds.stream().mapToInt(CardUtils::getRank).max().orElse(-1);
        }

        // 三带二：返回三张的点数
        if (isThreeWithTwo(cardIds)) {
            for (Map.Entry<Integer, Integer> entry : rankCount.entrySet()) {
                if (entry.getValue() == 3) {
                    return entry.getKey();
                }
            }
        }

        // 钢板：返回最大点数
        if (isSteelPlate(cardIds)) {
            return cardIds.stream().mapToInt(CardUtils::getRank).max().orElse(-1);
        }

        // 同花顺：返回最大点数
        if (isStraightFlush(cardIds)) {
            return cardIds.stream().mapToInt(CardUtils::getRank).max().orElse(-1);
        }

        return null;
    }

    /**
     * 获取卡牌的点数
     * @param cardId 卡牌ID
     * @return 点数 (0-12对应2-A, 13对应小王, 14对应大王)
     */
    public static int getRank(int cardId) {
        // 处理大小王
        if (cardId >= 104) {
            if (cardId <= 105) {
                return 13; // 小王
            } else {
                return 14; // 大王
            }
        }

        // 处理普通牌 (0-103)
        int cardInDeck = cardId % 52;
        return cardInDeck % 13;
    }

    /**
     * 获取卡牌的花色
     * @param cardId 卡牌ID
     * @return 花色 (0-3对应方块、梅花、红桃、黑桃，大小王返回-1)
     */
    public static int getSuit(int cardId) {
        if (cardId >= 104) {
            return -1; // 大小王无花色
        }
        int cardInDeck = cardId % 52;
        return cardInDeck / 13;
    }

    /**
     * 检查是否为顺子
     * @param cardIds 卡牌ID列表
     * @return 是否为顺子
     */
    private static boolean isStraight(List<Integer> cardIds) {
        if (cardIds.size() < 5) {
            return false;
        }

        // 获取所有点数并排序
        List<Integer> ranks = cardIds.stream()
                .map(CardUtils::getRank)
                .sorted()
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        // 顺子不能包含大小王，也不能包含2
        if (ranks.stream().anyMatch(r -> r >= 13 || r == 12)) {
            return false;
        }

        // 检查点数是否连续
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) != ranks.get(i - 1) + 1) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查是否为三带二
     * @param cardIds 卡牌ID列表
     * @return 是否为三带二
     */
    private static boolean isThreeWithTwo(List<Integer> cardIds) {
        if (cardIds.size() != 5) {
            return false;
        }

        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Integer cardId : cardIds) {
            int rank = getRank(cardId);
            rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
        }

        // 三带二应该有两个不同的点数，一个出现3次，一个出现2次
        return rankCount.size() == 2 && 
               rankCount.values().stream().anyMatch(count -> count == 3) &&
               rankCount.values().stream().anyMatch(count -> count == 2);
    }

    /**
     * 检查是否为钢板（连续三张）
     * @param cardIds 卡牌ID列表
     * @return 是否为钢板
     */
    private static boolean isSteelPlate(List<Integer> cardIds) {
        if (cardIds.size() != 6) {
            return false;
        }

        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Integer cardId : cardIds) {
            int rank = getRank(cardId);
            rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
        }

        // 钢板应该有两个不同的点数，每个出现3次
        if (rankCount.size() != 2) {
            return false;
        }

        // 检查每个点数是否出现3次
        if (!rankCount.values().stream().allMatch(count -> count == 3)) {
            return false;
        }

        // 检查点数是否连续
        List<Integer> ranks = new ArrayList<>(rankCount.keySet());
        java.util.Collections.sort(ranks);
        return ranks.get(1) == ranks.get(0) + 1;
    }

    /**
     * 检查是否为同花顺
     * @param cardIds 卡牌ID列表
     * @return 是否为同花顺
     */
    private static boolean isStraightFlush(List<Integer> cardIds) {
        if (cardIds.size() != 5) {
            return false;
        }

        // 检查是否为顺子
        if (!isStraight(cardIds)) {
            return false;
        }

        // 检查是否为同一花色
        int suit = getSuit(cardIds.get(0));
        if (suit == -1) {
            return false; // 包含大小王，不是同花顺
        }

        for (Integer cardId : cardIds) {
            if (getSuit(cardId) != suit) {
                return false;
            }
        }

        return true;
    }

    /**
     * 判断卡牌是否为级牌
     * @param cardId 卡牌ID
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 是否为级牌
     */
    public static boolean isLevelCard(int cardId, int levelCardRank) {
        int rank = getRank(cardId);
        // 大小王不是级牌
        if (rank >= 13) {
            return false;
        }
        return rank == levelCardRank;
    }

    /**
     * 判断卡牌是否为逢人配（万能牌）
     * 逢人配是红桃的级牌，可以作为任意牌使用
     * @param cardId 卡牌ID
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 是否为逢人配
     */
    public static boolean isWildCard(int cardId, int levelCardRank) {
        // 必须是级牌且是红桃花色
        return isLevelCard(cardId, levelCardRank) && getSuit(cardId) == 2;
    }

    /**
     * 获取卡牌在游戏中的等级（用于比较大小）
     * 级牌的等级最高
     * @param cardId 卡牌ID
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 卡牌等级
     */
    public static int getGameLevel(int cardId, int levelCardRank) {
        // 大王最大，返回16
        if (cardId >= 106) {
            return 16;
        }
        // 小王第二大，返回15
        if (cardId >= 104) {
            return 15;
        }
        // 级牌第三大，返回14
        if (isLevelCard(cardId, levelCardRank)) {
            return 14;
        }
        return getRank(cardId);
    }

    /**
     * 获取手牌中的所有级牌
     * @param cardIds 卡牌ID列表
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 级牌ID列表
     */
    public static List<Integer> getLevelCards(List<Integer> cardIds, int levelCardRank) {
        if (cardIds == null) {
            return new ArrayList<>(); // 空值保护
        }
        List<Integer> levelCards = new ArrayList<>();
        for (Integer cardId : cardIds) {
            if (isLevelCard(cardId, levelCardRank)) {
                levelCards.add(cardId);
            }
        }
        return levelCards;
    }

    /**
     * 获取手牌中的所有逢人配（万能牌）
     * @param cardIds 卡牌ID列表
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 逢人配ID列表
     */
    public static List<Integer> getWildCards(List<Integer> cardIds, int levelCardRank) {
        if (cardIds == null) {
            return new ArrayList<>(); // 空值保护
        }
        List<Integer> wildCards = new ArrayList<>();
        for (Integer cardId : cardIds) {
            if (isWildCard(cardId, levelCardRank)) {
                wildCards.add(cardId);
            }
        }
        return wildCards;
    }

    /**
     * 获取卡牌的显示名称（包含级牌和逢人配标记）
     * @param cardId 卡牌ID
     * @param levelCardRank 级牌点数 (0-12对应2-A)
     * @return 显示名称
     */
    public static String getDisplayName(int cardId, int levelCardRank) {
        String baseName = idToString(cardId);
        if (isLevelCard(cardId, levelCardRank)) {
            baseName += "(级)";
        }
        if (isWildCard(cardId, levelCardRank)) {
            baseName += "(逢人配)";
        }
        return baseName;
    }

    // ============================================================
    //  新增：发牌与手牌分析工具（提升开局可追踪性）
    // ============================================================

    /**
     * 生成一副新牌（0-107），包含两副标准扑克
     * @return 未洗牌的卡牌ID列表
     */
    public static List<Integer> createNewDeck() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 108; i++) {
            deck.add(i);
        }
        return deck;
    }

    /**
     * 洗牌
     * @param deck 卡牌列表
     * @param random 随机数生成器（传入null使用默认）
     */
    public static void shuffleDeck(List<Integer> deck, Random random) {
        if (deck == null) return;
        if (random != null) {
            Collections.shuffle(deck, random);
        } else {
            Collections.shuffle(deck);
        }
    }

    /**
     * 发牌：将牌堆均分给指定数量的玩家
     * @param deck 洗好的牌堆
     * @param playerCount 玩家数量
     * @return 每个玩家的手牌列表
     */
    public static List<List<Integer>> dealCards(List<Integer> deck, int playerCount) {
        List<List<Integer>> hands = new ArrayList<>();
        if (deck == null || playerCount <= 0) return hands;

        for (int i = 0; i < playerCount; i++) {
            hands.add(new ArrayList<>());
        }

        int totalCards = deck.size();
        int cardsPerPlayer = totalCards / playerCount;

        int index = 0;
        for (int i = 0; i < playerCount; i++) {
            for (int j = 0; j < cardsPerPlayer && index < totalCards; j++) {
                hands.get(i).add(deck.get(index++));
            }
        }

        return hands;
    }

    /**
     * 分析一手牌的牌型分布
     * @param handCards 手牌卡牌ID列表
     * @param levelCardRank 级牌点数
     * @return 牌型分布统计Map
     */
    public static Map<String, Object> analyzeHand(List<Integer> handCards, int levelCardRank) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        if (handCards == null || handCards.isEmpty()) {
            analysis.put("totalCards", 0);
            return analysis;
        }

        analysis.put("totalCards", handCards.size());

        // 统计各点数数量
        Map<Integer, Integer> rankCount = new TreeMap<>();
        for (int cardId : handCards) {
            int rank = getRank(cardId);
            rankCount.put(rank, rankCount.getOrDefault(rank, 0) + 1);
        }
        analysis.put("rankDistribution", new TreeMap<>(rankCount));

        // 级牌和逢人配
        List<Integer> levelCards = getLevelCards(handCards, levelCardRank);
        List<Integer> wildCards = getWildCards(handCards, levelCardRank);
        analysis.put("levelCardCount", levelCards.size());
        analysis.put("wildCardCount", wildCards.size());

        // 炸弹数量（4张及以上同点数）
        long bombCount = rankCount.values().stream().filter(c -> c >= 4).count();
        analysis.put("bombCount", bombCount);

        // 统计花色分布
        Map<String, Integer> suitDistribution = new LinkedHashMap<>();
        suitDistribution.put("方块", 0);
        suitDistribution.put("梅花", 0);
        suitDistribution.put("红桃", 0);
        suitDistribution.put("黑桃", 0);
        int jokerCount = 0;

        for (int cardId : handCards) {
            if (cardId >= 104) {
                jokerCount++;
            } else {
                int suit = getSuit(cardId);
                if (suit >= 0 && suit <= 3) {
                    suitDistribution.put(SUITS[suit], suitDistribution.get(SUITS[suit]) + 1);
                }
            }
        }
        analysis.put("suitDistribution", suitDistribution);
        analysis.put("jokerCount", jokerCount);

        return analysis;
    }

    /**
     * 将手牌按花色和点数排序（掼蛋常用排序：先按花色，再按点数）
     * @param cardIds 卡牌ID列表
     * @param levelCardRank 级牌点数（用于级牌优先）
     * @return 排序后的卡牌ID列表
     */
    public static List<Integer> sortHandCards(List<Integer> cardIds, int levelCardRank) {
        if (cardIds == null) return new ArrayList<>();
        List<Integer> sorted = new ArrayList<>(cardIds);
        sorted.sort((a, b) -> {
            // 级牌优先排在前面
            boolean aIsLevel = isLevelCard(a, levelCardRank);
            boolean bIsLevel = isLevelCard(b, levelCardRank);
            if (aIsLevel && !bIsLevel) return -1;
            if (!aIsLevel && bIsLevel) return 1;

            // 逢人配（红桃级牌）最优先
            boolean aIsWild = isWildCard(a, levelCardRank);
            boolean bIsWild = isWildCard(b, levelCardRank);
            if (aIsWild && !bIsWild) return -1;
            if (!aIsWild && bIsWild) return 1;

            // 按点数从大到小
            int rankA = getGameLevel(a, levelCardRank);
            int rankB = getGameLevel(b, levelCardRank);
            if (rankA != rankB) return rankB - rankA;

            // 同点数按花色
            return getSuit(a) - getSuit(b);
        });
        return sorted;
    }

    /**
     * 批量转换卡牌ID列表为可读字符串列表
     * @param cardIds 卡牌ID列表
     * @return 字符串列表
     */
    public static List<String> idsToStringList(List<Integer> cardIds) {
        if (cardIds == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (int id : cardIds) {
            result.add(idToString(id));
        }
        return result;
    }
}
