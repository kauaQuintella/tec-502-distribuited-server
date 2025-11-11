package com.cardgame_distribuited_servers.tec502.server.game.matchmaking;

import com.cardgame_distribuited_servers.tec502.server.game.core.GameService;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MatchmakingService {

    private final RaftClientService raftClientService;
    private final EventPublisherService eventPublisherService;
    private final Gson gson;
    private final Random random = new Random();
    private final GameService gameService;
    private final AtomicLong matchmakingKeyCounter = new AtomicLong(4_000_000_000L);

    public MatchmakingService(RaftClientService raftClientService, EventPublisherService eventPublisherService, GameService gameService) {
        this.raftClientService = raftClientService;
        this.eventPublisherService = eventPublisherService;
        this.gson = new Gson();
        this.gameService = gameService;
    }

    public void joinQueue(String playerId) {
        long timestamp = matchmakingKeyCounter.getAndIncrement();
        MatchmakingQueueEntry entry = new MatchmakingQueueEntry(timestamp, playerId);

        JsonElement queuePayload = gson.toJsonTree(entry);
        RaftEntry raftEntry = new RaftEntry("MATCHMAKING_ENTRY", queuePayload);
        String entryJson = gson.toJson(raftEntry);

        System.out.println("A submeter para Raft (joinQueue): Key=" + timestamp + ", Value=" + entryJson);
        boolean success = raftClientService.submitOperation(timestamp, entryJson);
    }

    private static MatchmakingQueueEntry getEntry(String playerId, long timestamp) {
        return new MatchmakingQueueEntry(timestamp, playerId);
    }

    public void findAndFormMatch() {
        System.out.println("A executar procura por partidas...");
        List<Entry> allEntries = raftClientService.getAllEntries();
        List<Map.Entry<Long, MatchmakingQueueEntry>> waitingPlayers = new ArrayList<>();

        System.out.println("DEBUG: Total de entradas no Raft: " + allEntries.size());

        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            Long entryKey = raftEntry.getKey();
            if (jsonValue == null || jsonValue.isEmpty()) continue;

            try {
                RaftEntry raftEntryWrapper = gson.fromJson(jsonValue, RaftEntry.class);

                if (raftEntryWrapper != null && "MATCHMAKING_ENTRY".equals(raftEntryWrapper.type())) {
                    MatchmakingQueueEntry queueEntry = gson.fromJson(raftEntryWrapper.payload(), MatchmakingQueueEntry.class);

                    if (queueEntry.playerId() != null && !queueEntry.playerId().isEmpty()) {
                        waitingPlayers.add(Map.entry(entryKey, queueEntry));
                        System.out.println("DEBUG: Entrada de matchmaking VÁLIDA encontrada: Key=" + entryKey + ", Player=" + queueEntry.playerId());
                    }
                }

            } catch (JsonSyntaxException e) {
                System.err.println("DEBUG: FALHA AO PARSEAR JSON (provavelmente uma Skin): Key=" + entryKey + ". Value: '" + jsonValue + "'.");
            } catch (Exception e) {
                System.err.println("DEBUG: ERRO INESPERADO ao processar entrada Raft com Key=" + entryKey + ". Erro: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Jogadores válidos encontrados na fila: " + waitingPlayers.size());

        if (waitingPlayers.size() >= 2) {
            waitingPlayers.sort(Comparator.comparingLong(entry -> entry.getValue().timestamp()));

            Map.Entry<Long, MatchmakingQueueEntry> player1Entry = waitingPlayers.get(0);
            Map.Entry<Long, MatchmakingQueueEntry> player2Entry = waitingPlayers.get(1);

            System.out.println("A tentar parear: " + player1Entry.getValue().playerId() + " e " + player2Entry.getValue().playerId()); 

            boolean deleted1 = raftClientService.deleteEntry(player1Entry.getKey());
            boolean deleted2 = raftClientService.deleteEntry(player2Entry.getKey());

            System.out.println("Resultado da deleção no Raft: Player1=" + deleted1 + ", Player2=" + deleted2);

            if (deleted1 && deleted2) {
                String matchId = UUID.randomUUID().toString();
                MatchmakingQueueEntry p1 = player1Entry.getValue();
                MatchmakingQueueEntry p2 = player2Entry.getValue();

                gameService.createGame(matchId, p1.playerId(), p2.playerId());
                System.out.println(">>> Partida formada: " + matchId + " entre " + p1.playerId() + " e " + p2.playerId()); 
            } else {
                System.out.println("Conflito de matchmaking ou erro ao apagar do Raft. Tentará novamente.");
            }
        }
    }
}