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
