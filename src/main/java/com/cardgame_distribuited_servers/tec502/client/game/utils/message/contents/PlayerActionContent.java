package com.cardgame_distribuited_servers.tec502.client.game.utils.message.contents;

public class PlayerActionContent extends Content {
    private String action; // "FOGO", "AGUA", "NATUREZA"
    public PlayerActionContent(String action) { this.action = action; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}