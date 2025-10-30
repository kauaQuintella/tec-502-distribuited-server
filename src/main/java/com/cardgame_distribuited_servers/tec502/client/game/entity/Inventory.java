package com.cardgame_distribuited_servers.tec502.client.game.entity;

import java.util.ArrayList;
import java.util.List;

public class Inventory {
    private final List<Skin> skins;

    public Inventory() {
        this.skins = new ArrayList<>();
    }

    public List<Skin> getSkins() { return skins; }

    public void addSkin (Skin skin) { // DEPOIS: Aceita um objeto Skin
        this.skins.add(skin);
    }
}