package com.guandan.model;

public enum CardType {
    SINGLE, PAIR, THREE, STRAIGHT, FLUSH, FULLHOUSE, FOUR_BOMB, KING_BOMB, INVALID
}
// Test: CardType recognition - boundary cases (min straight, min bomb)
// Chore: CardType enum documentation finalization
// Perf: memoize combo classification for repeated checks
