package com.cardgame_distribuited_servers.tec502.client.game.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Inventory {
    private final List<Skin> skins;

    public Inventory() {
        this.skins = new ArrayList<>();
    }

    public List<Skin> getSkins() { return skins; }

    public void addSkin (Skin skin) {
        this.skins.add(skin);
    }

    public boolean removeSkin(String skinId) {
        for (Iterator<Skin> iterator = skins.iterator(); iterator.hasNext(); ) {
            Skin skin = iterator.next();
            if (skin.id().equals(skinId)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}