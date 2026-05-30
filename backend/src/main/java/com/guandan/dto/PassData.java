package com.guandan.dto;

public class PassData {
    private String roomCode;
    private String playerId;
}
// PassData: pass action with player identification
// Fix: prevent double-pass on already-passed player
// Refactor: reuse PassData for pass and skip actions
// Docs: consecutive 3 passes clear table, last player leads new round
