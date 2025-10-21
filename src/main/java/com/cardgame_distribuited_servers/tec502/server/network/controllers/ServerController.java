package com.cardgame_distribuited_servers.tec502.server.network.controllers;

import com.cardgame_distribuited_servers.tec502.server.game.DistributedSkinsManager;
import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import org.springframework.web.bind.annotation.*;
import com.cardgame_distribuited_servers.tec502.server.network.events.EventPublisherService;

import java.util.Optional;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final EventPublisherService eventPublisherService;
    private final RaftClientService raftClientService;
    private final DistributedSkinsManager distributedSkinsManager; // Adicione o novo manager

    public ServerController(EventPublisherService eventPublisherService,
                            RaftClientService raftClientService,
                            DistributedSkinsManager distributedSkinsManager) {
        this.eventPublisherService = eventPublisherService;
        this.raftClientService = raftClientService;
        this.distributedSkinsManager = distributedSkinsManager;
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
        distributedSkinsManager.abrirPacote();
        return "Operação de teste submetida ao Raft com a chave: " + key;
    }

    @PostMapping("/test-open-pack") // Renomeado para clareza
    public String testOpenPack() {
        System.out.println("Recebido pedido de teste para abrir pacote..."); // Log para saber que o endpoint foi chamado
        Optional<Skin> acquiredSkin = distributedSkinsManager.abrirPacote();

        if (acquiredSkin.isPresent()) {
            return "Parabéns! Adquiriu a skin (via Raft): " + acquiredSkin.get().getNome();
        } else {
            return "Não foi possível adquirir uma skin (verifique os logs).";
        }
    }
}
