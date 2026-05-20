package com.guandan.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guandan.dto.NewGameRequest;
import com.guandan.entity.Room;
import com.guandan.entity.RoomPlayer;
import com.guandan.mapper.RoomMapper;
import com.guandan.mapper.RoomPlayerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * 房间服务类
 *
 * 职责：房间基础信息管理（创建、加入、查询）
 * 处理重复房间号、满员和重复加入边界的校验。
 */
@Slf4j
@Service
public class RoomService {

    @Resource
    private RoomMapper roomMapper;

    @Resource
    private RoomPlayerMapper roomPlayerMapper;

    private static final int MAX_PLAYERS = 4;

    /**
     * 创建新房间
     */
    public String createRoom(NewGameRequest request) {
        String roomNo = generateRoomNo();

        // 检查房间号是否已存在
        QueryWrapper<Room> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_no", roomNo);
        if (roomMapper.selectCount(queryWrapper) > 0) {
            return createRoom(request);
        }

        Room room = new Room();
        room.setRoomNo(roomNo);
        room.setStatus(0);
        room.setCreatorId(request.getUserId());
        room.setIsPrivate(request.getIsPrivate() != null ? request.getIsPrivate() : false);
        room.setLevelTeamA(2);
        room.setLevelTeamB(2);
        room.setConfig(request.getConfig());

        roomMapper.insert(room);

        // 自动将创建者加入房间（座位0）
        try {
            RoomPlayer roomPlayer = new RoomPlayer();
            roomPlayer.setRoomId(room.getId());
            roomPlayer.setUserId(request.getUserId());
            roomPlayer.setSeatIndex(0);
            roomPlayer.setIsReady(0);
            roomPlayer.setCardCount(0);
            roomPlayerMapper.insert(roomPlayer);
            log.info("创建者 {} 已自动加入房间 {}", request.getUserId(), roomNo);
        } catch (Exception e) {
            log.error("自动将创建者加入房间失败 roomId={}", room.getId(), e);
        }

        log.info("房间创建成功：roomNo={}, creatorId={}", roomNo, request.getUserId());
        return roomNo;
    }

    /**
     * 加入房间（重复校验、满员校验、状态校验）
     */
    public RoomPlayer joinRoom(String roomNo, Long userId) {
        // 参数空值校验
        if (roomNo == null || roomNo.trim().isEmpty()) {
            throw new IllegalArgumentException("房间号不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        Room room = getRoomByRoomNo(roomNo);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }

        // 房间号一致性校验
        if (!roomNo.equals(room.getRoomNo())) {
            throw new IllegalArgumentException("房间号不匹配");
        }

        // 房间状态校验
        Integer status = room.getStatus();
        if (status == null) {
            throw new IllegalArgumentException("房间状态异常");
        }
        if (status != 0) {
            throw new IllegalArgumentException("房间当前不可加入（状态：" + status + "）");
        }

        int currentCount = getPlayerCount(room.getId());
        if (currentCount >= MAX_PLAYERS) {
            throw new IllegalArgumentException("房间已满，最多" + MAX_PLAYERS + "人");
        }

        // 重复加入检查
        QueryWrapper<RoomPlayer> duplicateCheck = new QueryWrapper<>();
        duplicateCheck.eq("room_id", room.getId());
        duplicateCheck.eq("user_id", userId);
        RoomPlayer existing = roomPlayerMapper.selectOne(duplicateCheck);
        if (existing != null) {
            return existing;
        }

        // 找最小可用座位
        int seatIndex = findAvailableSeat(room.getId());

        RoomPlayer roomPlayer = new RoomPlayer();
        roomPlayer.setRoomId(room.getId());
        roomPlayer.setUserId(userId);
        roomPlayer.setSeatIndex(seatIndex);
        roomPlayer.setIsReady(0);
        roomPlayer.setCardCount(0);

        roomPlayerMapper.insert(roomPlayer);
        log.info("用户 {} 加入房间 {}，座位号 {}", userId, roomNo, seatIndex);
        return roomPlayer;
    }

    private int findAvailableSeat(Long roomId) {
        List<RoomPlayer> players = getRoomPlayers(roomId);
        boolean[] occupied = new boolean[MAX_PLAYERS];
        for (RoomPlayer p : players) {
            if (p.getSeatIndex() != null && p.getSeatIndex() >= 0 && p.getSeatIndex() < MAX_PLAYERS) {
                occupied[p.getSeatIndex()] = true;
            }
        }
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!occupied[i]) return i;
        }
        return players.size();
    }

    public Room getRoomByRoomNo(String roomNo) {
        QueryWrapper<Room> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_no", roomNo);
        return roomMapper.selectOne(queryWrapper);
    }

    public Room getRoomById(Long roomId) {
        return roomMapper.selectById(roomId);
    }

    public void updateRoomStatus(Long roomId, Integer status) {
        Room room = new Room();
        room.setId(roomId);
        room.setStatus(status);
        roomMapper.updateById(room);
    }

