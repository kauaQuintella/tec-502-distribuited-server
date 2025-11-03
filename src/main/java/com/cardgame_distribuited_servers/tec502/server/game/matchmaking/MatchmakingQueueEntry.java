package com.cardgame_distribuited_servers.tec502.server.game.matchmaking;

public record MatchmakingQueueEntry(String type, String playerId, long timestamp) {

    public MatchmakingQueueEntry(long timestamp, String playerId){
        this("MATCHMAKING_ENTRY", playerId, timestamp);
    }
}