package com.guandan.game.service;

import com.guandan.entity.GameCardDeal;
import com.guandan.entity.RoomPlayer;
import com.guandan.mapper.RoomPlayerMapper;
import com.guandan.service.RoomService;
import com.guandan.game.model.GameRoom;
import com.guandan.game.util.CardUtils;
import com.guandan.game.websocket.GameWebSocketServer;
import com.guandan.service.GameReferee;
import com.guandan.model.CardType;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏逻辑服务类
 * 负责人：成员A（核心引擎与逻辑）
 *
 * 负责游戏房间管理、发牌、出牌验证等核心逻辑
 * 注意：这里的房间管理是内存级别的（用于游戏进行中）
 * 数据库层面的房间管理由成员B的 RoomService 负责
 */
@Slf4j
@Service
public class GameLogicService {

    /**
     * 房间存储：roomId -> GameRoom
     * 注意：这是内存存储，用于游戏进行中的状态管理
     */
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    /**
     * 玩家到房间的映射：playerId -> roomId
     */
    private final Map<String, String> playerToRoom = new ConcurrentHashMap<>();

    /**
     * AI服务
     */
    @Autowired
    private AIService aiService;

    /**
     * 房间服务
     */
    @Autowired
    private RoomService roomService;

    /**
     * 房间玩家Mapper
     */
    @Autowired
    private RoomPlayerMapper roomPlayerMapper;

    /**
     * 房间Mapper
     */
    @Autowired
    private com.guandan.mapper.RoomMapper roomMapper;

    /**
     * 游戏裁判服务（规则验证）
     */
    @Autowired
    private GameReferee gameReferee;

    /**
     * 游戏算法服务（支持级牌和逢人配）
     */
    @Autowired
    private GameAlgorithm gameAlgorithm;

    @Autowired
    private com.guandan.service.GameRecordService gameRecordService;

    /**
     * 创建或加入房间
     * @param playerId 玩家ID
     * @param roomId 房间ID（如果为null则创建新房间）
     * @return 房间对象
     */
    public GameRoom joinRoom(String playerId, String roomId) {
        // 如果玩家已在其他房间，先移除
        String existingRoomId = playerToRoom.get(playerId);
        if (existingRoomId != null) {
            GameRoom existingRoom = rooms.get(existingRoomId);
            if (existingRoom != null) {
                existingRoom.getPlayerIds().remove(playerId);
                existingRoom.getHandCards().remove(playerId);
            }
        }

        // 如果没有指定房间ID，创建新房间
        if (roomId == null || roomId.isEmpty()) {
            roomId = "room_" + System.currentTimeMillis();
        }

        GameRoom room = rooms.computeIfAbsent(roomId, GameRoom::new);

        // 从数据库中读取最新的房间级别信息，并更新到GameRoom对象中
        try {
            String roomNo = roomId.replace("room_", "");
            if (roomNo.length() == 6) {
                com.guandan.entity.Room dbRoom = roomService.getRoomByRoomNo(roomNo);
                if (dbRoom != null) {
                    // 更新房间级别信息
                    room.setLevelTeamA(dbRoom.getLevelTeamA());
                    room.setLevelTeamB(dbRoom.getLevelTeamB());
                    // 根据级别计算级牌点数
                    // 正确的映射关系：
                    // 级别2→级牌2→levelCardRank=0 (因为RANKS[0] = "2")
                    // 级别3→级牌3→levelCardRank=1 (因为RANKS[1] = "3")
                    // 级别4→级牌4→levelCardRank=2 (因为RANKS[2] = "4")
                    // ...
                    // 级别14→级牌A→levelCardRank=12 (因为RANKS[12] = "A")
                    int level = Math.max(dbRoom.getLevelTeamA(), dbRoom.getLevelTeamB());
                    int levelCardRank;
                    if (level == 2) {
                        levelCardRank = 0; // 2对应levelCardRank 0
                    } else if (level >= 3 && level <= 14) {
                        levelCardRank = level - 2; // 3对应1, 4对应2, ..., 14对应12
                    } else {
                        levelCardRank = 0; // 默认打2
                    }
                    room.setLevelCardRank(levelCardRank);
                    log.info("从数据库更新房间级别信息: roomId={}, levelTeamA={}, levelTeamB={}, levelCardRank={}",
                            roomId, dbRoom.getLevelTeamA(), dbRoom.getLevelTeamB(), levelCardRank);
                }
            }
        } catch (Exception e) {
            log.error("从数据库获取房间级别信息失败", e);
        }

        // 添加玩家到房间
        if (room.addPlayer(playerId)) {
            playerToRoom.put(playerId, roomId);
            log.info("玩家 {} 加入房间 {}", playerId, roomId);

            // 如果是真实玩家（不是AI），添加到数据库
            if (!aiService.isAIPlayer(playerId)) {
                try {
                    // 解析房间号，获取数据库中的房间信息
                    String roomNo = roomId.replace("room_", "");
                    com.guandan.entity.Room dbRoom = roomService.getRoomByRoomNo(roomNo);
                    if (dbRoom != null) {
                        // 检查是否已经在数据库中
                        QueryWrapper<RoomPlayer> queryWrapper = new QueryWrapper<>();
                        queryWrapper.eq("room_id", dbRoom.getId());
                        queryWrapper.eq("user_id", Long.parseLong(playerId));
                        RoomPlayer existing = roomPlayerMapper.selectOne(queryWrapper);

                        if (existing == null) {
                            // 创建房间玩家记录
                            RoomPlayer roomPlayer = new RoomPlayer();
                            roomPlayer.setRoomId(dbRoom.getId());
                            roomPlayer.setUserId(Long.parseLong(playerId));
                            roomPlayer.setSeatIndex(room.getPlayerIds().size() - 1);
                            roomPlayer.setIsReady(0);
                            roomPlayer.setCardCount(0);
                            roomPlayerMapper.insert(roomPlayer);
                            log.info("玩家 {} 已添加到数据库房间 {}", playerId, dbRoom.getId());
                        }
                    }
                } catch (Exception e) {
                    log.error("添加玩家到数据库失败", e);
                }
            }
        } else {
            log.warn("玩家 {} 无法加入房间 {} (可能已满或已在房间中)", playerId, roomId);
        }

        return room;
    }

