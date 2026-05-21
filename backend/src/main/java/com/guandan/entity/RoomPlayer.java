package com.guandan.entity;

public class RoomPlayer {
    private Long roomId;
    private Long userId;
    private Integer seatNo;
    private Boolean ready = false;
}
// RoomPlayer: player-seat association and ready state
// Fix: validate player count before seat assignment
// Refactor: restructure RoomPlayer fields and method names
// Docs: RoomPlayer status lifecycle documentation
// Regression: RoomPlayer seat assignment and ready state verification
