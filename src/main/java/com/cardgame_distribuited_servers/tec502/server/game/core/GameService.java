// Crie em: src/main/java/com/cardgame_distribuited_servers/tec502/server/game/GameService.java
package com.cardgame_distribuited_servers.tec502.server.game.core;

import com.cardgame_distribuited_servers.tec502.server.game.entity.PlayRequest;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private final EventPublisherService eventPublisher;
    private final Gson gson = new Gson();
    private static final int ROUNDS_TO_WIN = 2;
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    public GameService(EventPublisherService eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void createGame(String matchId, String player1Id, String player2Id) {
        GameState newGame = new GameState(player1Id, player2Id);
        activeGames.put(matchId, newGame);

        // Notifica ambos os jogadores
        eventPublisher.publishMessage("user." + player1Id, gson.toJson(new GameEvent.MatchFound(matchId, player2Id)));
        eventPublisher.publishMessage("user." + player2Id, gson.toJson(new GameEvent.MatchFound(matchId, player1Id)));

        System.out.println("Partida criada: " + matchId + " entre " + player1Id + " e " + player2Id);
    }

    public void receivePlay(String matchId, PlayRequest play) {
        GameState game = activeGames.get(matchId);
        if (game == null) {
            System.err.println("Jogada recebida para partida inexistente: " + matchId);
            return;
        }

        // 1. Armazena a jogada
        game.getCurrentPlays().put(play.getPlayerId(), play.getAction());
        System.out.println("Jogada armazenada para " + play.getPlayerId() + " na partida " + matchId);

        // 2. Notifica o oponente sobre a jogada (mas não qual foi)
        String opponentId = game.getOpponent(play.getPlayerId());
        String opponentTopic = "game." + matchId; // Todos na partida ouvem este tópico

        eventPublisher.publishMessage(opponentTopic, gson.toJson(new GameEvent.PlayerAction(play.getPlayerId(), "JOGOU")));

        // 3. Verifica se a ronda terminou
        checkRoundCompletion(matchId, game);
    }

    private void checkRoundCompletion(String matchId, GameState game) {
        Map<String, String> plays = game.getCurrentPlays();
        if (plays.size() < 2) {
            // Ainda falta o outro jogador jogar
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

        // Atualiza pontuação se não for empate
        if (roundWinnerId != null) {
            game.getScores().merge(roundWinnerId, 1, Integer::sum);
        }

        // Notifica ambos os jogadores sobre o resultado da ronda
        GameEvent.RoundResult result = new GameEvent.RoundResult(roundWinnerId, roundLoserId, reason, game.getScores());
        eventPublisher.publishMessage("game." + matchId, gson.toJson(result));

        System.out.println("Resultado da ronda: " + reason);

        // Limpa as jogadas para a próxima ronda
        game.clearCurrentPlays();

        // Atualiza pontuação se não for empate
        if (roundWinnerId != null) {
            // Usa merge para incrementar a pontuação do vencedor de forma segura
            game.getScores().merge(roundWinnerId, 1, Integer::sum);
            System.out.println("Pontuação atualizada para " + roundWinnerId + ": " + game.getScores().get(roundWinnerId)); // Log Adicionado
        }

        // Cria o evento de resultado da ronda
        GameEvent.RoundResult roundResult = new GameEvent.RoundResult(roundWinnerId, roundLoserId, reason, game.getScores());

        // Notifica ambos os jogadores sobre o resultado da ronda
        String gameTopic = "game." + matchId;
        eventPublisher.publishMessage(gameTopic, gson.toJson(roundResult));
        System.out.println("Resultado da ronda [" + matchId + "]: " + reason);

        // Verifica se algum jogador atingiu a pontuação necessária
        for (Map.Entry<String, Integer> entry : game.getScores().entrySet()) {
            if (entry.getValue() >= ROUNDS_TO_WIN) {
                gameWinnerId = entry.getKey();
                gameLoserId = game.getOpponent(gameWinnerId); // Encontra o perdedor
                break;
            }
        }

        if (gameWinnerId != null) {
            System.out.println(">>> Fim de Jogo [" + matchId + "]! Vencedor: " + gameWinnerId); // Log Adicionado

            // Cria o evento de fim de jogo
            GameEvent.GameOver gameOver = new GameEvent.GameOver(gameWinnerId, gameLoserId, game.getScores());

            // Publica o evento de fim de jogo para o tópico da partida
            eventPublisher.publishMessage(gameTopic, gson.toJson(gameOver));

            // Remove o estado da partida da memória para limpeza
            activeGames.remove(matchId);
            System.out.println("Estado da partida [" + matchId + "] removido da memória."); // Log Adicionado

        } else {
            game.clearCurrentPlays();
            System.out.println("A preparar próxima ronda para a partida [" + matchId + "]"); // Log Adicionado
        }
    }
}