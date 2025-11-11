package com.cardgame_distribuited_servers.tec502.client;

import com.cardgame_distribuited_servers.tec502.client.game.entity.PlayRequest;
import com.cardgame_distribuited_servers.tec502.client.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.client.game.entity.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ClientState {

    public enum State { MENU, FINDING_MATCH, IN_GAME }

    private final HttpClient httpClient;
    private final AtomicReference<State> currentState = new AtomicReference<>(State.MENU);
    private final AtomicReference<String> currentMatchId = new AtomicReference<>(null);
    private final User user;
    private final Object consoleLock = new Object();
    private final Gson gson;
    private final String baseApiUrl;

    private final Map<String, JsonObject> pendingTradeOffers = new ConcurrentHashMap<>();

    public ClientState(User user, String baseApiUrl) {
        this.user = user;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder().build();
        this.baseApiUrl = baseApiUrl;
    }

    public User getUser() { return user; }
    public String getCurrentMatchId() { return currentMatchId.get(); }
    public State getCurrentState() { return currentState.get(); }

    public void handleMatchFound(String matchId, String opponentId) {
        synchronized (consoleLock) {
            if (currentState.compareAndSet(State.FINDING_MATCH, State.IN_GAME)) {
                currentMatchId.set(matchId);
                System.out.println("\n[!!!] Partida encontrada contra: " + opponentId);
                System.out.println("[!!!] ID da Partida: " + matchId);
                System.out.println("--- Partida Iniciada! Faça sua jogada (FOGO, AGUA, NATUREZA) ---");
            }
        }
    }

    public void handleGameEvent(JsonObject json) {
        synchronized (consoleLock) {
            String eventType = json.get("type").getAsString();

            switch (eventType) {
                case "PLAYER_ACTION":
                    if (!json.get("playerId").getAsString().equals(user.getIdUser())) {
                        System.out.println("\n[OPONENTE] O seu oponente fez uma jogada!");
                    }
                    break;
                case "ROUND_RESULT":
                    System.out.println("\n[RESULTADO] " + json.get("reason").getAsString());
                    System.out.println("[PLACAR] " + json.get("scores").toString());
                    System.out.println("\n--- Próxima Ronda! Faça sua jogada (FOGO, AGUA, NATUREZA) ---");
                    break;
                case "GAME_OVER":
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
                    break;

                case "INVENTORY_UPDATE": 
                    handleInventoryUpdate(json);
                    break;
                case "TRADE_PROPOSED": 
                    handleTradeProposed(json);
                    break;
                case "TRADE_COMPLETE": 
                    handleTradeComplete(json);
                    break;
            }
        }
    }

    public void handleCommand(String command) {

        synchronized (consoleLock) {
            try {
                if (currentState.get() == State.IN_GAME) {
                    if ("FOGO".equalsIgnoreCase(command) || "AGUA".equalsIgnoreCase(command) || "NATUREZA".equalsIgnoreCase(command)) {
                        sendPlay(command.toUpperCase());
                    } else {
                        System.out.println("Comando inválido. Você está em jogo. Use: FOGO, AGUA, ou NATUREZA.");
                    }
                } else if (currentState.get() == State.MENU) {
                    String[] parts = command.split(" ");
                    String cmd = parts[0];

                    switch (cmd.toUpperCase()) {
                        case "JOGAR":
                            joinMatchmaking();
                            break;
                        case "ABRIR":
                            openPack();
                            break;
                        case "INVENTARIO":
                            showInventory();
                            break;
                        case "OFERTAS":
                            showPendingOffers();
                            break;
                        case "PROPOR": 
                            proposeTrade(parts);
                            break;
                        case "ACEITAR":
                            acceptTrade(parts);
                            break;
                        default:
                            System.out.println("Comando inválido.");
                            break;
                    }
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Erro ao processar comando: " + e.getMessage());
            }
        }
    }

    private void sendPlay(String action) throws IOException, InterruptedException {
        PlayRequest play = new PlayRequest(user.getIdUser(), action);
        String playJson = gson.toJson(play);

        HttpRequest playRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseApiUrl + "game/" + getCurrentMatchId() + "/play"))
                .POST(HttpRequest.BodyPublishers.ofString(playJson))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> playResponse = httpClient.send(playRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("SERVER: " + playResponse.body());
        System.out.println("Jogada (" + action + ") enviada. Aguardando oponente...");
    }

    private void joinMatchmaking() throws IOException, InterruptedException {
        if (currentState.compareAndSet(State.MENU, State.FINDING_MATCH)) {
            HttpRequest joinRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseApiUrl + "matchmaking/join"))
                    .POST(HttpRequest.BodyPublishers.ofString(user.getIdUser()))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> joinResponse = httpClient.send(joinRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("SERVER: " + joinResponse.body());
            System.out.println("A procurar partida...");
        }
    }

    private void openPack() throws IOException, InterruptedException {
        HttpRequest openRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseApiUrl + "servers/test-open-pack"))
                .POST(HttpRequest.BodyPublishers.ofString(user.getIdUser()))
                .build();

        HttpResponse<String> openResponse = httpClient.send(openRequest, HttpResponse.BodyHandlers.ofString());

        if (openResponse.statusCode() == 200) {
            System.out.println("SERVER: Pedido de abrir pacote recebido... aguardando notificação.");
        } else {
            System.out.println("SERVER (Erro): " + openResponse.body());
        }
    }

    private void showInventory() {
        System.out.println("\n--- INVENTÁRIO LOCAL ---");
        List<Skin> skins = user.getInventory().getSkins();
        if (skins.isEmpty()) {
            System.out.println("Inventário vazio.");
        } else {
            for (Skin skin : skins) {
                System.out.printf("- ID: %s (%s, %s, Dono: %s)\n", skin.id(), skin.nome(), skin.raridade(), skin.ownerId());
            }
        }
    }

    private void showPendingOffers() {
        System.out.println("\n--- PROPOSTAS DE TROCA RECEBIDAS ---");
        if (pendingTradeOffers.isEmpty()) {
            System.out.println("Nenhuma proposta pendente.");
        } else {
            for (JsonObject offerJson : pendingTradeOffers.values()) {
                System.out.println("---------------------------------");
                System.out.println("  ID da Proposta: " + offerJson.get("offerId").getAsString());
                System.out.println("  De: " + offerJson.get("fromPlayerId").getAsString());
                System.out.println("  Ele oferece: " + offerJson.get("fromSkinId").getAsString());
                System.out.println("  Ele quer: " + offerJson.get("toSkinId").getAsString());
                System.out.println("---------------------------------");
            }
        }
    }

    private void proposeTrade(String[] commandParts) throws IOException, InterruptedException {
        if (commandParts.length != 4) {
            System.out.println("Uso: PROPOR <PlayerID_Destino> <ID_Minha_Skin> <ID_Skin_Dele>");
            return;
        }
        String toPlayerId = commandParts[1];
        String mySkinId = commandParts[2];
        String theirSkinId = commandParts[3];

        String requestJson = gson.toJson(Map.of(
                "toPlayerId", toPlayerId,
                "mySkinId", mySkinId,
                "theirSkinId", theirSkinId
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseApiUrl + "trade/propose"))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .header("Content-Type", "application/json")
                .header("X-Player-ID", user.getIdUser())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("SERVER: " + response.body());
    }

    private void acceptTrade(String[] commandParts) throws IOException, InterruptedException {
        if (commandParts.length != 2) {
            System.out.println("Uso: ACEITAR <ID_da_Oferta>");
            System.out.println("(Use o comando OFERTAS para ver os IDs)");
            return;
        }
        String offerId = commandParts[1];

        String requestJson = gson.toJson(Map.of("offerId", offerId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseApiUrl + "trade/accept"))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .header("Content-Type", "application/json")
                .header("X-Player-ID", user.getIdUser()) 
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            System.out.println("SERVER: Proposta aceite! Aguardando notificação de conclusão...");
            pendingTradeOffers.remove(offerId);
        } else {
            System.out.println("SERVER (Erro): " + response.body());
        }
    }

    private void handleInventoryUpdate(JsonObject json) {
        try {
            Skin skin = gson.fromJson(json.get("skin"), Skin.class);
            String action = json.get("action").getAsString();

            if ("ADD".equals(action)) {
                user.getInventory().addSkin(skin);
                System.out.println("\n[INVENTÁRIO] Skin " + skin.nome() + " adicionada ao seu inventário!");
            } else if ("REMOVE".equals(action)) {
                user.getInventory().removeSkin(skin.id());
                System.out.println("\n[INVENTÁRIO] Skin " + skin.nome() + " removida do seu inventário.");
            }
        } catch (JsonSyntaxException e) {
            System.err.println("Erro ao desserializar skin do INVENTORY_UPDATE");
        }
    }

    private void handleTradeProposed(JsonObject json) {
        JsonObject offer = json.get("offer").getAsJsonObject();
        String offerId = offer.get("offerId").getAsString();
        pendingTradeOffers.put(offerId, offer);

        System.out.println("\n[!!!] NOVA PROPOSTA DE TROCA RECEBIDA [!!!]");
        System.out.println("  De: " + offer.get("fromPlayerId").getAsString());
        System.out.println("  Ele oferece (ID): " + offer.get("fromSkinId").getAsString());
        System.out.println("  Ele quer (ID): " + offer.get("toSkinId").getAsString());
        System.out.println("  Para aceitar, digite: ACEITAR " + offerId);
        System.out.println("(Use OFERTAS para ver todas as propostas)");
    }

    private void handleTradeComplete(JsonObject json) {
        String status = json.get("status").getAsString();
        if ("SUCCESS".equals(status)) {
            Skin receivedSkin = gson.fromJson(json.get("received"), Skin.class);
            Skin sentSkin = gson.fromJson(json.get("sent"), Skin.class);

            user.getInventory().removeSkin(sentSkin.id());
            user.getInventory().addSkin(receivedSkin);

            System.out.println("\n[!!!] TROCA CONCLUÍDA [!!!]");
            System.out.println("  Você enviou: " + sentSkin.nome() + " (" + sentSkin.id() + ")");
            System.out.println("  Você recebeu: " + receivedSkin.nome() + " (" + receivedSkin.id() + ")");
        } else {
            System.out.println("\n[!] TROCA FALHOU [!]");
            System.out.println("  Motivo: " + json.get("reason").getAsString());
        }
    }


    public void printPrompt() {
        synchronized (consoleLock) {
            if (currentState.get() == State.IN_GAME) {
                System.out.print("\nJogada: ");
            } else {
                System.out.println("\n--- MENU ---");
                System.out.println("JOGAR | ABRIR | INVENTARIO | OFERTAS | PROPOR | ACEITAR | SAIR");
                System.out.print("Comando: ");
            }
        }
    }
}