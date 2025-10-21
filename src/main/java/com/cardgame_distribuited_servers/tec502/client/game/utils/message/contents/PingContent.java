package com.cardgame_distribuited_servers.tec502.client.game.utils.message.contents; // ou ...client... dependendo do lado

// Esta classe serve tanto para o PING (cliente -> servidor)
// como para o PONG (servidor -> cliente)
public class PingContent extends Content {
    private final long timestamp;

    public PingContent(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}