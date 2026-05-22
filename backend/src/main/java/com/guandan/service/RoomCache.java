package com.guandan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间缓存服务
 *
 * 职责：内存级别的房间状态、匹配队列和匹配结果缓存。
 * 使用 ConcurrentHashMap 保证线程安全。
 *
 * 注意：这里的房间管理是内存级别的（用于游戏进行中），
 * 数据库层面的房间管理由 RoomService 负责。
 */
@Slf4j
@Service
public class RoomCache {

    private static final Map<Long, Integer> roomStatusMap = new ConcurrentHashMap<>();
    private static final Map<Long, Set<Long>> roomPlayersMap = new ConcurrentHashMap<>();
    private static final Set<Long> matchQueue = ConcurrentHashMap.newKeySet();
    private static final Map<Long, String> matchResultMap = new ConcurrentHashMap<>();
    private static final Map<Long, Long> matchResultExpiryMap = new ConcurrentHashMap<>();
    private static final long MATCH_RESULT_EXPIRY_TIME = 5 * 60 * 1000; // 5分钟过期

    public void setRoomStatus(Long roomId, Integer status) {
        roomStatusMap.put(roomId, status);
    }

    public Integer getRoomStatus(Long roomId) {
        return roomStatusMap.get(roomId);
    }

    public void addPlayerToRoom(Long roomId, Long playerId) {
        roomPlayersMap.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public List<Long> getRoomPlayers(Long roomId) {
        Set<Long> players = roomPlayersMap.get(roomId);
        return players != null ? new ArrayList<>(players) : new ArrayList<>();
    }

    public void removePlayerFromRoom(Long roomId, Long playerId) {
        Set<Long> players = roomPlayersMap.get(roomId);
        if (players != null) {
            players.remove(playerId);
            if (players.isEmpty()) {
                roomPlayersMap.remove(roomId);
            }
        }
    }

    public void addToMatchQueue(Long playerId) {
        matchQueue.add(playerId);
    }

    public void removeFromMatchQueue(Long playerId) {
        matchQueue.remove(playerId);
    }

    public long getMatchQueueSize() {
        return matchQueue.size();
    }

    public boolean isInMatchQueue(Long playerId) {
        return matchQueue.contains(playerId);
    }

    public Set<Long> getMatchQueue() {
        return new HashSet<>(matchQueue);
    }

    public void clearMatchQueue() {
        matchQueue.clear();
    }

    public void setMatchResult(Long userId, String roomNo) {
        matchResultMap.put(userId, roomNo);
        matchResultExpiryMap.put(userId, System.currentTimeMillis() + MATCH_RESULT_EXPIRY_TIME);
    }

    public String getMatchResult(Long userId) {
        Long expiryTime = matchResultExpiryMap.get(userId);
        if (expiryTime != null && System.currentTimeMillis() > expiryTime) {
            removeMatchResult(userId);
            return null;
        }
        return matchResultMap.get(userId);
    }

    public void removeMatchResult(Long userId) {
        matchResultMap.remove(userId);
        matchResultExpiryMap.remove(userId);
    }
}
