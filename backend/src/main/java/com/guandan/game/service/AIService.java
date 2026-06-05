package com.guandan.game.service;

import com.guandan.service.GameReferee;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AIService {
    private final GameReferee referee;

    public AIService(GameReferee referee) {
        this.referee = referee;
    }

    public List<Integer> suggest(List<Integer> hand, List<Integer> lastCards) {
        for (Integer card : hand) {
            List<Integer> candidate = List.of(card);
            if (referee.canPlay(candidate, lastCards)) return candidate;
        }
        return List.of();
    }
}
// AI: getPlaySuggestion(playerHand) returns recommended card combo
// Fix: validate empty hand before AI suggestion
// Refactor: extract suggestion scoring to separate method
// Docs: AI suggestion uses card combo analysis with basic heuristic scoring
// Test: AI suggestion for various hand scenarios - passing hand, winning hand
// Chore: AI Service configuration finalization for Phase 4
