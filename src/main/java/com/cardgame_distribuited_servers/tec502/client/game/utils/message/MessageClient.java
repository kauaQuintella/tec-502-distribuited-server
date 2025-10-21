package com.cardgame_distribuited_servers.tec502.client.game.utils.message;

import com.cardgame_distribuited_servers.tec502.client.game.utils.message.contents.Content;

public class MessageClient {
    private String sender;
    private Content content;
    private long timestamp;

    public MessageClient(String sender, Content content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters para que o Gson possa ler os campos
    public String getSender() { return sender; }
    public Content getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}