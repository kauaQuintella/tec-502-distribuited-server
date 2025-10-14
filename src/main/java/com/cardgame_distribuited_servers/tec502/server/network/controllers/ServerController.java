package com.cardgame_distribuited_servers.tec502.server.network.controllers;

import org.springframework.web.bind.annotation.*;
import com.cardgame_distribuited_servers.tec502.server.network.events.EventPublisherService;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final EventPublisherService eventPublisherService;

    public ServerController(EventPublisherService eventPublisherService) {
        this.eventPublisherService = eventPublisherService;
    }

    @PostMapping("/test-publish/{userId}")
    public String testPublish(@PathVariable String userId) {
        String testMatchId = "match-" + System.currentTimeMillis();
        eventPublisherService.publishMatchFoundEvent(userId, testMatchId);
        return "Mensagem de teste publicada para o usuário: " + userId;
    }
}
