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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏控制器
 *
 * 功能：
 * - 房间内准备/取消准备
 * - 游戏开始/状态查询
 * - 房间状态查询（等待页）
 * - 房主提示信息
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
     *     "message": "准备就绪",
     *     "roomNo": "123456"
     *   }
     * }
     *
     * 异常场景：
     * - 房间不存在：返回 error 提示房间不存在
     * - 玩家不在房间中：返回 error 提示玩家不在该房间中
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

            if (room.getStatus() != 0) {
                return Result.error("房间不在等待状态，无法准备");
            }

            // 查找当前玩家在该房间的记录
            RoomPlayer roomPlayer = roomService.getRoomPlayer(room.getId(), userId);
            if (roomPlayer == null) {
                return Result.error("玩家不在该房间中");
            }

            // 切换准备状态
            Integer currentReady = roomPlayer.getIsReady();
            int nextReady = (currentReady != null && currentReady == 1) ? 0 : 1;
            roomService.updatePlayerReadyStatus(roomPlayer.getId(), nextReady);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("ready", nextReady == 1);
            data.put("roomNo", roomNo);
            data.put("message", nextReady == 1 ? "准备就绪" : "已取消准备");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 开始游戏
     * POST /api/game/start
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
     *     "message": "游戏开始",
     *     "roomNo": "123456"
     *   }
     * }
     *
     * 异常场景：
     * - 非房主操作：返回 error 提示只有房主可以开始游戏
     * - 玩家未全部准备：返回 error 提示还有玩家未准备
     * - 人数不足：返回 error 提示人数不足
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

            if (room.getStatus() != 0) {
                return Result.error("房间不在等待状态，无法开始游戏");
            }

            // 检查玩家数量和准备状态
            List<RoomPlayer> players = roomService.getRoomPlayers(room.getId());
            if (players == null || players.size() < 2) {
                return Result.error("人数不足，至少需要2名玩家");
            }

            boolean allReady = players.stream().allMatch(p -> {
                if (room.getCreatorId().equals(p.getUserId())) {
                    return true;
                }
                return p.getIsReady() != null && p.getIsReady() == 1;
            });

            if (!allReady) {
                return Result.error("还有玩家未准备");
            }

            // 更新房间状态为游戏中
            roomService.updateRoomStatus(room.getId(), 1);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("message", "游戏开始");
            data.put("roomNo", roomNo);

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
            boolean isCreator = room.getCreatorId().equals(userId);

            if (players != null) {
                for (RoomPlayer p : players) {
                    if (p.getUserId().equals(userId)) {
                        seatIndex = p.getSeatIndex();
                    }
                    if (!room.getCreatorId().equals(p.getUserId())) {
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
     * 返回房主在当前状态下的操作提示
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "showTip": true,
     *     "tipMessage": "所有玩家已准备，点击开始游戏",
     *     "canStart": true
     *   }
     * }
     *
     * 异常场景：
     * - 非房主访问：提示非房主
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

            boolean isCreator = room.getCreatorId().equals(userId);
            Integer playerCount = roomService.getPlayerCount(room.getId());
            List<RoomPlayer> players = roomService.getRoomPlayers(room.getId());
            boolean allReady = players != null && players.stream().allMatch(p -> {
                if (room.getCreatorId().equals(p.getUserId())) {
                    return true;
                }
                return p.getIsReady() != null && p.getIsReady() == 1;
            });

            Map<String, Object> data = new HashMap<>();
            data.put("isCreator", isCreator);
            data.put("playerCount", playerCount);
            data.put("allReady", allReady);

            if (!isCreator) {
                data.put("showTip", false);
                data.put("tipMessage", "等待房主开始游戏");
                data.put("canStart", false);
            } else if (playerCount == null || playerCount < 2) {
                data.put("showTip", true);
                data.put("tipMessage", "至少需要2名玩家才能开始游戏，当前 " + playerCount + " 人");
                data.put("canStart", false);
            } else if (!allReady) {
                data.put("showTip", true);
                data.put("tipMessage", "还有玩家未准备，请提醒其他玩家准备");
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
     * 获取等待页完整状态
     * GET /api/game/{roomNo}/waiting-status
     *
     * 返回等待页所需的所有状态信息
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "roomNo": "123456",
     *     "status": "WAITING",
     *     "players": [...],
     *     "playerCount": 2,
     *     "maxPlayers": 4,
     *     "hostTip": "等待其他玩家加入..."
     *   }
     * }
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
            boolean isCreator = room.getCreatorId().equals(userId);
            if (roomPlayers != null) {
                for (RoomPlayer rp : roomPlayers) {
                    Map<String, Object> playerInfo = new HashMap<>();
                    playerInfo.put("userId", rp.getUserId());
                    // 获取用户名
                    User u = userMapper.selectById(rp.getUserId());
                    playerInfo.put("username", u != null ? u.getUsername() : "unknown");
                    playerInfo.put("seatIndex", rp.getSeatIndex());
                    playerInfo.put("isReady", rp.getIsReady() != null && rp.getIsReady() == 1);
                    playerInfo.put("isCreator", room.getCreatorId().equals(rp.getUserId()));
                    players.add(playerInfo);
                }
            }

            boolean allReady = roomPlayers != null && roomPlayers.stream().allMatch(p -> {
                if (room.getCreatorId().equals(p.getUserId())) {
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
