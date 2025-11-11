package com.cardgame_distribuited_servers.tec502.client.game.entity;

public record Skin(
        String id,
        String nome,
        String raridade,
        String ownerId
) {

    public Skin(String id, String nome, String raridade) {
        this(id, nome, raridade, null);
    }

    public Skin withNewOwner(String newOwnerId) {
        return new Skin(this.id, this.nome, this.raridade, newOwnerId);
    }
}