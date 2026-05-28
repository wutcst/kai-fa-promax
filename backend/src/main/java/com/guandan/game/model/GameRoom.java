package com.guandan.game.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏房间模型
 * 负责人：成员A（核心引擎与逻辑）
 *
 * <p>存储房间内的玩家信息、手牌和游戏状态。提供房间生命周期管理，包括：
 * <ul>
 *   <li>玩家加入/离开房间</li>
 *   <li>手牌分配与移除</li>
 *   <li>出牌状态追踪（上一手牌、当前玩家、跳过计数）</li>
 *   <li>游戏排名与升级计算</li>
 * </ul>
 *
 * <p><b>异常场景：</b>
 * <ul>
 *   <li>玩家重复加入同一房间 → 返回 false</li>
 *   <li>房间满员时尝试加入 → 返回 false</li>
 *   <li>查询 null 玩家手牌 → 返回空列表而非 NPE</li>
 *   <li>currentPlayerIndex 越界 → 自动重置为 0</li>
 * </ul>
 */
@Data
public class GameRoom {

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 玩家ID列表（最多4人）
     */
    private List<String> playerIds;

    /**
     * 玩家手牌映射：playerId -> List<Integer> (卡牌ID列表)
     */
    private Map<String, List<Integer>> handCards;

    /**
     * 游戏状态
     * WAITING: 等待玩家加入
     * PLAYING: 游戏中
     * FINISHED: 游戏结束
     */
    private GameStatus status;

    /**
     * 当前出牌玩家索引
     */
    private int currentPlayerIndex;

    /**
     * 级牌点数
     * 0-12 对应 3,4,5,6,7,8,9,10,J,Q,K,A,2
     * 表示级牌是2
     */
    private int levelCardRank;

    /**
     * 上一次出牌的类型
     */
    private String lastCardType;

    /**
     * 上一次出牌的牌值
     */
    private Integer lastCardValue;

    /**
     * 上一次出牌的玩家ID
     */
    private String lastPlayerId;

    /**
     * 上一手牌（用于比牌判断）
     */
    private List<Integer> lastHandCards;

    /**
     * 上一手牌的出牌玩家ID（用于检测是否所有玩家都跳过）
     */
    private String lastHandPlayerId;

    /**
     * 连续跳过次数（用于检测是否所有玩家都跳过）
     */
    private int consecutivePassCount;

    /**
     * 是否刚刚清空桌面（上一手牌）
     * 用于通知前端清除桌面展示
     */
    private boolean tableCleared;

    /**
     * 第一个出完牌的玩家ID（头游）
     */
    private String firstFinishPlayerId;

    /**
     * 第二个出完牌的玩家ID（二游）
     */
    private String secondFinishPlayerId;

    /**
     * 第三个出完牌的玩家ID（三游）
     */
    private String thirdFinishPlayerId;

    /**
     * A队级别
     */
    private Integer levelTeamA;

    /**
     * B队级别
     */
    private Integer levelTeamB;

    /**
     * 构造函数
     */
    public GameRoom(String roomId) {
        this.roomId = roomId;
        this.playerIds = new ArrayList<>();
        this.handCards = new ConcurrentHashMap<>();
        this.status = GameStatus.WAITING;
        this.currentPlayerIndex = 0;
        this.levelCardRank = 0; // 0对应2，表示打2级
        this.lastCardType = null;
        this.lastCardValue = null;
        this.lastPlayerId = null;
        this.lastHandCards = null;
        this.lastHandPlayerId = null;
        this.consecutivePassCount = 0;
        this.tableCleared = false;
        this.firstFinishPlayerId = null;
        this.secondFinishPlayerId = null;
        this.thirdFinishPlayerId = null;
        this.levelTeamA = 2; // 默认级别为2
        this.levelTeamB = 2; // 默认级别为2
    }

    /**
     * 更新上一次出牌信息
     * @param playerId 玩家ID
     * @param cardType 牌型
     * @param cardValue 牌值
     */
    public void updateLastPlayedCards(String playerId, String cardType, Integer cardValue) {
        this.lastPlayerId = playerId;
        this.lastCardType = cardType;
        this.lastCardValue = cardValue;
    }

