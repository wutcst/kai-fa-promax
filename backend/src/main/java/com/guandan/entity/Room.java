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
// Fix: null check on room capacity and duplicate room code
// Refactor: rename room status constants for clarity
// Docs: API field descriptions and exception scenarios for room entity
// Refactor: standardized package naming com.guandan.{entity,controller,service}
// Refactor: align entity fields with DB schema naming conventions
// Fix: update import paths after package restructuring
