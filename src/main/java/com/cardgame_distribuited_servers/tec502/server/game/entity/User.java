package com.cardgame_distribuited_servers.tec502.server.game.entity;
import java.util.UUID;

public class User {

    private final String idUser;
    private String nickname;
    private Inventory inventory;

    public User(String nickname, Inventory inventory) {
        this.nickname = nickname;

        if (inventory == null) {
            this.inventory = new Inventory(); // Cria um inventário vazio
        } else {
            this.inventory = inventory;
        }

        this.idUser = nickname+UUID.randomUUID().toString();
    }

    //Será que precisa?
    public void setNickname(String nickname) {this.nickname = nickname;}
    public void setInventory(Inventory inventory) {this.inventory = inventory;}

    public String getIdUser() {return this.idUser;}
    public Inventory getInventory() {return inventory;}
    public String getNickname() {return nickname;}

}
