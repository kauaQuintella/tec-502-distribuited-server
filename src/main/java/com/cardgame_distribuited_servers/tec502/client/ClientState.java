// Novo arquivo: client/ClientState.java
package com.cardgame_distribuited_servers.tec502.client;

import com.cardgame_distribuited_servers.tec502.client.game.entity.PlayRequest;
import com.cardgame_distribuited_servers.tec502.client.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.client.game.entity.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ClientState {

    public enum State { MENU, FINDING_MATCH, IN_GAME }

    private final HttpClient httpClient;

    private final AtomicReference<State> currentState = new AtomicReference<>(State.MENU);
    private final AtomicReference<String> currentMatchId = new AtomicReference<>(null);
    private final User user;
    private final Object consoleLock = new Object(); // Para controlar o prompt
    private final Gson gson;

    private static String BASE_API_URL = "http://localhost:8080/api/";

    public ClientState(User user) {
        this.user = user;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder().build();
    }

    public User getUser() { return user; }
    public String getCurrentMatchId() { return currentMatchId.get(); }
    public State getCurrentState() { return currentState.get(); }

    // Chamado pelo RabbitMQService
    public void handleMatchFound(String matchId, String opponentId) {
        synchronized (consoleLock) {
            if (currentState.compareAndSet(State.FINDING_MATCH, State.IN_GAME)) {
                currentMatchId.set(matchId);
                System.out.println("\n[!!!] Partida encontrada contra: " + opponentId);
                System.out.println("[!!!] ID da Partida: " + matchId);
                System.out.println("--- Partida Iniciada! Faça sua jogada (FOGO, AGUA, NATUREZA) ---");
                printPrompt();
            }
        }
    }

    // Chamado pelo RabbitMQService
    public void handleGameEvent(JsonObject json) {
        synchronized (consoleLock) {
            String eventType = json.get("type").getAsString();
            if ("PLAYER_ACTION".equals(eventType)) {
                if (!json.get("playerId").getAsString().equals(user.getIdUser())) {
                    System.out.println("\n[OPONENTE] O seu oponente fez uma jogada!");
                }
            } else if ("ROUND_RESULT".equals(eventType)) {
                System.out.println("\n[RESULTADO] " + json.get("reason").getAsString());
                System.out.println("[PLACAR] " + json.get("scores").toString());
                System.out.println("\n--- Próxima Ronda! Faça sua jogada (FOGO, AGUA, NATUREZA) ---");
            } else if ("GAME_OVER".equals(eventType)) {
                System.out.println("\n===================================");
                if (json.get("winnerId").getAsString().equals(user.getIdUser())) {
                    System.out.println(" FIM DE JOGO! VOCÊ VENCEU!");
                } else {
                    System.out.println(" FIM DE JOGO! VOCÊ PERDEU!");
                }
                System.out.println(" Placar Final: " + json.get("scores").toString());
                System.out.println("===================================");
                currentState.set(State.MENU);
                currentMatchId.set(null);
                System.out.println("\nPressione Enter para voltar ao menu...");
            }
            printPrompt();
        }
    }

    // Chamado pelo Loop Principal (Main)
    public void handleCommand(String command) throws IOException, InterruptedException {
        synchronized (consoleLock) {
            // Lógica de comando baseada no estado
            if (currentState.get() == State.IN_GAME) {
                if ("FOGO".equals(command) || "AGUA".equals(command) || "NATUREZA".equals(command)) {

                    PlayRequest play = new PlayRequest(user.getIdUser(), command);
                    String playJson = gson.toJson(play);

                    HttpRequest playRequest = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_API_URL + "game/" + getCurrentMatchId() + "/play"))
                            .POST(HttpRequest.BodyPublishers.ofString(playJson))
                            .header("Content-Type", "application/json")
                            .build();
                    HttpResponse<String> playResponse = httpClient.send(playRequest, HttpResponse.BodyHandlers.ofString());
                    System.out.println("SERVER: " + playResponse.body());

                    System.out.println("Jogada (" + command + ") enviada. Aguardando oponente...");
                } else {
                    System.out.println("Comando inválido durante o jogo.");
                }
            } else if (currentState.get() == State.MENU) {
                switch (command) {
                    case "JOGAR":
                        if (currentState.compareAndSet(State.MENU, State.FINDING_MATCH)) {

                            String joinPayloadJson = gson.toJson(Map.of("playerId", user.getIdUser()));

                            HttpRequest joinRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(BASE_API_URL + "matchmaking/join"))
                                    // Envia o JSON
                                    .POST(HttpRequest.BodyPublishers.ofString(user.getIdUser()))
                                    // Define o Content-Type como application/json
                                    .header("Content-Type", "application/json")
                                    .build();

                            HttpResponse<String> joinResponse = httpClient.send(joinRequest, HttpResponse.BodyHandlers.ofString());
                            System.out.println("SERVER: " + joinResponse.body());
                            System.out.println("A procurar partida...");
                        }

                    case "ABRIR":
                        HttpRequest openRequest = HttpRequest.newBuilder()
                                .uri(URI.create(BASE_API_URL + "servers/test-open-pack"))
                                .POST(HttpRequest.BodyPublishers.ofString(user.getIdUser()))
                                .build();

                        HttpResponse<String> openResponse = httpClient.send(openRequest, HttpResponse.BodyHandlers.ofString());
                        Skin skin = gson.fromJson(openResponse.body(), Skin.class);
                        user.getInventory().addSkin(skin);
                        System.out.println("SERVER: Parabéns! Adquiriu a skin (via Raft): " + skin.getNome());
                        break;

                    case "INVENTARIO":
                        System.out.println("Olha seu inventário aqui!\n");
                        for (Skin skinFromUser : user.getInventory().getSkins()){
                            System.out.println(skinFromUser.getNome());
                        }
                        System.out.println("\nSatisfeito? :)\n");
                        break;

                    default:
                        System.out.println("Comando inválido.");
                        break;
                }
            }
            printPrompt();
        }
    }

    public void printPrompt() {
        synchronized (consoleLock) {
            if (currentState.get() == State.IN_GAME) {
                System.out.print("Jogada: ");
            } else {
                System.out.print("Comando: ");
            }
        }
    }
}