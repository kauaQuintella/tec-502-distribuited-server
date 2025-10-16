package com.cardgame_distribuited_servers.tec502.server.network.controllers;

import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import org.springframework.web.bind.annotation.*;
import com.cardgame_distribuited_servers.tec502.server.network.events.EventPublisherService;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final EventPublisherService eventPublisherService;
    private final RaftClientService raftClientService;

    public ServerController(EventPublisherService eventPublisherService, RaftClientService raftClientService) {
        this.eventPublisherService = eventPublisherService;
        this.raftClientService = raftClientService;
    }

    @PostMapping("/test-publish/{userId}")
    public String testPublish(@PathVariable String userId) {
        String testMatchId = "match-" + System.currentTimeMillis();
        eventPublisherService.publishMatchFoundEvent(userId, testMatchId);
        return "Mensagem de teste publicada para o usuário: " + userId;
    }

    @PostMapping("/test-raft")
    public String testRaft() {
        long key = System.currentTimeMillis();
        String value = "teste-" + key;
        raftClientService.submitOperation(key, value);
        return "Operação de teste submetida ao Raft com a chave: " + key;
    }
}
