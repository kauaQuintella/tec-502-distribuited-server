package com.cardgame_distribuited_servers.tec502.client.game.entity;


public class PlayRequest {
    private String playerId;
    private String action;

    public PlayRequest(String playerId, String action){
        this.playerId = playerId;
        this.action = action;
    }

    public String getAction() {
        return action;
    }
    public String getPlayerId () {
        return playerId;
    }
}