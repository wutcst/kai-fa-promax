package com.guandan.game.util;

import java.util.*;

public class CardUtils {
    public static List<Integer> newDeck() {
        List<Integer> cards = new ArrayList<>();
        for (int d = 0; d < 2; d++) {
            for (int i = 1; i <= 54; i++) cards.add(i);
        }
        Collections.shuffle(cards);
        return cards;
    }

    public static int rank(int card) {
        if (card == 53 || card == 54) return card;
        int r = card % 13;
        return r == 0 ? 13 : r;
    }
}
// CardUtils: shuffle and deal with deterministic seed
// Fix: card format validation for deal results
// Refactor: split CardUtils into compare and deal utilities
// Docs: card value mapping: 3-2 (low to high), suit ordering
// CardUtils: combo recognition and comparison logic
// Fix: bomb comparison - rank bomb > number bomb
// Refactor: split compare logic into CompareResult enum
// Docs: comparison rules: same type -> compare rank; bomb > regular; king bomb > all
// Test: CardUtils deal distribution edge cases
