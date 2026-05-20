package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.dto.JoinRoomRequest;
import com.guandan.dto.NewGameRequest;
import com.guandan.dto.LeaveRoomRequest;
import com.guandan.entity.Room;
import com.guandan.entity.RoomPlayer;
import com.guandan.service.AuthService;
import com.guandan.service.RoomService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间控制器
 *
 * 职责：房间基础管理（创建、加入、查询）
 * 处理重复房间号、满员和重复加入的边界情况。
 */
@CrossOrigin(originPatterns = "*")
@RestController
@RequestMapping("/api")
public class RoomController {

    @Resource
    private RoomService roomService;

    @Resource
    private AuthService authService;

    /**
     * 创建新游戏/房间
     *
     * POST /api/new-game
     *
     * @param token   用户认证Token
     * @param request 创建房间请求
     * @return 包含房间号的响应
     */
    @PostMapping("/new-game")
    public Result<Map<String, String>> createGame(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody NewGameRequest request) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            // 检查用户是否已经在房间中（防止重复创建）
            Room currentRoom = roomService.getCurrentRoom(userId);
            if (currentRoom != null && currentRoom.getStatus() == 0) {
                return Result.error("您已在房间 " + currentRoom.getRoomNo() + " 中，请先退出再加入其他房间");
            }

            request.setUserId(userId);
            String roomNo = roomService.createRoom(request);

            Map<String, String> data = new HashMap<>();
            data.put("roomNo", roomNo);
            data.put("message", "房间创建成功");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有可用房间列表
     *
     * GET /api/rooms
     *
     * @param token 用户认证Token
     * @return 等待中的房间列表
     */
    @GetMapping("/rooms")
    public Result<List<Room>> getAvailableRooms(@RequestHeader("Authorization") String token) {
        try {
            getUserIdFromToken(token);
            List<Room> rooms = roomService.getAvailableRooms();
            return Result.success(rooms);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 加入房间
     *
     * POST /api/room/join
     * <p>
     * 重复校验：
     * - 检查房间号是否存在
     * - 检查房间是否已满（最多4人）
     * - 检查用户是否重复加入（同一用户不可重复加入同房间）
     * - 检查房间是否可加入（非等待中状态不可加入）
     *
     * @param token   用户认证Token
     * @param request 加入房间请求
     * @return 加入结果
     */
    @PostMapping("/room/join")
    public Result<Map<String, Object>> joinRoom(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody JoinRoomRequest request) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            // 校验房间号格式
            String roomNo = request.getRoomNo();
            if (roomNo == null || !roomNo.matches("^\\d{6}$")) {
                return Result.error("房间号必须是6位数字");
            }

            // 检查用户是否已经在其他房间中
            Room existingRoom = roomService.getCurrentRoom(userId);
            if (existingRoom != null && existingRoom.getStatus() == 0) {
                return Result.error("您已在房间 " + existingRoom.getRoomNo() + " 中，请先退出再加入其他房间");
            }

            // 执行加入房间逻辑
            RoomPlayer roomPlayer = roomService.joinRoom(roomNo, userId);

            Map<String, Object> data = new HashMap<>();
            data.put("roomNo", roomNo);
            data.put("playerId", roomPlayer.getId());
            data.put("seatIndex", roomPlayer.getSeatIndex());
            data.put("message", "加入房间成功");
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("加入房间失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户当前所在的房间
     *
     * GET /api/room/current
     *
     * @param token 用户认证Token
     * @return 用户当前所在房间信息
     */
    @GetMapping("/room/current")
    public Result<Room> getCurrentRoom(@RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }

            Room room = roomService.getCurrentRoom(userId);
            if (room == null) {
                return Result.success(null);
            }

            Integer playerCount = roomService.getPlayerCount(room.getId());
            room.setPlayerCount(playerCount);

            return Result.success(room);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 离开房间
     *
     * POST /api/room/leave
     *
     * @param token   用户认证Token
     * @param request 离开请求
     * @return 操作结果
     */
    @PostMapping("/room/leave")
    public Result<String> leaveRoom(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody LeaveRoomRequest request) {
        try {
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("用户未登录或Token已过期");
            }
            roomService.leaveRoom(roomService.getRoomByRoomNo(request.getRoomNo()).getId(), userId);
            return Result.success("退出房间成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取房间详情
     *
     * GET /api/room/detail/{roomNo}
     */
    @GetMapping("/room/detail/{roomNo}")
    public Result<Room> getRoomDetail(@PathVariable String roomNo) {
        try {
            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在");
            }
            Room detail = roomService.getRoomDetail(room.getId());
            return Result.success(detail);
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
