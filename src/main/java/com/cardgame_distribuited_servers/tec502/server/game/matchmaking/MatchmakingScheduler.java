package com.cardgame_distribuited_servers.tec502.server.game.matchmaking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MatchmakingScheduler {
    MatchmakingService matchmakingService;

    public MatchmakingScheduler (MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @Scheduled(fixedRate = 5000)
    public void matchmakingScheduler (){
        matchmakingService.findAndFormMatch();
    }
}
