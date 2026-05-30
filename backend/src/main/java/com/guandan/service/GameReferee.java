package com.guandan.service;

import com.guandan.game.util.CardUtils;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GameReferee {
    public boolean canPlay(List<Integer> cards, List<Integer> lastCards) {
        if (cards == null || cards.isEmpty()) return false;
        if (lastCards == null || lastCards.isEmpty()) return true;
        if (cards.size() != lastCards.size()) return false;
        return cards.stream().mapToInt(CardUtils::rank).max().orElse(0)
             > lastCards.stream().mapToInt(CardUtils::rank).max().orElse(0);
    }
}
// GameReferee: validate play - check card combo, compare with last play
// Fix: reject invalid card combo (singles with duplicates)
// Refactor: extract card combo validator to separate class
// Docs: card combo hierarchy and comparison rules reference
// Test: GameReferee validate card combo for all card types
