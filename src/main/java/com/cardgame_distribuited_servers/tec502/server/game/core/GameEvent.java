package com.cardgame_distribuited_servers.tec502.server.game.core;

import java.util.Map;

// Usamos uma classe "selada" (sealed) para definir um conjunto fixo de eventos
public sealed interface GameEvent {
    String type();

    // Evento para quando uma partida é encontrada
    record MatchFound(String type, String matchId, String opponentId) implements GameEvent {
        public MatchFound(String matchId, String opponentId) {
            this("MATCH_FOUND", matchId, opponentId);
        }
    }

    // Evento para quando um jogador faz uma jogada
    record PlayerAction(String type, String playerId, String action) implements GameEvent {
        public PlayerAction(String playerId, String action) {
            this("PLAYER_ACTION", playerId, action);
        }
    }

    // Evento para o resultado de uma ronda
    record RoundResult(String type, String winnerId, String loserId, String reason, Map<String, Integer> scores) implements GameEvent {
        public RoundResult(String winnerId, String loserId, String reason, Map<String, Integer> scores) {
            this("ROUND_RESULT", winnerId, loserId, reason, scores);
        }
    }

    // Evento para o fim do jogo
    record GameOver(String type, String winnerId, String loserId, Map<String, Integer> scores) implements GameEvent {
        public GameOver(String winnerId, String loserId, Map<String, Integer> scores) {
            this("GAME_OVER", winnerId, loserId, scores);
        }
    }
}