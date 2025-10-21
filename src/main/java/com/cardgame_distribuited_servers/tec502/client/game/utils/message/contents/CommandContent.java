package com.cardgame_distribuited_servers.tec502.client.game.utils.message.contents;

public class CommandContent extends Content {
    private String command;
    public CommandContent(String command) { this.command = command; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
}