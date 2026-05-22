package com.guandan.game.dto;

import java.util.List;

public class RoomStatusResponse {
    private String roomCode;
    private String status;
    private List<PlayerReadyInfo> players;
    private String hostTip;

    public static class PlayerReadyInfo {
        private String username;
        private boolean ready;
        private int seatNumber;
    }
}
// DTO: detailed ready status for each player slot
// Fix: default ready=false for newly joined players
// Refactor: rename PlayerReadyInfo to ReadySlot for clarity
