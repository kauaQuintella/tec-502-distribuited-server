package com.cardgame_distribuited_servers.tec502.client.game.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.project.client.libs.RuntimeTypeAdapterFactory;
import org.project.client.utils.message.contents.*;

public enum GsonSingleton {
    INSTANCE;

    private final Gson gson;

    GsonSingleton() {
        // Cria a "fábrica" que diferencia as subclasses de Content
        RuntimeTypeAdapterFactory<Content> adapter = RuntimeTypeAdapterFactory
                .of(Content.class, "type") // O campo "type" no JSON dirá qual é a classe
                .registerSubtype(CommandContent.class)
                .registerSubtype(PlayerActionContent.class)
                .registerSubtype(LoginContent.class)
                .registerSubtype(OpenPackResultContent.class)
                .registerSubtype(PingContent.class);

        this.gson = new GsonBuilder()
                .registerTypeAdapterFactory(adapter)
                .create();
    }

    public Gson getGson() {
        return gson;
    }
}