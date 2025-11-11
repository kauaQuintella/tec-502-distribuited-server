package com.cardgame_distribuited_servers.tec502.client.game.entity;
import com.cardgame_distribuited_servers.tec502.client.game.entity.Inventory;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public class User {

    private final String idUser;
    private String nickname;
    private Inventory inventory;

    public User(String nickname) {
        this.nickname = nickname;
        this.inventory = new Inventory();
        this.idUser = createIdUser(nickname);
    }

    public void setNickname(String nickname) {this.nickname = nickname;}
    public void setInventory(Inventory inventory) {this.inventory = inventory;}

    public String getIdUser() {return this.idUser;}
    public Inventory getInventory() {return inventory;}
    public String getNickname() {return nickname;}


    public String createIdUser (String nickname) {
        String shortUUID = toBase64(UUID.randomUUID());
        String  nickWithoutSpaces = nickname.replaceAll("\\s", "");
        return nickWithoutSpaces;
    }

    public static String toBase64(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bb.array());
    }

}
