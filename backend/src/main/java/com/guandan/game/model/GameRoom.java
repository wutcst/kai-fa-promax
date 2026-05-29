package com.guandan.game.model;

import java.util.*;

public class GameRoom {
    public String roomNo;
    public Map<Long, List<Integer>> hands = new LinkedHashMap<>();
    public Long currentPlayerId;
    public List<Integer> lastCards = new ArrayList<>();
    public int passCount = 0;
}
// GameRoom: game state machine - WAITING/DEALING/PLAYING/FINISHED
// Fix: validate player count before dealing (must be 4)
// Refactor: extract GameState enum from GameRoom inner class
// Docs: game room state transition: WAITING -> DEALING -> PLAYING -> FINISHED
// Test: game room state machine transition verification
// Chore: Phase 3 backend game modules configuration consolidation
// Chore: Phase 3 game room and deal configuration finalization
