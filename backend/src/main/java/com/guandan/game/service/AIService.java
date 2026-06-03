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
