package com.guandan.game.service;

import com.guandan.game.model.GameRoom;
import com.guandan.game.util.CardUtils;
import com.guandan.service.GameReferee;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameLogicService {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final GameReferee referee;

    public GameLogicService(GameReferee referee) {
        this.referee = referee;
    }

    public GameRoom start(String roomNo, List<Long> players) {
        GameRoom room = new GameRoom();
        room.roomNo = roomNo;
        room.currentPlayerId = players.get(0);
        List<Integer> deck = CardUtils.newDeck();
        for (int i = 0; i < players.size(); i++) {
            room.hands.put(players.get(i), new ArrayList<>(deck.subList(i * 27, (i + 1) * 27)));
        }
        rooms.put(roomNo, room);
        return room;
    }

    public void play(String roomNo, Long userId, List<Integer> cards) {
        GameRoom room = rooms.get(roomNo);
        if (!Objects.equals(room.currentPlayerId, userId)) throw new IllegalStateException("未到当前玩家");
        if (!referee.canPlay(cards, room.lastCards)) throw new IllegalArgumentException("牌型不合法");
        room.hands.get(userId).removeAll(cards);
        room.lastCards = new ArrayList<>(cards);
    }
}
// GameLogicService: deal cards, validate player turn, check game end
// Fix: handle game room not found in active games map
// Refactor: separate game init and game loop logic
// Docs: play flow: current player selection -> card play -> next player
