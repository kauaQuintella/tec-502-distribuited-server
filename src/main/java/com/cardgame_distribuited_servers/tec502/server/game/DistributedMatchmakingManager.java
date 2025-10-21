// Crie em /server/game/DistributedSkinsManager.java
package com.cardgame_distribuited_servers.tec502.server.game;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.network.events.EventPublisherService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.operations.Entry;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class DistributedMatchmakingManager {

    private final RaftClientService raftClientService;
    private final EventPublisherService eventPublisherService;
    private final Gson gson = new Gson();
    private final Random random = new Random();

    public DistributedMatchmakingManager(RaftClientService raftClientService,  EventPublisherService eventPublisherService) {
        this.raftClientService = raftClientService;
        this.eventPublisherService = eventPublisherService;
    }

    public void joinQueue(String playerId){

    }

}