    /**
     * 更新上一次出牌信息（包含牌列表及空值保护）
     * @param playerId 玩家ID
     * @param cardIds 出牌列表
     */
    public void recordPlayedCards(String playerId, List<Integer> cardIds) {
        if (playerId == null) {
            return; // 空值保护
        }
        this.lastPlayerId = playerId;
        this.lastHandCards = (cardIds == null) ? null : new java.util.ArrayList<>(cardIds);
    }

    /**
     * 重置上一次出牌信息（同 resetLastPlayedCards 别名，提升可读性）
     */
    public void clearPlayedCards() {
        resetLastPlayedCards();
    }

    /**
     * 重置上一次出牌信息
     */
    public void resetLastPlayedCards() {
        this.lastPlayerId = null;
        this.lastCardType = null;
        this.lastCardValue = null;
    }

    /**
     * 添加玩家
     * @param playerId 玩家ID
     * @return 是否添加成功
     */
    public boolean addPlayer(String playerId) {
        if (playerIds.size() >= 4) {
            return false; // 房间已满
        }
        if (playerIds.contains(playerId)) {
            return false; // 玩家已在房间中
        }
        playerIds.add(playerId);
        handCards.put(playerId, new ArrayList<>());
        return true;
    }

    /**
     * 检查房间是否已满（4人）
     */
    public boolean isFull() {
        return playerIds.size() >= 4;
    }

    /**
     * 获取玩家手牌（含空值保护）
     * @param playerId 玩家ID
     * @return 手牌列表，若playerId为空返回空列表
     */
    public List<Integer> getPlayerHandCards(String playerId) {
        if (playerId == null || handCards == null) {
            return new ArrayList<>();
        }
        return handCards.getOrDefault(playerId, new ArrayList<>());
    }

    /**
     * 移除玩家手牌中的指定卡牌
     * @param playerId 玩家ID
     * @param cardIds 要移除的卡牌ID列表
     * @return 是否成功移除（所有卡牌都在手牌中）
     */
    public boolean removeCards(String playerId, List<Integer> cardIds) {
        List<Integer> hand = handCards.get(playerId);
        if (hand == null) {
            return false;
        }

        // 检查所有卡牌是否都在手牌中
        for (Integer cardId : cardIds) {
            if (!hand.contains(cardId)) {
                return false;
            }
        }

        // 移除卡牌
        hand.removeAll(cardIds);
        return true;
    }

    /**
     * 游戏状态枚举
     */
    public enum GameStatus {
        WAITING,    // 等待玩家
        PLAYING,    // 游戏中
        FINISHED    // 游戏结束
    }

    /**
     * 获取当前轮到的玩家ID
     * @return 当前玩家的ID，如果玩家列表为空则返回null
     */
    public String getCurrentPlayerId() {
        if (playerIds == null || playerIds.isEmpty()) {
            return null;
        }
        if (currentPlayerIndex < 0 || currentPlayerIndex >= playerIds.size()) {
            currentPlayerIndex = 0; // 重置索引
        }
        return playerIds.get(currentPlayerIndex);
    }

    /**
     * 检查是否是当前玩家的回合
     * @param playerId 玩家ID
     * @return 是否是当前玩家
     */
    public boolean isCurrentPlayer(String playerId) {
        String currentPlayerId = getCurrentPlayerId();
        return currentPlayerId != null && currentPlayerId.equals(playerId);
    }

    /**
     * 切换到下一个玩家
     */
    public void nextPlayer() {
        if (playerIds != null && !playerIds.isEmpty()) {
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        }
    }

    /**
     * 重置连续跳过次数
     */
    public void resetPassCount() {
        this.consecutivePassCount = 0;
    }

    /**
     * 增加连续跳过次数
     */
    public void incrementPassCount() {
        this.consecutivePassCount++;
    }

    /**
     * 获取连续跳过次数
     * @return 连续跳过次数
     */
    public int getConsecutivePassCount() {
        return this.consecutivePassCount;
    }

