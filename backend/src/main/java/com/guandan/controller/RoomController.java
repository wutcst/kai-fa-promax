package com.guandan.controller;

import com.guandan.common.Result;
import com.guandan.dto.*;
import com.guandan.service.RoomService;
import com.guandan.util.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/new-game")
    public Result<RoomDetailResponse> createRoom() {
        return Result.ok(roomService.createRoom(UserContext.getUserId()));
    }

    @PostMapping("/room/join")
    public Result<RoomDetailResponse> join(@RequestBody JoinRoomRequest request) {
        return Result.ok(roomService.joinRoom(UserContext.getUserId(), request));
    }

    @GetMapping("/rooms")
    public Result<?> rooms() {
        return Result.ok(roomService.waitingRooms());
    }
}
// Controller: POST /rooms and POST /rooms/join endpoints
// Fix: validate room full before allowing join
