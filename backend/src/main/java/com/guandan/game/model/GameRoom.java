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
