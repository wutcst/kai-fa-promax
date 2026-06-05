package com.guandan.controller;

import com.guandan.service.PlayerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/player")
public class PlayerController {
    private final PlayerService playerService;
    public PlayerController(PlayerService playerService) { this.playerService = playerService; }

    @GetMapping("/stats")
    public Object getStats(@RequestParam String playerId) { return playerService.getStats(playerId); }

    @GetMapping("/records")
    public Object getRecords(@RequestParam String playerId, @RequestParam int page, @RequestParam int size) {
        return playerService.getRecords(playerId, page, size);
    }
}
// Controller: GET /player/stats and GET /player/records?page=&size=
// Fix: validate page number in request
