package com.guandan.game.dto;

import java.util.List;

public class PlayCardData {
    private String roomCode;
    private String playerId;
    private List<Integer> cards;
}
// PlayCardData: card selection with combo validation
// Fix: card count validation against player hand
// Refactor: add builder pattern for PlayCardData
// Docs: PlayCardData.cardIds: list of selected card indices
// Test: PlayCardData validation - empty cards, invalid indices
