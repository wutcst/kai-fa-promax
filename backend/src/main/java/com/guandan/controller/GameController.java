package com.guandan.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guandan.common.Result;
import com.guandan.dto.GameStateResponse;
import com.guandan.dto.JoinRoomRequest;
import com.guandan.dto.RoomDetailResponse;
import com.guandan.entity.Room;
import com.guandan.entity.RoomPlayer;
import com.guandan.entity.User;
import com.guandan.game.model.GameRoom;
import com.guandan.game.service.GameLogicService;
import com.guandan.game.util.CardUtils;
import com.guandan.mapper.RoomPlayerMapper;
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
 * 负责人：成员B（通讯与架构）
 *
 * 功能：
 * - 游戏房间管理（加入、退出、准备）
 * - 游戏状态查询
 * - 游戏流程控制
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
    private GameLogicService gameLogicService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoomPlayerMapper roomPlayerMapper;

    /**
     * 加入房间
     * POST /api/room/join
     *
     * 功能：玩家通过房间号加入房间
     * 使用场景：大厅页面点击"进入房间"或手动输入房间号
     *
     * @param token 用户认证Token
     * @param request 加入房间请求 {roomNo: "123456"}
     * @return {success: true, seatIndex: 0, message: "加入成功"}
     */
    @PostMapping("/room/join")
    public Result<Map<String, Object>> joinRoom(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody JoinRoomRequest request) {
        try {
            // 验证Token并获取用户ID
            Long userId = getUserIdFromToken(token);

            // 通过userId获取用户
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 根据房间号查询房间
            Room room = roomService.getRoomByRoomNo(request.getRoomNo());
            if (room == null) {
                return Result.error("房间不存在：" + request.getRoomNo());
            }

            // 检查房间状态
            if (room.getStatus() == 2) {
                return Result.error("房间已结束，无法加入");
            }

            // 加入房间（使用用户名）
            RoomPlayer roomPlayer = roomService.joinRoom(request.getRoomNo(), user.getUsername());
            if (roomPlayer == null) {
                return Result.error("加入房间失败");
            }

            // 同时加入游戏逻辑服务的内存房间
            String gameRoomId = "room_" + request.getRoomNo();
            gameLogicService.joinRoom(String.valueOf(userId), gameRoomId);

            // 设置玩家在线状态
            user.setOnline(1);
            userMapper.updateById(user);

            // 广播房间人数更新
            com.guandan.game.websocket.GameWebSocketServer.broadcastRoomInfoUpdateStatic(gameRoomId);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("seatIndex", roomPlayer.getSeatIndex());
            data.put("roomNo", room.getRoomNo());
            data.put("message", "加入房间成功");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 退出房间
     * POST /api/room/exit
     *
     * 功能：玩家主动退出房间
     * 使用场景：房间内点击"退出房间"按钮
     *
     * @param token 用户认证Token
     * @param request 退出房间请求 {roomId: "xxx"}
     * @return {success: true, message: "退出成功"}
     */
    @PostMapping("/room/exit")
    public Result<Map<String, Object>> exitRoom(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = getUserIdFromToken(token);
            String roomId = request.get("roomId");

            if (roomId == null || roomId.isEmpty()) {
                return Result.error("房间ID不能为空");
            }

            // 设置玩家离线状态
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setOnline(0);
                userMapper.updateById(user);
            }

            // 从游戏中移除玩家
            String gameRoomId = null;
            // 如果roomId是完整格式（room_123456），直接使用；否则尝试查找
            if (roomId != null && roomId.startsWith("room_")) {
                gameRoomId = roomId;
            } else {
                // 尝试从数据库查找
                QueryWrapper<RoomPlayer> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("user_id", userId);
                queryWrapper.orderByDesc("id");
                queryWrapper.last("LIMIT 1");
                RoomPlayer roomPlayer = roomPlayerMapper.selectOne(queryWrapper);
                if (roomPlayer != null) {
                    Room room = roomService.getRoomById(roomPlayer.getRoomId());
                    if (room != null) {
                        gameRoomId = "room_" + room.getRoomNo();
                    }
                }
            }
            
            gameLogicService.removePlayer(String.valueOf(userId));

            // 广播房间人数更新
            if (gameRoomId != null) {
                com.guandan.game.websocket.GameWebSocketServer.broadcastRoomInfoUpdateStatic(gameRoomId);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("message", "退出房间成功");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取房间详细信息
     * GET /api/room/{roomNo}/detail
     *
     * 功能：获取房间的详细信息（玩家列表、状态等）
     * 使用场景：加入房间后显示房间详情
     *
     * @param token 用户认证Token
     * @param roomNo 房间号
     * @return 房间详细信息
     */
    @GetMapping("/room/{roomNo}/detail")
    public Result<RoomDetailResponse> getRoomDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomNo) {
        try {
            // 验证Token
            getUserIdFromToken(token);

            // 查询房间
            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            // 查询游戏房间状态
            GameRoom gameRoom = gameLogicService.getRoom("room_" + roomNo);

            // 构建响应
            RoomDetailResponse response = new RoomDetailResponse();
            response.setRoomNo(room.getRoomNo());
            response.setStatus(room.getStatus() == 0 ? "WAITING" : room.getStatus() == 1 ? "PLAYING" : "FINISHED");
            response.setCreatorId(room.getCreatorId());
            response.setLevelTeamA(room.getLevelTeamA());
            response.setLevelTeamB(room.getLevelTeamB());

            // 游戏状态
            if (gameRoom != null) {
                response.setCurrentPlayerIndex(gameRoom.getCurrentPlayerIndex());
                response.setPlayerCount(gameRoom.getPlayerIds().size());
            } else {
                response.setCurrentPlayerIndex(0);
                // 从数据库查询实际人数
                Integer actualPlayerCount = roomService.getPlayerCount(room.getId());
                response.setPlayerCount(actualPlayerCount);
            }

            // 查询房间玩家列表（返回带用户名/昵称/在线状态的简化结构）
            List<RoomPlayer> roomPlayers = roomService.getRoomPlayers(room.getId());
            List<Map<String, Object>> players = new java.util.ArrayList<>();
            if (roomPlayers != null) {
                for (RoomPlayer rp : roomPlayers) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("userId", rp.getUserId());
                    info.put("seatIndex", rp.getSeatIndex());
                    info.put("isReady", rp.getIsReady());
                    User u = null;
                    try {
                        u = userMapper.selectById(rp.getUserId());
                    } catch (Exception ignored) {
                    }
                    if (u != null) {
                        info.put("username", u.getUsername());
                        info.put("nickname", u.getNickname());
                        info.put("avatar", u.getAvatar());
                        info.put("online", u.getOnline());
                    } else {
                        info.put("online", 0);
                    }
                    players.add(info);
                }
            }
            response.setPlayers(players);

            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解散房间
     * POST /api/room/{roomNo}/dissolve
     *
     * 功能：房主解散房间
     * 使用场景：房主点击"解散房间"按钮
     *
     * @param token 用户认证Token
     * @param roomNo 房间号
     * @return {success: true, message: "房间已解散"}
     */
    @PostMapping("/room/{roomNo}/dissolve")
    public Result<Map<String, Object>> dissolveRoom(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomNo) {
        try {
            Long userId = getUserIdFromToken(token);

            // 查询房间
            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在：" + roomNo);
            }

            // 验证是否是房主
            if (!room.getCreatorId().equals(userId)) {
                return Result.error("只有房主才能解散房间");
            }

            // 清理游戏房间
            String roomId = "room_" + roomNo;
            GameRoom gameRoom = gameLogicService.getRoom(roomId);
            if (gameRoom != null) {
                // 移除所有玩家
                for (String playerId : gameRoom.getPlayerIds()) {
                    gameLogicService.removePlayer(playerId);
                }
            }

            // 更新房间状态为结束
            roomService.updateRoomStatus(room.getId(), 2);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("message", "房间已解散");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 准备就绪
     * POST /api/game/ready
     *
     * 功能：玩家准备开始游戏
     * 使用场景：房间内点击"准备"按钮
     *
     * @param token 用户认证Token
     * @param request {roomId: "xxx"} 或 {roomNo: "123456"}
     * @return {success: true, message: "准备成功"}
     */
    @PostMapping("/game/ready")
    public Result<Map<String, Object>> ready(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = getUserIdFromToken(token);
            String roomId = request.get("roomId");
            String roomNo = request.get("roomNo");

            // 如果提供的是roomNo，需要先查找房间
            Room room = null;
            if (roomNo != null && !roomNo.isEmpty()) {
                room = roomService.getRoomByRoomNo(roomNo);
            } else if (roomId != null && !roomId.isEmpty()) {
                // 如果是roomId格式（room_123456），提取房间号
                String roomNoFromId = roomId.replace("room_", "");
                room = roomService.getRoomByRoomNo(roomNoFromId);
            }

            if (room == null) {
                return Result.error("房间不存在");
            }

            // 更新玩家准备状态（数据库）
            QueryWrapper<RoomPlayer> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("room_id", room.getId());
            queryWrapper.eq("user_id", userId);
            RoomPlayer roomPlayer = roomPlayerMapper.selectOne(queryWrapper);

            if (roomPlayer == null) {
                return Result.error("玩家不在该房间中");
            }

            Integer currentReady = roomPlayer.getIsReady();
            int nextReady = (currentReady != null && currentReady == 1) ? 0 : 1;
            roomPlayer.setIsReady(nextReady);
            roomPlayerMapper.updateById(roomPlayer);

            // 通过WebSocket广播房间人数和准备状态更新
            String gameRoomId = "room_" + room.getRoomNo();
            com.guandan.game.websocket.GameWebSocketServer.broadcastRoomInfoUpdateStatic(gameRoomId);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("message", nextReady == 1 ? "准备就绪" : "已取消准备");
            data.put("roomNo", room.getRoomNo());

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }


    /**
     * 开始游戏
     * POST /api/game/start
     *
     * 功能：房主开始游戏（发牌）
     * 使用场景：所有玩家准备好后，房主点击"开始游戏"
     *
     * @param token 用户认证Token
     * @param request {roomId: "xxx"}
     * @return {success: true, message: "游戏开始"}
     */
    @PostMapping("/game/start")
    public Result<Map<String, Object>> startGame(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = getUserIdFromToken(token);
            String roomId = request.get("roomId");

            if (roomId == null || roomId.isEmpty()) {
                return Result.error("房间ID不能为空");
            }

            String roomNo = roomId.replace("room_", "");
            Room room = roomService.getRoomByRoomNo(roomNo);
            if (room == null) {
                return Result.error("房间不存在");
            }

            if (room.getCreatorId() == null || !room.getCreatorId().equals(userId)) {
                return Result.error("只有房主可以开始游戏");
            }

            List<RoomPlayer> players = roomService.getRoomPlayers(room.getId());
            boolean allReady = players != null && !players.isEmpty() && players.stream().allMatch(p -> {
                if (room.getCreatorId() != null && room.getCreatorId().equals(p.getUserId())) {
                    return true;
                }
                return p.getIsReady() != null && p.getIsReady() == 1;
            });

            if (!allReady) {
                return Result.error("还有玩家未准备");
            }

            boolean started = gameLogicService.startGame(roomId);
            if (!started) {
                return Result.error("游戏开始失败，房间可能没有玩家");
            }

            if (room.getStatus() != null && room.getStatus() == 0) {
                roomService.updateRoomStatus(room.getId(), 1);
            }

            com.guandan.game.websocket.GameWebSocketServer.broadcastGameStartStatic(roomId);
            com.guandan.game.websocket.GameWebSocketServer.broadcastInitialTurnStatic(roomId);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("message", "游戏开始");

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取游戏状态
     * GET /api/game/{roomId}/state
     *
     * 功能：查询游戏的详细状态
     * 使用场景：前端定期刷新游戏状态
     *
     * @param token 用户认证Token
     * @param roomId 房间ID
     * @return 游戏状态（手牌、当前玩家、出牌区等）
     */
    @GetMapping("/game/{roomId}/state")
    public Result<GameStateResponse> getGameState(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomId) {
        try {
            // 验证Token
            Long userId = getUserIdFromToken(token);

            // 获取游戏房间
            GameRoom gameRoom = gameLogicService.getRoom(roomId);
            if (gameRoom == null) {
                return Result.error("游戏房间不存在");
            }

            // 构建响应
            GameStateResponse response = new GameStateResponse();
            response.setRoomId(roomId);
            response.setStatus(gameRoom.getStatus().name());
            response.setCurrentPlayerIndex(gameRoom.getCurrentPlayerIndex());
            response.setPlayerCount(gameRoom.getPlayerIds().size());

            // 获取当前玩家的手牌
            String playerId = String.valueOf(userId);
            List<Integer> myCards = gameRoom.getHandCards().get(playerId);
            response.setMyCards(myCards);

            // 设置级牌信息
            int levelCardRank = gameRoom.getLevelCardRank();
            response.setLevelCard(levelCardRank);
            response.setLevelCardName(CardUtils.getRankName(levelCardRank));

            // 设置级牌和逢人配信息
            if (myCards != null) {
                response.setMyLevelCards(CardUtils.getLevelCards(myCards, levelCardRank));
                response.setMyWildCards(CardUtils.getWildCards(myCards, levelCardRank));
            }

            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 从Token中获取用户ID（工具方法）
     */
    private Long getUserIdFromToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return authService.validateToken(token);
    }
}
// Controller: GET /game/room/{code}/status endpoint