    /**
     * 清空上一手牌
     */
    public void clearLastHandCards() {
        this.lastHandCards = null;
        this.lastHandPlayerId = null;
        this.tableCleared = true;
    }

    /**
     * 获取上一手牌
     * @return 上一手牌列表
     */
    public List<Integer> getLastHandCards() {
        return lastHandCards;
    }

    /**
     * 更新玩家排名（出完牌时调用）
     * @param playerId 出完牌的玩家ID
     */
    public void updatePlayerRank(String playerId) {
        if (firstFinishPlayerId == null) {
            firstFinishPlayerId = playerId;
        } else if (secondFinishPlayerId == null && !playerId.equals(firstFinishPlayerId)) {
            secondFinishPlayerId = playerId;
        } else if (thirdFinishPlayerId == null && !playerId.equals(firstFinishPlayerId) 
                && !playerId.equals(secondFinishPlayerId)) {
            thirdFinishPlayerId = playerId;
        }
    }

    /**
     * 获取玩家排名：1-头游, 2-二游, 3-三游, 4-末游
     * @param playerId 玩家ID
     * @return 排名，未找到返回null
     */
    public Integer getPlayerRank(String playerId) {
        if (playerId.equals(firstFinishPlayerId)) {
            return 1;
        } else if (playerId.equals(secondFinishPlayerId)) {
            return 2;
        } else if (playerId.equals(thirdFinishPlayerId)) {
            return 3;
        } else if (playerIds != null && playerIds.contains(playerId)) {
            return 4; // 末游
        }
        return null;
    }

    /**
     * 重置所有游戏状态（用于一局结束后重新开始）
     *
     * <p>恢复所有字段到初始值，包括：
     * 状态设为 WAITING，当前玩家索引归零，级牌点数归零，
     * 清空上一手牌信息、跳过计数、排名记录。
     * AB 队级别恢复为 2。
     *
     * <p><b>注意：</b>该方法不影响 playerIds 和 handCards 引用，
     * 调用方需自行清空手牌数据。
     */
    public void resetGameState() {
        this.status = GameStatus.WAITING;
        this.currentPlayerIndex = 0;
        this.levelCardRank = 0;
        this.lastCardType = null;
        this.lastCardValue = null;
        this.lastPlayerId = null;
        this.lastHandCards = null;
        this.lastHandPlayerId = null;
        this.consecutivePassCount = 0;
        this.tableCleared = false;
        this.firstFinishPlayerId = null;
        this.secondFinishPlayerId = null;
        this.thirdFinishPlayerId = null;
        this.levelTeamA = 2;
        this.levelTeamB = 2;
    }

    /**
     * 获取房间中尚未出完牌的玩家数量
     * @return 手牌非空的玩家数量
     */
    public int getActivePlayerCount() {
        int count = 0;
        for (String pid : playerIds) {
            List<Integer> hand = handCards.get(pid);
            if (hand != null && !hand.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断指定玩家是否已出完所有手牌
     * @param playerId 玩家ID
     * @return 是否已出完
     */
    public boolean isPlayerFinished(String playerId) {
        List<Integer> hand = handCards.get(playerId);
        return hand != null && hand.isEmpty();
    }

    /**
     * 获取所有未出完牌的玩家ID
     * @return 活跃玩家ID列表
     */
    public List<String> getActivePlayerIds() {
        List<String> active = new ArrayList<>();
        for (String pid : playerIds) {
            List<Integer> hand = handCards.get(pid);
            if (hand != null && !hand.isEmpty()) {
                active.add(pid);
            }
        }
        return active;
    }

    /**
     * 判断房间是否可开始游戏（至少2名真人玩家，含状态一致性检查）
     * @return 是否可开始
     */
    public boolean canStartGame() {
        if (playerIds == null || playerIds.isEmpty()) {
            return false;
        }
        if (status != GameStatus.WAITING) {
            return false; // 非等待状态不能开始
        }
        return playerIds.size() >= 2;
    }
}
