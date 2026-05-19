package com.guandan.entity;

public class Room {
    private Long id;
    private String roomNo;
    private Long ownerId;
    private String status = "WAITING";
    private Integer currentPlayers = 0;

    public boolean canJoin() {
        return "WAITING".equals(status) && currentPlayers < 4;
    }
}
// Room entity: seat uniqueness validation and room status tracking