    /**
     * 开始游戏（当房间满4人时）
     * @param roomId 房间ID
     * @return 是否成功开始游戏
     */
    public boolean startGame(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.error("房间 {} 不存在", roomId);
            return false;
        }

        if (room.getPlayerIds().isEmpty()) {
            log.warn("房间 {} 没有玩家，无法开始游戏", roomId);
            return false;
        }

        // 点击准备就绪后，检查当前人数，不足4人则自动补AI
        while (room.getPlayerIds().size() < 4) {
            String aiPlayerId = aiService.generateAIPlayerId(room.getPlayerIds().size());
            if (room.addPlayer(aiPlayerId)) {
                playerToRoom.put(aiPlayerId, roomId);
                log.info("添加AI玩家 {} 到房间 {}", aiPlayerId, roomId);
            } else {
                log.warn("无法添加AI玩家 {} 到房间 {}", aiPlayerId, roomId);
                break;
            }
        }

        log.info("房间 {} 开始游戏，当前玩家数: {}", roomId, room.getPlayerIds().size());

        // 生成并洗牌
        List<Integer> deck = generateDeck();
        Collections.shuffle(deck);

        // 发牌：每人27张
        int cardsPerPlayer = 27;
        int cardIndex = 0;

        // 解析房间号，获取数据库中的房间信息
        Long dbRoomId = null;
        String roomNo = roomId.replace("room_", "");
        try {
            // 尝试将roomId解析为6位房间号
            if (roomNo.length() == 6) {
                com.guandan.entity.Room dbRoom = roomService.getRoomByRoomNo(roomNo);
                if (dbRoom != null) {
                    dbRoomId = dbRoom.getId();
                }
            }
        } catch (Exception e) {
            log.warn("获取数据库房间信息失败: {}", e.getMessage());
        }

        for (String playerId : room.getPlayerIds()) {
            List<Integer> hand = new ArrayList<>();
            for (int i = 0; i < cardsPerPlayer && cardIndex < deck.size(); i++) {
                hand.add(deck.get(cardIndex++));
            }
            // 排序手牌（便于调试）
            Collections.sort(hand);
            room.getHandCards().put(playerId, hand);

            // 记录级牌和逢人配信息
            int levelCardRank = room.getLevelCardRank();
            List<Integer> levelCards = CardUtils.getLevelCards(hand, levelCardRank);
            List<Integer> wildCards = CardUtils.getWildCards(hand, levelCardRank);

            log.info("玩家 {} 获得 {} 张手牌: {}",
                    playerId, hand.size(),
                    Arrays.toString(CardUtils.idsToStrings(hand.stream().mapToInt(i->i).toArray())));

            if (!levelCards.isEmpty()) {
                log.info("玩家 {} 的级牌: {}", playerId,
                    Arrays.toString(CardUtils.idsToStrings(levelCards.stream().mapToInt(i->i).toArray())));
            }
            if (!wildCards.isEmpty()) {
                log.info("玩家 {} 的逢人配: {}", playerId,
                    Arrays.toString(CardUtils.idsToStrings(wildCards.stream().mapToInt(i->i).toArray())));
            }

            // 保存发牌记录到数据库
            if (dbRoomId != null) {
                try {
                    GameCardDeal dealRecord = new GameCardDeal();
                    dealRecord.setRoomId(dbRoomId);
                    dealRecord.setPlayerId(playerId);
                    // 将手牌ID列表转换为逗号分隔的字符串
                    String cardIdsStr = String.join(",", hand.stream().map(String::valueOf).toArray(String[]::new));
                    dealRecord.setCardIds(cardIdsStr);

                    log.info("保存玩家 {} 的发牌记录成功", playerId);
                } catch (Exception e) {
                    log.error("保存发牌记录失败: {}", e.getMessage());
                }
            }
        }

