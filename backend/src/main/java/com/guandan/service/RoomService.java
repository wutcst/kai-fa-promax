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
        Room room = getRoomByRoomNo(roomNo);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }

        if (room.getStatus() != 0) {
            throw new IllegalArgumentException("房间当前不可加入（状态：" + room.getStatus() + "）");
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
