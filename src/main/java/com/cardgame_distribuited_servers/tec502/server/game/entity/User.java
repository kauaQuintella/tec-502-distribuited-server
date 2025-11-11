package com.cardgame_distribuited_servers.tec502.server.game.entity;
import java.util.UUID;

public class User {

    private final String idUser;
    private String nickname;
    private Inventory inventory;

    public User(String nickname) {
        this.nickname = nickname;
        this.inventory = new Inventory();
        this.idUser = nickname+UUID.randomUUID();
    }

    public String getIdUser() {return this.idUser;}
    public Inventory getInventory() {return inventory;}
    public String getNickname() {return nickname;}

    public void setIdUser(String idUser) {

    }
}
