package com.cardgame_distribuited_servers.tec502.server.registry;

import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class RegistryService {

    //ConcurrentHashMap para garantir que o mapa seja thread-safe.
    private final Map<String, ServerInfo> onlineServers = new ConcurrentHashMap<>();

    public void register(ServerInfo serverInfo) {
        onlineServers.put(serverInfo.serverId(), serverInfo);
        System.out.println("Servidor registrado/atualizado: " + serverInfo);
    }

    public void unregister(String serverId) {
        onlineServers.remove(serverId);
        System.out.println("Servidor removido: " + serverId);
    }

    public Collection<ServerInfo> getOnlineServers() {
        return onlineServers.values();
    }
}