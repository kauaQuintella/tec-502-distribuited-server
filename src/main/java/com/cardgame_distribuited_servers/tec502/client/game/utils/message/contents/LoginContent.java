package com.cardgame_distribuited_servers.tec502.client.game.utils.message.contents;

import com.cardgame_distribuited_servers.tec502.client.game.entity.User;

public class LoginContent extends Content {
    private User user;
    public LoginContent(User user) { this.user = user; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}