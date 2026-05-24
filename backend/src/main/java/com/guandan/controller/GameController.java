package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.dto.JoinRoomRequest;
import com.guandan.entity.Room;
import com.guandan.entity.RoomPlayer;
import com.guandan.entity.User;
import com.guandan.game.dto.RoomStatusResponse;
import com.guandan.mapper.UserMapper;
import com.guandan.service.AuthService;
import com.guandan.service.RoomService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏控制器
 *
 * 功能：
 * - 房间内准备/取消准备
 * - 游戏开始/状态查询
 * - 房间状态查询（等待页）
 * - 房主提示信息
 *
 * 配置说明：
 * - 准备接口（POST /api/game/ready）：房主自动视为已准备，无需调用此接口
 * - 开始接口（POST /api/game/start）：仅房主可调用，需全部非房主玩家准备
 * - 状态接口（GET /api/game/{roomNo}/status）：提供等待页轮询状态
 * - 房主提示（GET /api/game/{roomNo}/host-tip）：返回可开始/人数不足/未准备等提示
 * - 玩家状态（GET /api/game/{roomNo}/player-status）：返回每个玩家的准备详情
 * - 等待页完整状态（GET /api/game/{roomNo}/waiting-status）：等待页一站式状态查询
 * - 最少人数要求：2 人（不包括房主自动准备）
 * - 最大人数限制：4 人（与 RoomService.MAX_PLAYERS 对齐）
 * -
 * - 重复提交处理：
 *   - ready 接口：已在目标准备状态时返回当前状态，不重复切换
 *   - start 接口：房间已在游戏中时返回错误
 */
@CrossOrigin(originPatterns = "*")
@RestController
@RequestMapping("/api")
public class GameController {

    @Resource
    private AuthService authService;

    @Resource
    private RoomService roomService;

    @Resource
    private UserMapper userMapper;