        // 更新数据库中的房间状态为游戏中
        if (dbRoomId != null) {
            try {
                roomService.updateRoomStatus(dbRoomId, 1); // 1-游戏中
                log.info("更新数据库房间状态为游戏中: roomId={}", dbRoomId);
            } catch (Exception e) {
                log.error("更新房间状态失败", e);
            }
        }

        room.setStatus(GameRoom.GameStatus.PLAYING);
        log.info("房间 {} 游戏开始，级牌：{}", roomId, CardUtils.getRankName(room.getLevelCardRank()));
        return true;
    }

    /**
     * 获取当前回合玩家ID
     * @param gameRoom 游戏房间
     * @return 当前玩家ID
     */
    public String getCurrentPlayerId(GameRoom gameRoom) {
        List<String> playerIds = gameRoom.getPlayerIds();
        if (playerIds.isEmpty()) {
            return null;
        }
        int currentIndex = gameRoom.getCurrentPlayerIndex();
        return playerIds.get(currentIndex % playerIds.size());
    }

    /**
     * 切换到下一回合玩家
     * @param gameRoom 游戏房间
     * @return 下一回合玩家ID
     */
    public String nextTurn(GameRoom gameRoom) {
        if (gameRoom.getStatus() != GameRoom.GameStatus.PLAYING) {
            log.info("游戏已结束，不再处理回合切换");
            return null;
        }

        List<String> playerIds = gameRoom.getPlayerIds();
        if (playerIds == null || playerIds.isEmpty()) {
            return null;
        }

        int safety = 0;
        int maxSteps = Math.max(20, playerIds.size() * 20);
        while (safety++ < maxSteps) {
            int currentIndex = gameRoom.getCurrentPlayerIndex();
            if (currentIndex < 0 || currentIndex >= playerIds.size()) {
                currentIndex = 0;
            }
            String currentPlayerId = playerIds.get(currentIndex);
            log.info("当前玩家: {}, 最后出牌玩家: {}", currentPlayerId, gameRoom.getLastPlayerId());

            int nextIndex = (currentIndex + 1) % playerIds.size();
            String nextPlayerId = playerIds.get(nextIndex);

            // 检查下一个玩家是否已经出完牌
            List<Integer> nextPlayerHand = gameRoom.getHandCards().get(nextPlayerId);
            if (nextPlayerHand != null && nextPlayerHand.isEmpty()) {
                log.info("玩家 {} 已经出完牌，跳过", nextPlayerId);
                // 继续寻找下一个未出完牌的玩家
                gameRoom.setCurrentPlayerIndex(nextIndex);
                continue;
            }

            gameRoom.setCurrentPlayerIndex(nextIndex);

            if (!aiService.isAIPlayer(nextPlayerId)) {
                return nextPlayerId;
            }

            try {
                int delay = 500 + (int) (Math.random() * 1500);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("AI出牌延迟被中断", e);
            }

            List<Integer> cardsToPlay = aiService.playCards(gameRoom, nextPlayerId, gameRoom.getLevelCardRank());
            boolean played = false;
            if (cardsToPlay != null && !cardsToPlay.isEmpty()) {
                played = playCards(nextPlayerId, cardsToPlay);
            }

            if (!played) {
                GameWebSocketServer.handleAIPlayCard(nextPlayerId, null);
                gameRoom.incrementPassCount();
            }

            if (gameRoom.getStatus() != GameRoom.GameStatus.PLAYING) {
                return null;
            }

            String lastPlayerId = gameRoom.getLastPlayerId();
            String lastHandPlayerId = gameRoom.getLastHandPlayerId();

            // 检查是否需要清空桌面
            boolean shouldClearTable = false;
            if (gameRoom.getConsecutivePassCount() >= 3 && lastPlayerId != null) {
                // 连续3人跳过，清空桌面
                shouldClearTable = true;
            } else if (lastHandPlayerId != null) {
                // 检查上一手出牌者是否已经出完牌（头游）
                List<Integer> lastHandPlayerCards = gameRoom.getHandCards().get(lastHandPlayerId);
                boolean lastPlayerFinished = lastHandPlayerCards != null && lastHandPlayerCards.isEmpty();
                if (lastPlayerFinished) {
                    // 头游出完牌后，如果其他人都不要，立即清空桌面
                    shouldClearTable = true;
                }
            }

            if (shouldClearTable) {
                gameRoom.clearLastHandCards();
                try {
                    GameWebSocketServer.broadcastTableClear(gameRoom.getRoomId());
                    gameRoom.setTableCleared(false);
                } catch (Exception ignored) {
                }
                gameRoom.resetPassCount();

                int leadIndex = getLeadPlayerIndexAfterTrickEnd(gameRoom, lastHandPlayerId != null ? lastHandPlayerId : lastPlayerId);
                if (leadIndex >= 0 && !playerIds.isEmpty()) {
                    gameRoom.setCurrentPlayerIndex((leadIndex - 1 + playerIds.size()) % playerIds.size());
                }
            }
        }

        log.warn("nextTurn 自动推进超过限制，可能存在全AI/异常状态，currentIndex={}", gameRoom.getCurrentPlayerIndex());
        int finalIndex = gameRoom.getCurrentPlayerIndex();
        return playerIds.get(finalIndex % playerIds.size());
    }

    private int getLeadPlayerIndexAfterTrickEnd(GameRoom room, String lastPlayerId) {
        if (room == null || lastPlayerId == null) {
            return -1;
        }

        List<String> playerIds = room.getPlayerIds();
        if (playerIds == null || playerIds.isEmpty()) {
            return -1;
        }

        int lastIdx = playerIds.indexOf(lastPlayerId);
        if (lastIdx < 0) {
            return -1;
        }

        // 默认：上一手出牌者获得首出权
        int preferred = lastIdx;

        // 接风规则：如果上一手出牌者已走完（头游等），则队友接风（隔一位）
        List<Integer> lastHand = room.getHandCards().get(lastPlayerId);
        boolean lastFinished = lastHand != null && lastHand.isEmpty();
        if (lastFinished && playerIds.size() >= 4) {
            preferred = (lastIdx + 2) % playerIds.size();
        }

        // 从 preferred 开始找第一个还没走完的玩家
        for (int i = 0; i < playerIds.size(); i++) {
            int idx = (preferred + i) % playerIds.size();
            String pid = playerIds.get(idx);
            List<Integer> h = room.getHandCards().get(pid);
            if (h != null && !h.isEmpty()) {
                return idx;
            }
        }

        return preferred;
    }

    /**
     * 生成一副完整的牌（0-107）
     * @return 卡牌ID列表
     */
    private List<Integer> generateDeck() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 108; i++) {
            deck.add(i);
        }
        return deck;
    }

    /**
     * 玩家出牌
     * @param playerId 玩家ID
     * @param cardIds 要出的卡牌ID列表
     * @return 是否成功出牌
     */
    public boolean playCards(String playerId, List<Integer> cardIds) {
        String roomId = playerToRoom.get(playerId);
        if (roomId == null) {
            log.error("玩家 {} 不在任何房间中", playerId);
            return false;
        }

        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.error("房间 {} 不存在", roomId);
            return false;
        }

        if (room.getStatus() != GameRoom.GameStatus.PLAYING) {
            log.warn("房间 {} 不在游戏中，无法出牌", roomId);
            return false;
        }

        // 检查是否是当前玩家的回合
        String currentPlayerId = room.getCurrentPlayerId();
        if (!playerId.equals(currentPlayerId)) {
            log.warn("玩家 {} 尝试出牌，但当前回合是玩家 {}", playerId, currentPlayerId);
            return false;
        }

        if (cardIds == null || cardIds.isEmpty()) {
            log.info("玩家 {} 不出牌", playerId);

            room.incrementPassCount();

            if (room.getConsecutivePassCount() >= 3 && room.getLastPlayerId() != null) {
                String lastPlayerId = room.getLastPlayerId();
                log.info("所有玩家都跳过，让玩家 {} 继续出牌", lastPlayerId);

                room.clearLastHandCards();
                try {
                    GameWebSocketServer.broadcastTableClear(room.getRoomId());
                    room.setTableCleared(false);
                } catch (Exception ignored) {
                }
                room.resetPassCount();

                List<String> playerIds = room.getPlayerIds();
                int leadIndex = getLeadPlayerIndexAfterTrickEnd(room, lastPlayerId);
                if (leadIndex >= 0 && !playerIds.isEmpty()) {
                    room.setCurrentPlayerIndex((leadIndex - 1 + playerIds.size()) % playerIds.size());
                }
            }

            return true;
        }

        if (!gameReferee.isValidHand(cardIds, room.getLevelCardRank())) {
            log.warn("玩家 {} 尝试出牌失败：牌型不合法 {}",
                    playerId, Arrays.toString(CardUtils.idsToStrings(cardIds.stream().mapToInt(i -> i).toArray())));
            return false;
        }

        List<Integer> hand = room.getHandCards().get(playerId);
        if (hand == null) {
            log.warn("玩家 {} 尝试出牌失败：手牌为空", playerId);
            return false;
        }

        for (Integer cardId : cardIds) {
            if (!hand.contains(cardId)) {
                log.warn("玩家 {} 尝试出牌失败：手牌中不包含指定的卡牌 {}",
                        playerId, cardId);
                return false;
            }
        }

        List<Integer> lastHand = room.getLastHandCards();
        int levelCardRank = room.getLevelCardRank();
        log.info("canBeat调用前: lastHand={}, cardIds={}, levelCardRank={}",
                lastHand != null ? Arrays.toString(CardUtils.idsToStrings(lastHand.stream().mapToInt(i -> i).toArray())) : "无",
                Arrays.toString(CardUtils.idsToStrings(cardIds.stream().mapToInt(i -> i).toArray())),
                CardUtils.getRankName(levelCardRank));
        boolean canBeat = gameReferee.canBeat(lastHand, cardIds, levelCardRank);
        log.info("canBeat返回: {}", canBeat);
        if (!canBeat) {
            log.warn("玩家 {} 尝试出牌失败：无法管住上一手牌。上一手: {}, 当前: {}",
                    playerId,
                    lastHand != null ? Arrays.toString(CardUtils.idsToStrings(lastHand.stream().mapToInt(i -> i).toArray())) : "无",
                    Arrays.toString(CardUtils.idsToStrings(cardIds.stream().mapToInt(i -> i).toArray())));
            return false;
        }

        if (!room.removeCards(playerId, cardIds)) {
            log.warn("玩家 {} 尝试出牌失败：移除手牌时出错", playerId);
            return false;
        }

        log.info("玩家 {} 出牌: {}",
                playerId,
                Arrays.toString(CardUtils.idsToStrings(cardIds.stream().mapToInt(i -> i).toArray())));

        // 重置连续跳过次数
        room.resetPassCount();

        // 更新上一次出牌信息
        String cardType = CardUtils.getCardType(cardIds);
        Integer cardValue = CardUtils.getCardValue(cardIds);
        room.updateLastPlayedCards(playerId, cardType, cardValue);
        room.updateLastPlayedCardsWithList(playerId, cardIds);

        // 检查玩家是否出完牌，如果是第一个出完牌的玩家，记录为头游
        List<Integer> currentHand = room.getHandCards().get(playerId);
        if (currentHand != null && currentHand.isEmpty()) {
            if (room.getFirstFinishPlayerId() == null) {
                room.setFirstFinishPlayerId(playerId);
                log.info("玩家 {} 是第一个出完牌的（头游）", playerId);
            }
        }

        // 如果是AI玩家出牌，广播给所有玩家
        if (aiService.isAIPlayer(playerId)) {
            GameWebSocketServer.handleAIPlayCard(playerId, cardIds);
        }

        // 检查游戏是否结束：有3个玩家手牌为空
        checkGameEnd(room);

        return true;
    }

    /**
     * 检查游戏是否结束
     * 当有3个玩家手牌为空时，游戏结束
     * 第一个出完牌的玩家是头游（获胜者）
     * @param room 房间对象
     */
    private void checkGameEnd(GameRoom room) {
        if (room == null) {
            return;
        }

        int emptyHandCount = 0;

        for (String playerId : room.getPlayerIds()) {
            List<Integer> hand = room.getHandCards().get(playerId);
            if (hand == null || hand.isEmpty()) {
                emptyHandCount++;
            }
        }

        if (emptyHandCount >= 3) {
            // 使用第一个出完牌的玩家作为获胜者（头游）
            String winnerId = room.getFirstFinishPlayerId();
            if (winnerId == null) {
                log.warn("游戏结束但没有记录到头游玩家，使用最后一个出完牌的玩家");
                // 如果没有记录头游，使用最后一个出完牌的玩家作为备选
                for (String playerId : room.getPlayerIds()) {
                    List<Integer> hand = room.getHandCards().get(playerId);
                    if (hand == null || hand.isEmpty()) {
                        winnerId = playerId;
                    }
                }
            }

            log.info("游戏结束：房间 {}，头游（获胜者） {}", room.getRoomId(), winnerId);

            Long roomId = null;
            com.guandan.entity.Room dbRoom = null;
            try {
                String roomNo = room.getRoomId().replace("room_", "");
                dbRoom = roomService.getRoomByRoomNo(roomNo);
                if (dbRoom != null) {
                    roomId = dbRoom.getId();
                }
            } catch (Exception e) {
                log.warn("获取数据库房间ID失败: {}", e.getMessage());
            }

            if (roomId != null && winnerId != null) {
                Long winnerIdLong = Long.parseLong(winnerId);
                Integer score = room.getPlayerIds().size() == 4 ? 27 : 0;
                Integer levelTeamA = room.getLevelTeamA();
                Integer levelTeamB = room.getLevelTeamB();

                // 计算升级
                Map<String, Integer> playerRanks = new HashMap<>();
                for (String playerId : room.getPlayerIds()) {
                    Integer rank = room.getPlayerRank(playerId);
                    if (rank != null) {
                        playerRanks.put(playerId, rank);
                    }
                }

                // 判断获胜队伍
                Integer winnerRank = room.getPlayerRank(winnerId);
                boolean winnerIsTeamA = winnerRank != null && (
                        (winnerRank == 1 && (room.getPlayerRank(room.getPlayerIds().get(0)).equals(1)
                                || room.getPlayerRank(room.getPlayerIds().get(2)).equals(1))) ||
                        (winnerRank == 2 && (room.getPlayerRank(room.getPlayerIds().get(0)).equals(2)
                                || room.getPlayerRank(room.getPlayerIds().get(2)).equals(2))) ||
                        (winnerRank == 3 && (room.getPlayerRank(room.getPlayerIds().get(0)).equals(3)
                                || room.getPlayerRank(room.getPlayerIds().get(2)).equals(3))) ||
                        (winnerRank == 4 && (room.getPlayerRank(room.getPlayerIds().get(0)).equals(4)
                                || room.getPlayerRank(room.getPlayerIds().get(2)).equals(4)))
                );

                // 计算升级级数
                int upgradeLevels = 0;
                if (winnerIsTeamA) {
                    // A队获胜，计算A队的排名情况
                    int teamARank1 = getTeamPlayerRank(room, 0); // 座位0玩家的排名
                    int teamARank2 = getTeamPlayerRank(room, 2); // 座位2玩家的排名

                    if (teamARank1 == 1 && teamARank2 == 2) {
                        // 头游+二游，升3级
                        upgradeLevels = 3;
                    } else if (teamARank1 == 1 && teamARank2 == 3) {
                        // 头游+三游，升2级
                        upgradeLevels = 2;
                    } else if (teamARank1 == 1 && teamARank2 == 4) {
                        // 头游+末游，升1级
                        upgradeLevels = 1;
                    } else if (teamARank1 == 2 && teamARank2 == 1) {
                        // 二游+头游，升3级
                        upgradeLevels = 3;
                    } else if (teamARank1 == 3 && teamARank2 == 1) {
                        // 三游+头游，升2级
                        upgradeLevels = 2;
                    } else if (teamARank1 == 4 && teamARank2 == 1) {
                        // 末游+头游，升1级
                        upgradeLevels = 1;
                    }

                    if (upgradeLevels > 0) {
                        levelTeamA = Math.min(14, levelTeamA + upgradeLevels);
                        room.setLevelTeamA(levelTeamA);
                        log.info("A队获胜，排名 {}-{}，升{}级，新级牌: {}", teamARank1, teamARank2, upgradeLevels, levelTeamA);
                    }
                } else {
                    // B队获胜，计算B队的排名情况
                    int teamBRank1 = getTeamPlayerRank(room, 1); // 座位1玩家的排名
                    int teamBRank2 = getTeamPlayerRank(room, 3); // 座位3玩家的排名

                    if (teamBRank1 == 1 && teamBRank2 == 2) {
                        // 头游+二游，升3级
                        upgradeLevels = 3;
                    } else if (teamBRank1 == 1 && teamBRank2 == 3) {
                        // 头游+三游，升2级
                        upgradeLevels = 2;
                    } else if (teamBRank1 == 1 && teamBRank2 == 4) {
                        // 头游+末游，升1级
                        upgradeLevels = 1;
                    } else if (teamBRank1 == 2 && teamBRank2 == 1) {
                        // 二游+头游，升3级
                        upgradeLevels = 3;
                    } else if (teamBRank1 == 3 && teamBRank2 == 1) {
                        // 三游+头游，升2级
                        upgradeLevels = 2;
                    } else if (teamBRank1 == 4 && teamBRank2 == 1) {
                        // 末游+头游，升1级
                        upgradeLevels = 1;
                    }

                    if (upgradeLevels > 0) {
                        levelTeamB = Math.min(14, levelTeamB + upgradeLevels);
                        room.setLevelTeamB(levelTeamB);
                        log.info("B队获胜，排名 {}-{}，升{}级，新级牌: {}", teamBRank1, teamBRank2, upgradeLevels, levelTeamB);
                    }
                }

                // 更新数据库中的房间等级
                if (dbRoom != null) {
                    try {
                        LambdaUpdateWrapper<com.guandan.entity.Room> updateWrapper = new LambdaUpdateWrapper<>();
                        updateWrapper.eq(com.guandan.entity.Room::getId, dbRoom.getId())
                            .set(com.guandan.entity.Room::getLevelTeamA, levelTeamA)
                            .set(com.guandan.entity.Room::getLevelTeamB, levelTeamB);
                        roomMapper.update(null, updateWrapper);
                        log.info("更新数据库房间等级: roomId={}, levelTeamA={}, levelTeamB={}",
                                dbRoom.getId(), levelTeamA, levelTeamB);
                    } catch (Exception e) {
                        log.error("更新房间等级失败", e);
                    }
                }

                gameRecordService.saveGameRecord(roomId, winnerIdLong, score, levelTeamA, levelTeamB);
                GameWebSocketServer.broadcastGameEnd(room.getRoomId(), winnerIdLong, score, levelTeamA, levelTeamB);

                // 游戏结束后，将房间状态设置为WAITING，而不是FINISHED，以便继续显示在房间列表中
                room.setStatus(GameRoom.GameStatus.WAITING);
                room.resetLastPlayedCards();
                room.resetPassCount();
                room.clearLastHandCards();
                log.info("房间 {} 游戏结束，状态重置为 WAITING，准备下一局", room.getRoomId());
            }
        }
    }

    /**
     * 获取队伍玩家的排名
     * @param room 房间对象
     * @param seatIndex 座位索引
     * @return 排名
     */
    private int getTeamPlayerRank(GameRoom room, int seatIndex) {
        String playerId = room.getPlayerIds().get(seatIndex);
        Integer rank = room.getPlayerRank(playerId);
        return rank != null ? rank : 4;
    }

    /**
     * 获取玩家所在房间
     * @param playerId 玩家ID
     * @return 房间对象，如果不存在则返回null
     */
    public GameRoom getPlayerRoom(String playerId) {
        String roomId = playerToRoom.get(playerId);
        if (roomId == null) {
            return null;
        }
        return rooms.get(roomId);
    }

    /**
     * 获取房间内的玩家ID列表
     * @param roomId 房间ID
     * @return 玩家ID列表
     */
    public List<String> getPlayerIdsInRoom(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(room.getPlayerIds());
    }

    /**
     * 获取房间
     * @param roomId 房间ID
     * @return 房间对象
     */
    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 移除玩家（断开连接时）
     * @param playerId 玩家ID
     */
    public void removePlayer(String playerId) {
        String roomId = playerToRoom.remove(playerId);
        if (roomId != null) {
            GameRoom room = rooms.get(roomId);
            if (room != null) {
                room.getPlayerIds().remove(playerId);
                room.getHandCards().remove(playerId);
                log.info("玩家 {} 离开房间 {}", playerId, roomId);

                // 从数据库中删除房间玩家记录
                try {
                    String roomNo = roomId.replace("room_", "");
                    com.guandan.entity.Room dbRoom = roomService.getRoomByRoomNo(roomNo);
                    if (dbRoom != null) {
                        // 如果离开的是房主，则随机移交房主给其他仍在房间的真人玩家
                        try {
                            Long leavingUserId = Long.parseLong(playerId);
                            if (dbRoom.getCreatorId() != null && dbRoom.getCreatorId().equals(leavingUserId)) {
                                List<com.guandan.entity.RoomPlayer> remainingPlayers = roomService.getRoomPlayers(dbRoom.getId());
                                List<Long> candidates = new ArrayList<>();
                                for (com.guandan.entity.RoomPlayer rp : remainingPlayers) {
                                    if (rp.getUserId() != null && !rp.getUserId().equals(leavingUserId)) {
                                        candidates.add(rp.getUserId());
                                    }
                                }
                                if (!candidates.isEmpty()) {
                                    Long newCreatorId = candidates.get(new Random().nextInt(candidates.size()));
                                    roomService.updateRoomCreatorId(dbRoom.getId(), newCreatorId);
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        QueryWrapper<RoomPlayer> deleteQuery = new QueryWrapper<>();
                        deleteQuery.eq("room_id", dbRoom.getId());
                        deleteQuery.eq("user_id", Long.parseLong(playerId));
                        roomPlayerMapper.delete(deleteQuery);
                        log.info("从数据库删除房间玩家记录: roomId={}, userId={}", dbRoom.getId(), playerId);
                    }
                } catch (Exception e) {
                    log.error("从数据库删除房间玩家记录失败", e);
                }

                // 检查房间是否还有人类玩家
                boolean hasHumanPlayer = false;
                for (String pid : room.getPlayerIds()) {
                    if (!aiService.isAIPlayer(pid)) {
                        hasHumanPlayer = true;
                        break;
                    }
                }

                // 如果没有人类玩家了，将房间状态设置为已结束
                if (!hasHumanPlayer) {
                    log.info("房间 {} 没有人类玩家，将房间状态设置为已结束", roomId);
                    try {
                        String roomNo = roomId.replace("room_", "");
                        com.guandan.entity.Room dbRoom = roomService.getRoomByRoomNo(roomNo);
                        if (dbRoom != null) {
                            // 将房间状态设置为已结束
                            roomService.updateRoomStatus(dbRoom.getId(), 2);
                            log.info("更新房间状态为已结束: roomId={}", dbRoom.getId());
                        }
                    } catch (Exception e) {
                        log.error("更新房间状态失败", e);
                    }
                } else {
                    // 还有人类玩家，广播房间更新
                    try {
                        GameWebSocketServer.broadcastRoomInfoUpdateStatic(roomId);
                    } catch (Exception e) {
                        log.error("广播房间更新失败", e);
                    }
                }
            }
        }
    }

    // ============================================================
    //  新增：游戏状态查询接口（提升开局和联调可追踪性）
    // ============================================================

    /**
     * 获取游戏房间的完整状态快照（供前端/接口调用）
     * @param roomId 房间ID
     * @return 包含房间、玩家、手牌、出牌记录等完整信息的Map
     */
    public Map<String, Object> getGameState(String roomId) {
        Map<String, Object> state = new LinkedHashMap<>();
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            state.put("error", "房间不存在");
            return state;
        }

        state.put("roomId", room.getRoomId());
        state.put("status", room.getStatus().name());
        state.put("playerCount", room.getPlayerIds().size());
        state.put("playerIds", new ArrayList<>(room.getPlayerIds()));
        state.put("currentPlayerIndex", room.getCurrentPlayerIndex());
        state.put("currentPlayerId", room.getCurrentPlayerId());
        state.put("levelCardRank", room.getLevelCardRank());
        state.put("levelCardName", CardUtils.getRankName(room.getLevelCardRank()));
        state.put("levelTeamA", room.getLevelTeamA());
        state.put("levelTeamB", room.getLevelTeamB());

        // 各玩家手牌数量
        Map<String, Integer> handCardCounts = new LinkedHashMap<>();
        for (String pid : room.getPlayerIds()) {
            List<Integer> hand = room.getHandCards().get(pid);
            handCardCounts.put(pid, hand != null ? hand.size() : 0);
        }
        state.put("handCardCounts", handCardCounts);

        // 上一手牌信息
        state.put("lastCardType", room.getLastCardType());
        state.put("lastCardValue", room.getLastCardValue());
        state.put("lastPlayerId", room.getLastPlayerId());
        state.put("lastHandCards", room.getLastHandCards());

        // 完成排名的玩家
        state.put("firstFinishPlayerId", room.getFirstFinishPlayerId());
        state.put("secondFinishPlayerId", room.getSecondFinishPlayerId());
        state.put("thirdFinishPlayerId", room.getThirdFinishPlayerId());

        // 连续跳过次数
        state.put("consecutivePassCount", room.getConsecutivePassCount());

        log.info("获取游戏状态: roomId={}, status={}, playerCount={}",
                roomId, room.getStatus(), room.getPlayerIds().size());
        return state;
    }

    /**
     * 获取所有房间的简要状态列表（供大厅接口调用）
     * @return 房间列表
     */
    public List<Map<String, Object>> getAllRoomsBrief() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, GameRoom> entry : rooms.entrySet()) {
            GameRoom room = entry.getValue();
            Map<String, Object> brief = new LinkedHashMap<>();
            brief.put("roomId", room.getRoomId());
            brief.put("playerCount", room.getPlayerIds().size());
            brief.put("maxPlayers", 4);
            brief.put("status", room.getStatus().name());
            brief.put("levelTeamA", room.getLevelTeamA());
            brief.put("levelTeamB", room.getLevelTeamB());
            result.add(brief);
        }
        return result;
    }

    /**
     * 重置房间状态（用于一局结束后重新开始）
     * @param roomId 房间ID
     * @return 是否成功
     */
    public boolean resetRoom(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.error("重置失败：房间 {} 不存在", roomId);
            return false;
        }

        // 清空手牌
        room.getHandCards().clear();

        // 重置游戏状态
        room.setStatus(GameRoom.GameStatus.WAITING);
        room.setCurrentPlayerIndex(0);
        room.setFirstFinishPlayerId(null);
        room.setSecondFinishPlayerId(null);
        room.setThirdFinishPlayerId(null);
        room.resetLastPlayedCards();
        room.resetPassCount();
        room.clearLastHandCards();
        room.setLastHandCards(null);
        room.setLastHandPlayerId(null);
        room.setTableCleared(false);

        log.info("房间 {} 已重置，等待下一局", roomId);
        return true;
    }
}
