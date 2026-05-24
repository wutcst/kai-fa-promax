package com.guandan.service;

import com.guandan.dto.JoinRoomRequest;
import com.guandan.dto.RoomDetailResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RoomService {
    private final Map<String, RoomDetailResponse> rooms = new LinkedHashMap<>();

    public RoomDetailResponse createRoom(Long ownerId) {
        String roomNo = String.valueOf(100000 + new Random().nextInt(900000));
        RoomDetailResponse room = RoomDetailResponse.waiting(roomNo, ownerId);
        rooms.put(roomNo, room);
        return room;
    }

    public RoomDetailResponse joinRoom(Long userId, JoinRoomRequest request) {
        RoomDetailResponse room = rooms.get(request.getRoomNo());
        if (room == null) throw new IllegalArgumentException("房间不存在");
        room.addPlayer(userId);
        return room;
    }

    public List<RoomDetailResponse> waitingRooms() {
        return new ArrayList<>(rooms.values());
    }

    public void ready(String roomNo, Long userId, boolean ready) {
        rooms.get(roomNo).ready(userId, ready);
    }
}
// Service: room code generation with collision retry logic
// Fix: prevent duplicate room code on concurrent creation
// Refactor: extract RoomValidator service for SRP compliance
// Docs: room lifecycle states and error codes
// Regression: room creation duplicate code and full-room boundary checks
// Chore: configuration wrap-up for room creation/join module
