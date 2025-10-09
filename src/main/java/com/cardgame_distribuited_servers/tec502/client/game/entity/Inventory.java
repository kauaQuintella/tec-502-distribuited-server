package com.cardgame_distribuited_servers.tec502.client.game.entity;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import java.util.ArrayList;
import java.util.List;

public class Inventory {
    // ANTES: private List<String> skins;
    private final List<com.cardgame_distribuited_servers.tec502.server.game.entity.Skin> skins; // DEPOIS: Armazena objetos Skin completos

    // O construtor agora não precisa de argumentos
    public Inventory() {
        this.skins = new ArrayList<>();
    }

    // ANTES: public List<String> getSkins()
    public List<com.cardgame_distribuited_servers.tec502.server.game.entity.Skin> getSkins() { return skins; }
    // ANTES: public void setSkins(List<String> skins)
    // O setter pode ser removido se as skins forem apenas adicionadas, não substituídas em massa.

    // ANTES: public void addSkin (String skin)
    public void addSkin (Skin skin) { // DEPOIS: Aceita um objeto Skin
        this.skins.add(skin);
    }
}