// Crie em: src/main/java/com/cardgame_distribuited_servers/tec502/server/game/GameService.java
package com.cardgame_distribuited_servers.tec502.server.game.core;

import com.cardgame_distribuited_servers.tec502.server.game.entity.PlayRequest;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GameService {

    private final EventPublisherService eventPublisher;
    private final Gson gson = new Gson();
    private static final int ROUNDS_TO_WIN = 2;
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final RaftClientService raftClientService;
    private final AtomicLong gameRaftKeyCounter = new AtomicLong(2_000_000_000L);

    public GameService(EventPublisherService eventPublisher, RaftClientService raftClientService) {
        this.eventPublisher = eventPublisher;
        this.raftClientService = raftClientService;
    }

    private record RaftGameData(long raftKey, GameState gameState) {}

    public void createGame(String matchId, String player1Id, String player2Id) {
        GameState newGame = new GameState(matchId, player1Id, player2Id);
        try {
            JsonElement gamePayload = gson.toJsonTree(newGame);
            RaftEntry raftEntry = new RaftEntry("GAME_STATE", gamePayload);
            String entryJson = gson.toJson(raftEntry);

            long raftKey = gameRaftKeyCounter.getAndIncrement();
            raftClientService.submitOperation(raftKey, entryJson);
        } catch (Exception e) {
            System.err.println("Falha grave ao salvar novo Jogo no Raft: " + e.getMessage());
            // (Num cenário real, precisaríamos de retentativas ou falhar a criação do jogo)
            return;
        }

        // Notifica ambos os jogadores (sem alteração)
        eventPublisher.publishMessage("user." + player1Id, gson.toJson(new GameEvent.MatchFound(matchId, player2Id)));
        eventPublisher.publishMessage("user." + player2Id, gson.toJson(new GameEvent.MatchFound(matchId, player1Id)));

        System.out.println("Partida criada: " + matchId + " entre " + player1Id + " e " + player2Id);
    }

    private Optional<RaftGameData> findGameInRaft(String matchId) {
        List<Entry> allEntries = raftClientService.getAllEntries();

        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            if (jsonValue == null || jsonValue.isEmpty()) continue;

            try {
                RaftEntry raftEntryWrapper = gson.fromJson(jsonValue, RaftEntry.class);

                if (raftEntryWrapper != null && "GAME_STATE".equals(raftEntryWrapper.type())) {
                    GameState game = gson.fromJson(raftEntryWrapper.payload(), GameState.class);

                    if (game != null && matchId.equals(game.getMatchId())) {
                        // Encontrado! Retorna o GameState E a chave Raft (Long)
                        return Optional.of(new RaftGameData(raftEntry.getKey(), game));
                    }
                }
            } catch (Exception e) {
                // Ignora entradas malformadas ou de outro tipo
            }
        }
        return Optional.empty(); // Não encontrado
    }

    // ADICIONADO: Método helper para salvar o estado de volta
    private void updateGameInRaft(long raftKey, GameState game) {
        JsonElement gamePayload = gson.toJsonTree(game);
        RaftEntry raftEntry = new RaftEntry("GAME_STATE", gamePayload);
        String entryJson = gson.toJson(raftEntry);

        // Sobrescreve a entrada existente usando a mesma chave Long
        // Nota: A API `submitOperation` do pleshakoff/raft atualiza se a chave existe.
        raftClientService.submitOperation(raftKey, entryJson);
    }

    public void receivePlay(String matchId, PlayRequest play) {
        // MODIFICADO: Busca o jogo no Raft em vez do mapa local
        Optional<RaftGameData> gameDataOpt = findGameInRaft(matchId);

        if (gameDataOpt.isEmpty()) {
            System.err.println("Jogada recebida para partida inexistente ou já terminada no Raft: " + matchId);
            return;
        }

        RaftGameData gameData = gameDataOpt.get();
        GameState game = gameData.gameState();
        long raftKey = gameData.raftKey();

        // 1. Armazena a jogada
        game.getCurrentPlays().put(play.getPlayerId(), play.getAction());
        System.out.println("Jogada armazenada (em memória) para " + play.getPlayerId() + " na partida " + matchId);

        // 2. Notifica o oponente sobre a jogada (sem alteração)
        String opponentId = game.getOpponent(play.getPlayerId());
        String opponentTopic = "game." + matchId;
        eventPublisher.publishMessage(opponentTopic, gson.toJson(new GameEvent.PlayerAction(play.getPlayerId(), "JOGOU")));

        // 3. Salva o estado intermediário (com a jogada) de volta no Raft
        // Isto garante que se o nó cair agora, a jogada não se perde.
        updateGameInRaft(raftKey, game);

        // 4. Verifica se a ronda terminou
        checkRoundCompletion(matchId, game, raftKey); // Passa a chave do Raft
    }

    private void checkRoundCompletion(String matchId, GameState game, long raftKey) { // <-- MODIFICADO
        Map<String, String> plays = game.getCurrentPlays();
        if (plays.size() < 2) {
            // Ainda falta o outro jogador jogar.
            // O estado já foi salvo no receivePlay, então não fazemos nada.
            return;
        }

        System.out.println("Ambos jogaram na partida " + matchId + ". A calcular resultado...");

        // Descobre quem são os jogadores e suas jogadas
        String[] players = plays.keySet().toArray(new String[0]);
        String player1Id = players[0];
        String player2Id = players[1];
        String play1 = plays.get(player1Id);
        String play2 = plays.get(player2Id);

        String roundWinnerId = null;
        String roundLoserId = null;
        String gameWinnerId = null;
        String gameLoserId = null;
        String reason = "Empate";

        // Lógica do Pedra-Papel-Tesoura
        if (play1.equals(play2)) {
            reason = "Ambos jogaram " + play1;
        } else if ((play1.equals("FOGO") && play2.equals("NATUREZA")) ||
                (play1.equals("NATUREZA") && play2.equals("AGUA")) ||
                (play1.equals("AGUA") && play2.equals("FOGO"))) {
            roundWinnerId = player1Id;
            roundLoserId = player2Id;
            reason = play1 + " ganha de " + play2;
        } else {
            roundWinnerId = player2Id;
            roundLoserId = player1Id;
            reason = play2 + " ganha de " + play1;
        }

        if (roundWinnerId != null) {
            game.getScores().merge(roundWinnerId, 1, Integer::sum);
            System.out.println("Pontuação atualizada para " + roundWinnerId + ": " + game.getScores().get(roundWinnerId));
        }

        // Notifica ambos os jogadores sobre o resultado da ronda (UMA VEZ)
        GameEvent.RoundResult result = new GameEvent.RoundResult(roundWinnerId, roundLoserId, reason, game.getScores());
        eventPublisher.publishMessage("game." + matchId, gson.toJson(result));

        System.out.println("Resultado da ronda: " + reason);

        // Verifica se algum jogador atingiu a pontuação necessária
        for (Map.Entry<String, Integer> entry : game.getScores().entrySet()) {
            if (entry.getValue() >= ROUNDS_TO_WIN) {
                gameWinnerId = entry.getKey();
                gameLoserId = game.getOpponent(gameWinnerId); // Encontra o perdedor
                break;
            }
        }

        if (gameWinnerId != null) {
            System.out.println(">>> Fim de Jogo [" + matchId + "]! Vencedor: " + gameWinnerId);

            GameEvent.GameOver gameOver = new GameEvent.GameOver(gameWinnerId, gameLoserId, game.getScores());
            eventPublisher.publishMessage("game." + matchId, gson.toJson(gameOver));

            // Remove o estado da partida do Raft para limpeza
            raftClientService.deleteEntry(raftKey);
            System.out.println("Estado da partida [" + matchId + "] removido do Raft.");

        } else {
            // O Jogo continua. Salva o estado atualizado (placar novo, jogadas limpas)
            game.clearCurrentPlays();
            updateGameInRaft(raftKey, game);
            System.out.println("A preparar próxima ronda para a partida [" + matchId + "] (Estado salvo no Raft).");
        }
    }
}