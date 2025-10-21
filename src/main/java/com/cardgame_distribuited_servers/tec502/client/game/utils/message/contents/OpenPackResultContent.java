package com.cardgame_distribuited_servers.tec502.client.game.utils.message.contents;// (no package ...utils.message.contents)

import com.cardgame_distribuited_servers.tec502.client.game.entity.Skin;

public class OpenPackResultContent extends Content {
    private final Skin skinAdquirida;
    private final String mensagem;

    public OpenPackResultContent(Skin skin, String mensagem) {
        this.skinAdquirida = skin;
        this.mensagem = mensagem;
    }

    public Skin getSkinAdquirida() { return skinAdquirida; }
    public String getMensagem() { return mensagem; }
}