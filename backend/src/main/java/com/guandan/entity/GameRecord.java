package com.guandan.entity;

import java.time.LocalDateTime;

public class GameRecord {
    private Long id;
    private String roomCode;
    private String winnerId;
    private String players; // JSON array of playerIds
    private String finalScore;
    private LocalDateTime createdAt;
}
