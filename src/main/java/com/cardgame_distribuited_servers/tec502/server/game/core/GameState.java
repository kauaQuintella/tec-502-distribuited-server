package com.cardgame_distribuited_servers.tec502.server.game.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameState {
    private String player1Id;
    private String player2Id;

    private Map<String, String> currentPlays = new ConcurrentHashMap<>();
    private Map<String, Integer> scores = new ConcurrentHashMap<>();

    public GameState(String player1Id, String player2Id) {
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.scores.put(player1Id, 0);
        this.scores.put(player2Id, 0);
    }

    public String getOpponent(String playerId) {
        return playerId.equals(player1Id) ? player2Id : player1Id;
    }

    // Getters
    public Map<String, String> getCurrentPlays() {
        return currentPlays;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public void clearCurrentPlays() {
        this.currentPlays.clear();
    }
}
