package com.cardgame_distribuited_servers.tec502.server.service_registry;

//Record para objeto de dados imutável e conciso.
public record ServerInfo(String serverId, String address, int port) {
}