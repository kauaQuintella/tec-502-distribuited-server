package com.cardgame_distribuited_servers.tec502.server.game.entity;

public class Skin {
    private final String id;
    private final String nome;
    private final String raridade; // Ex: "Comum", "Raro", "Épico"

    public Skin(String id, String nome, String raridade) {
        this.id = id;
        this.nome = nome;
        this.raridade = raridade;
    }

    // Getters
    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getRaridade() { return raridade; }

    @Override
    public String toString() {
        return "Skin{" +
                "nome='" + nome + '\'' +
                ", raridade='" + raridade + '\'' +
                '}';
    }
}