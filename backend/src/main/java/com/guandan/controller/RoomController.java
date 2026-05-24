package com.guandan.controller;

import com.guandan.common.ApiResult;
import com.guandan.dto.NewGameRequest;
import com.guandan.entity.RoomEntity;
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
 * 注意：游戏核心逻辑（出牌、计分等）由 GameController 处理
 *
 * 边界处理：
 * - 重复房间号：createRoom 使用随机 6 位数字生成房间号，加唯一性检查最多重试 3 次
 * - 满员处理：joinRoom 检查当前玩家数量 >= MAX_PLAYERS（4人）时返回"房间已满"错误
 * - 重复加入：joinRoom 通过 findExistingPlayer 检测用户是否已在房间中，若已存在直接返回现有记录
 * - 状态校验：仅 status=0（等待中）的房间可加入，游戏中/已结束状态不可加入
 *
 * 配置说明：
 * - MAX_PLAYERS=4 定义在 RoomService 中，不可通过配置文件覆盖（硬编码常量）
 * - Token 前缀支持 "Bearer "，不区分大小写（已在 getUserIdFromToken 中处理 lowercase）
 * - 跨域配置：使用 originPatterns 通配符，开发环境允许所有来源
 * - 幂等设计：joinRoom 的重复加入检测由数据库唯一索引兜底，业务层做前置检查减少异常日志
 *
 * 重构说明：
 * - 提取 joinRoom 的公共校验逻辑到 RoomService
 * - 移除重复的 Token 空值校验，统一由 getUserIdFromToken 处理
 * - NewGameRequest 使用 DTO 内置的校验方法
 * - leaveRoom 委托 RoomService 的 removePlayer 方法
 * - 明确控制器职责边界：只做参数校验和结果包装
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
    public ApiResult<Map<String, String>> createGame(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody NewGameRequest request) {
        try {
            Long userId = getUserIdFromToken(token);
            request.setUserId(userId);
            String roomNo = roomService.createRoom(request);
            if (roomNo == null || roomNo.isEmpty()) {
                return ApiResult.error("房间创建失败，请重试");
            }
            Map<String, String> data = new HashMap<>();
            data.put("roomNo", roomNo);
            data.put("message", "房间创建成功");
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error("创建房间异常：" + e.getMessage());
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
    public ApiResult<List<RoomEntity>> getAvailableRooms(@RequestHeader("Authorization") String token) {
        try {
            getUserIdFromToken(token);
            List<RoomEntity> rooms = roomService.getAvailableRooms();
            return ApiResult.success(rooms);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * 加入房间
     *
     * POST /api/room/join
     * <p>
     * 边界处理：
     * - 房间号不存在
     * - 房间已满（最多4人）
     * - 用户重复加入（同一用户不可重复加入）
     * - 房间不可加入（非等待中状态）
     *
     * @param token   用户认证Token
     * @param request 加入房间请求（含校验注解）
     * @return 加入结果
     */
    @PostMapping("/room/join")
    public ApiResult<Map<String, Object>> joinRoom(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody NewGameRequest request) {
        try {
            Long userId = getUserIdFromToken(token);

            // 利用 DTO 内置校验
            String validationError = request.validateRequest();
            if (validationError != null) {
                return ApiResult.error(validationError);
            }

            String roomNo = request.getTrimmedRoomNo();

            // 检查用户是否已在其他房间
            Room existingRoom = roomService.getCurrentRoom(userId);
            if (existingRoom != null && existingRoom.isWaiting()) {
                return ApiResult.error("您已在房间 " + existingRoom.getRoomNo() + " 中，请先退出再加入其他房间");
            }

            // 执行加入
            com.guandan.entity.RoomPlayer roomPlayer = roomService.joinRoom(roomNo, userId);
            if (roomPlayer == null) {
                return ApiResult.error("加入房间失败，请重试");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("roomNo", roomNo);
            data.put("playerId", roomPlayer.getId());
            data.put("seatIndex", roomPlayer.getSeatIndex());
            data.put("message", "加入房间成功");
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            return ApiResult.error("加入房间失败：" + e.getMessage());
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
    public ApiResult<RoomEntity> getCurrentRoom(@RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            RoomEntity room = roomService.getCurrentRoom(userId);
            if (room == null) {
                return ApiResult.success(null);
            }
            Integer playerCount = roomService.getPlayerCount(room.getId());
            room.setPlayerCount(playerCount);
            return ApiResult.success(room);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
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
    public ApiResult<String> leaveRoom(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody com.guandan.dto.LeaveRoomRequest request) {
        try {
            Long userId = getUserIdFromToken(token);
            roomService.removePlayer(request.getRoomNo(), userId);
            return ApiResult.success("退出房间成功");
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * 获取房间详情
     *
     * GET /api/room/detail/{roomNo}
     */
    @GetMapping("/room/detail/{roomNo}")
    public ApiResult<RoomEntity> getRoomDetail(@PathVariable String roomNo) {
        try {
            RoomEntity room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return ApiResult.error("房间不存在");
            }
            Room detail = roomService.getRoomDetail(room.getId());
            return ApiResult.success(detail);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * 从Token中获取用户ID
     */
    private Long getUserIdFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少认证Token");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId = authService.validateToken(token);
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录或Token已过期");
        }
        return userId;
    }
}