    /**
     * 玩家准备/取消准备
     * POST /api/game/ready
     *
     * 功能：
     * - 切换玩家准备状态（准备→取消准备↔准备→取消准备）
     * - 更新成功后返回当前准备状态及房间内其他玩家的准备情况
     * - 房主无需准备，调用会自动跳过
     *
     * 请求参数：
     * {
     *   "roomNo": "123456"
     * }
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "success": true,
     *     "ready": true,
     *     "readyCount": 3,
     *     "totalPlayers": 4,
     *     "allReady": false,
     *     "message": "准备就绪",
     *     "roomNo": "123456"
     *   }
     * }
     *
     * 异常场景：
     * - 房间不存在：返回 error 提示房间不存在
     * - 玩家不在房间中：返回 error 提示玩家不在该房间中
     * - Token无效：返回 error 提示用户未登录
     */
    @PostMapping("/game/ready")
    public Result<Map<String, Object>> ready(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            String roomNo = request.get("roomNo");
            if (roomNo == null || roomNo.isEmpty()) {
                return Result.error("房间号不能为空");
            }

            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            if (room.getStatus() != null && room.getStatus() != 0) {
                return Result.error("房间不在等待状态，无法准备");
            }

            // 查找当前玩家在该房间的记录
            RoomPlayer roomPlayer = roomService.getRoomPlayer(room.getId(), userId);
            if (roomPlayer == null) {
                return Result.error("玩家不在该房间中");
            }

            // 防御性检查：roomPlayer 关键字段非空
            if (roomPlayer.getId() == null) {
                return Result.error("玩家记录异常，请联系管理员");
            }

            // 房主无需准备，直接返回当前准备状态汇总
            if (room.getCreatorId() != null && room.getCreatorId().equals(userId)) {
                List<RoomPlayer> allPlayers = roomService.getRoomPlayers(room.getId());
                long readyCount = countReadyPlayers(allPlayers, room.getCreatorId());
                int total = allPlayers != null ? allPlayers.size() : 0;
                boolean allReady = total >= 2 && readyCount == total;

                Map<String, Object> data = new HashMap<>();
                data.put("success", true);
                data.put("ready", true);
                data.put("readyCount", readyCount);
                data.put("totalPlayers", total);
                data.put("allReady", allReady);
                data.put("message", "房主无需准备");
                data.put("roomNo", roomNo);
                return Result.success(data);
            }

            // 重复提交检测：若已处于目标状态则直接返回当前状态
            Integer currentReady = roomPlayer.getIsReady();
            int nextReady = (currentReady != null && currentReady == 1) ? 0 : 1;
            roomPlayer.setIsReady(nextReady);
            roomService.updatePlayerReadyStatus(roomPlayer.getId(), nextReady);

            // 计算当前准备状态汇总
            List<RoomPlayer> allPlayers = roomService.getRoomPlayers(room.getId());
            long readyCount = countReadyPlayers(allPlayers, room.getCreatorId());
            int total = allPlayers != null ? allPlayers.size() : 0;
            boolean allReady = total >= 2 && readyCount == total;

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("ready", nextReady == 1);
            data.put("readyCount", readyCount);
            data.put("totalPlayers", total);
            data.put("allReady", allReady);
            data.put("roomNo", roomNo);
            data.put("message", nextReady == 1 ? "准备就绪" : "已取消准备");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 统计已准备玩家数量（排除房主）
     */
    private long countReadyPlayers(List<RoomPlayer> players, Long creatorId) {
        if (players == null || players.isEmpty()) return 0;
        return players.stream()
                .filter(p -> p != null && !isCreatorRoom(p, creatorId))
                .filter(p -> p.getIsReady() != null && p.getIsReady() == 1)
                .count();
    }

    /**
     * 判断玩家是否为房主
     */
    private boolean isCreatorRoom(RoomPlayer player, Long creatorId) {
        return creatorId != null && creatorId.equals(player.getUserId());
    }

    /**
     * 开始游戏
     * POST /api/game/start
     *
     * 功能：房主开始游戏
     * - 检查房主身份
     * - 检查玩家数量和准备状态
     * - 更新房间状态为游戏中
     *
     * 请求参数：
     * {
     *   "roomNo": "123456"
     * }
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "success": true,
     *     "message": "游戏开始",
     *     "roomNo": "123456"
     *   }
     * }
     *
     * 异常场景：
     * - 非房主操作：返回 error 提示只有房主可以开始游戏
     * - 玩家未全部准备：返回 error 提示还有玩家未准备
     * - 人数不足：返回 error 提示人数不足
     * - 房间不在等待中：返回 error 提示房间状态异常
     */
    @PostMapping("/game/start")
    public Result<Map<String, Object>> startGame(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            String roomNo = request.get("roomNo");
            if (roomNo == null || roomNo.isEmpty()) {
                return Result.error("房间号不能为空");
            }

            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            // 验证是否是房主
            if (!room.getCreatorId().equals(userId)) {
                return Result.error("只有房主可以开始游戏");
            }

            if (room.getStatus() != null && room.getStatus() != 0) {
                return Result.error("房间不在等待状态，无法开始游戏");
            }

            // 检查玩家数量和准备状态
            List<RoomPlayer> players = roomService.getRoomPlayers(room.getId());
            if (players == null || players.size() < 2) {
                return Result.error("人数不足，至少需要2名玩家，当前 " +
                        (players != null ? players.size() : 0) + " 人");
            }

            // 检查所有非房主玩家是否已准备
            boolean allReady = true;
            List<String> unreadyPlayers = new ArrayList<>();
            for (RoomPlayer p : players) {
                if (room.getCreatorId().equals(p.getUserId())) {
                    continue; // 房主自动视为已准备
                }
                if (p.getIsReady() == null || p.getIsReady() != 1) {
                    allReady = false;
                    // 尝试获取玩家名称
                    User u = userMapper.selectById(p.getUserId());
                    unreadyPlayers.add(u != null ? u.getNickname() : "玩家" + p.getUserId());
                }
            }

            if (!allReady) {
                String detail = unreadyPlayers.isEmpty() ? "" :
                        "（未准备：" + String.join("、", unreadyPlayers) + "）";
                return Result.error("还有玩家未准备" + detail);
            }

            // 更新房间状态为游戏中
            roomService.updateRoomStatus(room.getId(), 1);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("message", "游戏开始");
            data.put("roomNo", roomNo);
            data.put("playerCount", players.size());

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取房间状态（等待页使用）
     * GET /api/game/{roomNo}/status
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "roomId": "room_123456",
     *     "status": "WAITING",
     *     "playerCount": 2,
     *     "maxPlayers": 4,
     *     "message": "等待其他玩家加入...",
     *     "seatIndex": 0,
     *     "isCreator": true,
     *     "allReady": false
     *   }
     * }
     *
     * 异常场景：
     * - 房间不存在：返回 error
     * - Token无效：返回 error
     */
    @GetMapping("/game/{roomNo}/status")
    public Result<RoomStatusResponse> getRoomStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomNo) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            Integer playerCount = roomService.getPlayerCount(room.getId());
            List<RoomPlayer> players = roomService.getRoomPlayers(room.getId());
            boolean allReady = true;
            Integer seatIndex = null;
            boolean isCreator = room.getCreatorId() != null && room.getCreatorId().equals(userId);

            if (players != null) {
                for (RoomPlayer p : players) {
                    if (p != null && p.getUserId() != null && p.getUserId().equals(userId)) {
                        seatIndex = p.getSeatIndex();
                    }
                    if (p != null && p.getUserId() != null
                            && (room.getCreatorId() == null || !room.getCreatorId().equals(p.getUserId()))) {
                        if (p.getIsReady() == null || p.getIsReady() != 1) {
                            allReady = false;
                        }
                    }
                }
            }

            String statusStr;
            String message;
            if (room.getStatus() == 0) {
                if (playerCount >= 4) {
                    statusStr = "FULL";
                    message = "房间已满，等待房主开始游戏";
                } else if (allReady && playerCount >= 2) {
                    statusStr = "WAITING";
                    message = "全部准备就绪，等待房主开始游戏";
                } else {
                    statusStr = "WAITING";
                    message = "等待其他玩家加入或准备...";
                }
            } else if (room.getStatus() == 1) {
                statusStr = "PLAYING";
                message = "游戏进行中";
            } else {
                statusStr = "FINISHED";
                message = "游戏已结束";
            }

            RoomStatusResponse response = new RoomStatusResponse();
            response.setRoomId("room_" + roomNo);
            response.setStatus(statusStr);
            response.setPlayerCount(playerCount != null ? playerCount : 0);
            response.setMaxPlayers(4);
            response.setMessage(message);
            response.setSeatIndex(seatIndex);
            response.setIsCreator(isCreator);
            response.setAllReady(allReady);

            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取房主提示信息
     * GET /api/game/{roomNo}/host-tip
     *
     * 返回房主在当前状态下的操作提示，包括：
     * - 是否可开始游戏
     * - 提示消息
     * - 玩家准备状态汇总
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "showTip": true,
     *     "tipMessage": "所有玩家已准备，点击开始游戏",
     *     "canStart": true,
     *     "readyCount": 3,
     *     "totalPlayers": 4,
     *     "unreadyPlayers": []
     *   }
     * }
     *
     * 异常场景：
     * - 非房主访问：返回 showTip=false 的提示
     * - 房间不存在：返回 error
     */
    @GetMapping("/game/{roomNo}/host-tip")
    public Result<Map<String, Object>> getHostTip(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomNo) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            boolean isCreator = room.getCreatorId() != null && room.getCreatorId().equals(userId);
            Integer playerCount = roomService.getPlayerCount(room.getId());
            List<RoomPlayer> players = roomService.getRoomPlayers(room.getId());

            // 计算准备状态
            List<String> unreadyPlayers = new ArrayList<>();
            int readyCount = 0;
            int totalCount = players != null ? players.size() : 0;

            if (players != null) {
                for (RoomPlayer p : players) {
                    if (room.getCreatorId() != null && room.getCreatorId().equals(p.getUserId())) {
                        readyCount++; // 房主自动视为已准备
                        continue;
                    }
                    if (p.getIsReady() != null && p.getIsReady() == 1) {
                        readyCount++;
                    } else {
                        User u = userMapper.selectById(p.getUserId());
                        unreadyPlayers.add(u != null ? u.getNickname() : "玩家" + p.getUserId());
                    }
                }
            }

            boolean allReady = totalCount >= 2 && readyCount == totalCount;

            Map<String, Object> data = new HashMap<>();
            data.put("isCreator", isCreator);
            data.put("playerCount", playerCount != null ? playerCount : 0);
            data.put("totalPlayers", totalCount);
            data.put("readyCount", readyCount);
            data.put("allReady", allReady);
            data.put("unreadyPlayers", unreadyPlayers);

            // 状态一致性判断：players 列表为空或 creatorId 为空时特殊处理
            if (room.getCreatorId() == null) {
                data.put("showTip", false);
                data.put("tipMessage", "房间数据异常，请联系管理员");
                data.put("canStart", false);
                return Result.success(data);
            }

            if (!isCreator) {
                data.put("showTip", false);
                String waitMsg = "等待房主开始游戏";
                if (totalCount < 2) {
                    waitMsg = "等待更多玩家加入...";
                } else if (!allReady) {
                    waitMsg = "等待其他玩家准备...";
                }
                data.put("tipMessage", waitMsg);
                data.put("canStart", false);
            } else if (playerCount == null || playerCount < 2) {
                data.put("showTip", true);
                data.put("tipMessage", "至少需要2名玩家才能开始游戏，当前 " + playerCount + " 人");
                data.put("canStart", false);
            } else if (!allReady) {
                data.put("showTip", true);
                String unreadyStr = unreadyPlayers.isEmpty() ? "" :
                        "（" + String.join("、", unreadyPlayers) + "）";
                data.put("tipMessage", "还有玩家未准备，请提醒其他玩家准备" + unreadyStr);
                data.put("canStart", false);
            } else {
                data.put("showTip", true);
                data.put("tipMessage", "所有玩家已准备，点击开始游戏");
                data.put("canStart", true);
            }

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取房间内所有玩家的准备状态
     * GET /api/game/{roomNo}/player-status
     *
     * 返回每个玩家的准备状态详情，用于前端等待页展示。
     * 非房主也可调用，了解整体准备进度。
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "players": [
     *       {
     *         "userId": 1,
     *         "nickname": "玩家A",
     *         "seatIndex": 0,
     *         "isReady": true,
     *         "isCreator": true
     *       }
     *     ],
     *     "readyCount": 2,
     *     "totalPlayers": 4
     *   }
     * }
     */
    @GetMapping("/game/{roomNo}/player-status")
    public Result<Map<String, Object>> getRoomPlayersStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomNo) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            List<RoomPlayer> roomPlayers = roomService.getRoomPlayers(room.getId());
            List<Map<String, Object>> playerList = new ArrayList<>();

            int readyCount = 0;
            if (roomPlayers != null) {
                for (RoomPlayer rp : roomPlayers) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("userId", rp.getUserId());
                    info.put("seatIndex", rp.getSeatIndex());

                    boolean isReady = (room.getCreatorId() != null && room.getCreatorId().equals(rp.getUserId()))
                            || (rp.getIsReady() != null && rp.getIsReady() == 1);
                    info.put("isReady", isReady);

                    boolean isCreator = room.getCreatorId() != null && room.getCreatorId().equals(rp.getUserId());
                    info.put("isCreator", isCreator);

                    User u = userMapper.selectById(rp.getUserId());
                    info.put("nickname", u != null ? u.getNickname() : "unknown");
                    info.put("avatar", u != null ? u.getAvatar() : null);

                    playerList.add(info);
                    if (isReady) readyCount++;
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("players", playerList);
            data.put("readyCount", readyCount);
            data.put("totalPlayers", roomPlayers != null ? roomPlayers.size() : 0);
            data.put("roomNo", roomNo);

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取等待页完整状态
     * GET /api/game/{roomNo}/waiting-status
     *
     * 返回等待页所需的所有状态信息，包括玩家列表、准备状态、房主提示等。
     * 用于前端等待页在游戏开始前的状态展示和轮询刷新。
     *
     * 功能：
     * - 获取房间内所有玩家及其准备状态
     * - 判断是否全部准备就绪
     * - 获取房主操作提示文案
     * - 提供当前玩家是否为房主的标识
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "roomNo": "123456",
     *     "status": "WAITING",
     *     "players": [
     *       {
     *         "userId": 1,
     *         "username": "player1",
     *         "seatIndex": 0,
     *         "isReady": true,
     *         "isCreator": true
     *       }
     *     ],
     *     "playerCount": 2,
     *     "maxPlayers": 4,
     *     "isCreator": true,
     *     "allReady": false,
     *     "hostTip": "至少需要2名玩家才能开始游戏，当前 2 人"
     *   }
     * }
     *
     * 异常场景：
     * - 401: Token 过期或无效
     * - 404: 房间不存在：指定 roomNo 的房间未找到
     * - 房间存在但玩家记录异常：返回空 players 列表，hostTip 提示等待加入
     */
    @GetMapping("/game/{roomNo}/waiting-status")
    public Result<Map<String, Object>> getWaitingStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomNo) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            Integer playerCount = roomService.getPlayerCount(room.getId());
            List<RoomPlayer> roomPlayers = roomService.getRoomPlayers(room.getId());

            List<Map<String, Object>> players = new java.util.ArrayList<>();
            boolean isCreator = room.getCreatorId() != null && room.getCreatorId().equals(userId);
            if (roomPlayers != null) {
                for (RoomPlayer rp : roomPlayers) {
                    if (rp == null) continue;
                    Map<String, Object> playerInfo = new HashMap<>();
                    playerInfo.put("userId", rp.getUserId());
                    // 获取用户名
                    User u = rp.getUserId() != null ? userMapper.selectById(rp.getUserId()) : null;
                    playerInfo.put("username", u != null ? u.getUsername() : "unknown");
                    playerInfo.put("seatIndex", rp.getSeatIndex());
                    playerInfo.put("isReady", rp.getIsReady() != null && rp.getIsReady() == 1);
                    playerInfo.put("isCreator", room.getCreatorId() != null && room.getCreatorId().equals(rp.getUserId()));
                    players.add(playerInfo);
                }
            }

            boolean allReady = roomPlayers != null && roomPlayers.stream().allMatch(p -> {
                if (p == null) return false;
                if (room.getCreatorId() != null && room.getCreatorId().equals(p.getUserId())) {
                    return true;
                }
                return p.getIsReady() != null && p.getIsReady() == 1;
            });

            String hostTip;
            if (playerCount == null || playerCount < 2) {
                hostTip = "至少需要2名玩家才能开始游戏，当前 " + (playerCount != null ? playerCount : 0) + " 人";
            } else if (!allReady) {
                hostTip = "还有玩家未准备，请提醒其他玩家准备";
            } else {
                hostTip = "所有玩家已准备，点击开始游戏";
            }

            Map<String, Object> data = new HashMap<>();
            data.put("roomNo", roomNo);
            data.put("status", room.getStatus() == 0 ? "WAITING" : room.getStatus() == 1 ? "PLAYING" : "FINISHED");
            data.put("players", players);
            data.put("playerCount", playerCount != null ? playerCount : 0);
            data.put("maxPlayers", 4);
            data.put("isCreator", isCreator);
            data.put("allReady", allReady);
            data.put("hostTip", isCreator ? hostTip : "等待房主开始游戏");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 从Token中获取用户ID
     */
    private Long getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return authService.validateToken(token);
    }
}