    public List<Room> getAvailableRooms() {
        QueryWrapper<Room> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("status", 0, 1);
        queryWrapper.orderByDesc("id");
        List<Room> rooms = roomMapper.selectList(queryWrapper);

        for (Room room : rooms) {
            int count = getPlayerCount(room.getId());
            room.setUserCount(count);
            room.setPlayerCount(count);
        }

        return rooms;
    }

    public int getPlayerCount(Long roomId) {
        QueryWrapper<RoomPlayer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_id", roomId);
        Long count = roomPlayerMapper.selectCount(queryWrapper);
        return count != null ? count.intValue() : 0;
    }

    public List<RoomPlayer> getRoomPlayers(Long roomId) {
        QueryWrapper<RoomPlayer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_id", roomId);
        return roomPlayerMapper.selectList(queryWrapper);
    }

    public Room getCurrentRoom(Long userId) {
        QueryWrapper<RoomPlayer> playerQuery = new QueryWrapper<>();
        playerQuery.eq("user_id", userId);
        playerQuery.orderByDesc("id");
        playerQuery.last("LIMIT 1");
        RoomPlayer roomPlayer = roomPlayerMapper.selectOne(playerQuery);

        if (roomPlayer == null) return null;
        return roomMapper.selectById(roomPlayer.getRoomId());
    }

    /**
     * 检查用户是否在指定房间中
     */
    public boolean isUserInRoom(Long userId, Long roomId) {
        if (userId == null || roomId == null) return false;
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        query.eq("room_id", roomId);
        return roomPlayerMapper.selectCount(query) > 0;
    }

    /**
     * 检查用户是否已在某个房间中（跨房间防重复）
     */
    public boolean isUserInAnyRoom(Long userId) {
        if (userId == null) return false;
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        return roomPlayerMapper.selectCount(query) > 0;
    }

    /**
     * 获取用户在房间中的记录
     */
    public RoomPlayer getRoomPlayer(Long roomId, Long userId) {
        if (roomId == null || userId == null) return null;
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("room_id", roomId);
        query.eq("user_id", userId);
        return roomPlayerMapper.selectOne(query);
    }

    /**
     * 获取房间详情（含玩家列表）
     */
    public Room getRoomDetail(Long roomId) {
        if (roomId == null) return null;
        Room room = roomMapper.selectById(roomId);
        if (room == null) return null;
        List<RoomPlayer> players = getRoomPlayers(roomId);
        room.setPlayers(players);
        room.setPlayerCount(players.size());
        room.setUserCount(players.size());
        return room;
    }

    /**
     * 用户离开房间
     */
    public void leaveRoom(Long roomId, Long userId) {
        if (roomId == null || userId == null) return;
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("room_id", roomId);
        query.eq("user_id", userId);
        roomPlayerMapper.delete(query);
        log.info("用户 {} 离开房间 {}", userId, roomId);

        int remaining = getPlayerCount(roomId);
        if (remaining == 0) {
            roomMapper.deleteById(roomId);
            log.info("房间 {} 无玩家，自动删除", roomId);
        }
    }

    /**
     * 踢出玩家（房主操作）
     */
    public boolean kickPlayer(Long roomId, Long operatorId, Long targetUserId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null || !room.getCreatorId().equals(operatorId)) {
            return false;
        }
        if (operatorId.equals(targetUserId)) return false;
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("room_id", roomId);
        query.eq("user_id", targetUserId);
        int affected = roomPlayerMapper.delete(query);
        log.info("用户 {} 被房主 {} 踢出房间 {}", targetUserId, operatorId, roomId);
        return affected > 0;
    }

    /**
     * 转移房主
     */
    public boolean transferCreator(Long roomId, Long currentCreatorId, Long newCreatorId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null || !room.getCreatorId().equals(currentCreatorId)) return false;
        if (!isUserInRoom(newCreatorId, roomId)) return false;
        room.setCreatorId(newCreatorId);
        roomMapper.updateById(room);
        log.info("房主转移：房间 {}，从 {} 到 {}", roomId, currentCreatorId, newCreatorId);
        return true;
    }

    private String generateRoomNo() {
        Random random = new Random();
        int roomNo = 100000 + random.nextInt(900000);
        return String.valueOf(roomNo);
    }

    public void deleteRoom(Long roomId) {
        QueryWrapper<RoomPlayer> playerQuery = new QueryWrapper<>();
        playerQuery.eq("room_id", roomId);
        roomPlayerMapper.delete(playerQuery);
        roomMapper.deleteById(roomId);
        log.info("房间 {} 已删除", roomId);
    }

    public Room createRoom(Boolean isPrivate, String config) {
        String roomNo = generateRoomNo();
        QueryWrapper<Room> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("room_no", roomNo);
        if (roomMapper.selectCount(queryWrapper) > 0) {
            return createRoom(isPrivate, config);
        }
        Room room = new Room();
        room.setRoomNo(roomNo);
        room.setStatus(0);
        room.setCreatorId(1L);
        room.setIsPrivate(isPrivate != null ? isPrivate : false);
        room.setLevelTeamA(2);
        room.setLevelTeamB(2);
        room.setConfig(config);
        roomMapper.insert(room);
        return room;
    }
}
