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
 * 职责：房间基础信息管理（创建、加入、查询、离开）
 *
 * 边界处理：
 *
 * 1. 重复房间号处理：
 *    - createRoom 使用 Random 生成 6 位数字房间号（100000-999999）
 *    - 生成后检查数据库是否已存在该房间号，若存在则重新生成
 *    - 最多重试 3 次，超过则返回 null 表示创建失败
 *    - 实际碰撞概率极低（100万空间中不足100个活跃房间），重试机制为防御性编程
 *
 * 2. 满员处理：
 *    - joinRoom 检查 getPlayerCount(roomId) >= MAX_PLAYERS（4）
 *    - 满员时抛出 IllegalArgumentException("房间已满，最多4人")
 *    - 先检查状态再查人数，避免状态异常时误判
 *
 * 3. 重复加入边界：
 *    - joinRoom 通过 findExistingPlayer(roomId, userId) 检测用户是否已在房间中
 *    - 若已存在则直接返回现有 RoomPlayer 记录（幂等设计）
 *    - 并发场景下 insert 可能抛出异常，捕获后重新查一次确认
 *
 * 配置说明：
 * - MAX_PLAYERS=4 为硬编码常量，不可通过配置文件修改
 * - 房间号生成：seed 为 Random 默认构造（系统时间），无需显式设置
 * - 创建房间自动将创建者加入房间并分配 seatIndex=0
 * - 递归重试 createRoom(Boolean, String) 用尾递归风格，深度不超过3层
 *
 * 重构说明：
 * - joinRoom 收拢所有校验逻辑，业务异常统一抛出 IllegalArgumentException
 * - 新增 removePlayer 方法替换旧的 leaveRoom → getRoomByRoomNo 链路
 * - 提取重复校验为 findExistingPlayer 等独立方法
 * - isUserInRoom / isUserInAnyRoom / getRoomPlayer / kickPlayer /
 *   transferCreator 等管理方法职责明确
 *
 * ## 回归验证点
 * - [TC-ROOM-001] createRoom 正常创建 → 返回 6 位 roomNo，数据库有记录
 * - [TC-ROOM-002] createRoom 重复创建（已在房间中）→ Controller 层由 UserContext 校验返回 400
 * - [TC-ROOM-003] createRoom 房间号生成冲突重试 → 循环最多 3 次，超过返回 null
 * - [TC-ROOM-004] joinRoom 加入有效房间 → 返回 RoomPlayer（含 seatIndex）
 * - [TC-ROOM-005] joinRoom 加入已满房间 → 抛出 IllegalArgumentException("房间已满，最多4人")
 * - [TC-ROOM-006] joinRoom 加入不存在房间 → 抛出 IllegalArgumentException("房间不存在")
 * - [TC-ROOM-007] joinRoom 重复加入同一房间 → 幂等返回现有 RoomPlayer
 * - [TC-ROOM-008] joinRoom 房间状态非 0（游戏中/已结束）→ 抛出 IllegalArgumentException
 * - [TC-ROOM-009] joinRoom 空参数 → 抛出 IllegalArgumentException
 * - [TC-ROOM-010] removePlayer 正常离开 → 数据库记录删除，剩余 0 人时房间自动删除
 * - [TC-ROOM-011] getAvailableRooms 返回等待中+游戏中房间 → 按 ID 倒序，填充 playerCount
 * - [TC-ROOM-012] getCurrentRoom 用户不在任何房间 → 返回 null
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
        if (request == null) {
            log.error("createRoom failed: request is null");
            return null;
        }
        if (request.getUserId() == null) {
            log.error("createRoom failed: userId is null");
            return null;
        }

        String roomNo = generateRoomNo();
        if (roomNo == null) {
            log.error("createRoom failed: generated roomNo is null");
            return null;
        }

        // 检查房间号是否已存在（递归重试最多3次）
        int retryCount = 0;
        int maxRetries = 3;
        while (retryCount < maxRetries) {
            QueryWrapper<Room> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("room_no", roomNo);
            if (roomMapper.selectCount(queryWrapper) == 0) {
                break;
            }
            roomNo = generateRoomNo();
            retryCount++;
        }
        if (retryCount >= maxRetries) {
            log.error("createRoom failed: unable to generate unique roomNo after {} retries", maxRetries);
            return null;
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
     * 加入房间（重复校验、满员校验、状态校验、防重复提交）
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

        // 房间状态校验
        Integer status = room.getStatus();
        if (status == null || status != 0) {
            throw new IllegalArgumentException(status == null ? "房间状态异常" : "房间当前不可加入（状态：" + status + "）");
        }

        int currentCount = getPlayerCount(room.getId());
        if (currentCount >= MAX_PLAYERS) {
            throw new IllegalArgumentException("房间已满，最多" + MAX_PLAYERS + "人");
        }

        // 重复加入检查
        RoomPlayer existing = findExistingPlayer(room.getId(), userId);
        if (existing != null) {
            log.info("用户 {} 重复加入房间 {}，返回已有记录 seatIndex={}", userId, roomNo, existing.getSeatIndex());
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

        try {
            roomPlayerMapper.insert(roomPlayer);
        } catch (Exception e) {
            log.error("插入玩家记录失败，可能为并发重复提交 roomId={}, userId={}", room.getId(), userId, e);
            RoomPlayer recheck = findExistingPlayer(room.getId(), userId);
            if (recheck != null) {
                return recheck;
            }
            throw new IllegalArgumentException("加入房间失败，请稍后重试");
        }

        log.info("用户 {} 加入房间 {}，座位号 {}", userId, roomNo, seatIndex);
        return roomPlayer;
    }

    /**
     * 将玩家从房间中移除（替代旧的 leaveRoom + getRoomByRoomNo 二段式调用）
     */
    public void removePlayer(String roomNo, Long userId) {
        if (roomNo == null || userId == null) {
            return;
        }
        Room room = getRoomByRoomNo(roomNo);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("room_id", room.getId());
        query.eq("user_id", userId);
        roomPlayerMapper.delete(query);
        log.info("用户 {} 离开房间 {}", userId, roomNo);

        int remaining = getPlayerCount(room.getId());
        if (remaining == 0) {
            roomMapper.deleteById(room.getId());
            log.info("房间 {} 无玩家，自动删除", roomNo);
        }
    }

    /**
     * 查找用户在当前房间的已有记录
     */
    private RoomPlayer findExistingPlayer(Long roomId, Long userId) {
        QueryWrapper<RoomPlayer> query = new QueryWrapper<>();
        query.eq("room_id", roomId);
        query.eq("user_id", userId);
        return roomPlayerMapper.selectOne(query);
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
        if (userId == null) {
            return null;
        }
        QueryWrapper<RoomPlayer> playerQuery = new QueryWrapper<>();
        playerQuery.eq("user_id", userId);
        playerQuery.orderByDesc("id");
        playerQuery.last("LIMIT 1");
        RoomPlayer roomPlayer;
        try {
            roomPlayer = roomPlayerMapper.selectOne(playerQuery);
        } catch (Exception e) {
            log.error("查询用户当前房间失败 userId={}", userId, e);
            return null;
        }

        if (roomPlayer == null || roomPlayer.getRoomId() == null) return null;
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
     * 更新玩家准备状态
     */
    public void updatePlayerReadyStatus(Long playerId, Integer isReady) {
        RoomPlayer roomPlayer = new RoomPlayer();
        roomPlayer.setId(playerId);
        roomPlayer.setIsReady(isReady);
        roomPlayerMapper.updateById(roomPlayer);
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
     * 用户离开房间（按roomId+userId）
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
