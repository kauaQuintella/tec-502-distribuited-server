package com.cardgame_distribuited_servers.tec502.server.game.core;

import java.util.Map;

public sealed interface GameEvent {
    String type();

    record MatchFound(String type, String matchId, String opponentId) implements GameEvent {
        public MatchFound(String matchId, String opponentId) {
            this("MATCH_FOUND", matchId, opponentId);
        }
    }

    record PlayerAction(String type, String playerId, String action) implements GameEvent {
        public PlayerAction(String playerId, String action) {
            this("PLAYER_ACTION", playerId, action);
        }
    }

    record RoundResult(String type, String winnerId, String loserId, String reason, Map<String, Integer> scores) implements GameEvent {
        public RoundResult(String winnerId, String loserId, String reason, Map<String, Integer> scores) {
            this("ROUND_RESULT", winnerId, loserId, reason, scores);
        }
    }

    record GameOver(String type, String winnerId, String loserId, Map<String, Integer> scores) implements GameEvent {
        public GameOver(String winnerId, String loserId, Map<String, Integer> scores) {
            this("GAME_OVER", winnerId, loserId, scores);
        }
    }
}