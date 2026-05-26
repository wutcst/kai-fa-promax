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
