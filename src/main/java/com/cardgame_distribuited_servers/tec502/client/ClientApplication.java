package com.cardgame_distribuited_servers.tec502.client;

import com.cardgame_distribuited_servers.tec502.client.game.entity.PlayRequest;
import com.cardgame_distribuited_servers.tec502.client.game.entity.User;
import com.cardgame_distribuited_servers.tec502.client.game.entity.Skin;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class ClientApplication {

    private static final String EXCHANGE_NAME = "game_events_exchange";
    private static final String[] currentMatchId = {null};
    private static Channel rabbitChannel;

    private static void startRabbitMQListener(String userId) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        rabbitChannel = connection.createChannel();

        rabbitChannel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
        String queueName = rabbitChannel.queueDeclare().getQueue();
        String routingKey = "user." + userId;
        rabbitChannel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [*] Listener RabbitMQ iniciado. Aguardando notificações no tópico '" + routingKey + "'");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println("\n[!] Notificação Recebida: " + message);

            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String eventType = json.get("type").getAsString();

                if ("MATCH_FOUND".equals(eventType)) {
                    currentMatchId[0] = json.get("matchId").getAsString();
                    String opponentId = json.get("opponentId").getAsString();
                    System.out.println("[!!!] Partida encontrada contra: " + opponentId);
                    System.out.println("[!!!] ID da Partida: " + currentMatchId[0]);

                    // Inscreve-se no tópico da partida
                    String gameTopic = "game." + currentMatchId[0];
                    rabbitChannel.queueBind(queueName, EXCHANGE_NAME, gameTopic);
                    System.out.println("[i] Inscrito no tópico da partida: " + gameTopic);
                    System.out.println("\n--- Partida Iniciada! Faça sua jogada (FOGO, AGUA, NATUREZA) ---");

                } else if ("PLAYER_ACTION".equals(eventType)) {
                    String playerId = json.get("playerId").getAsString();
                    if (!playerId.equals(userId)) {
                        System.out.println("[OPONENTE] O seu oponente fez uma jogada!");
                    }
                } else if ("ROUND_RESULT".equals(eventType)) {
                    String reason = json.get("reason").getAsString();
                    System.out.println("[RESULTADO] " + reason);
                    System.out.println("[PLACAR] " + json.get("scores").toString());
                    System.out.println("\n--- Próxima Ronda! Faça sua jogada (FOGO, AGUA, NATUREZA) ---");
                } else if ("GAME_OVER".equals(eventType)) {
                    String winnerId = json.get("winnerId").getAsString();
                    System.out.println("\n===================================");
                    if (winnerId.equals(userId)) {
                        System.out.println(" FIM DE JOGO! VOCÊ VENCEU!");
                    } else {
                        System.out.println(" FIM DE JOGO! VOCÊ PERDEU!");
                    }
                    System.out.println(" Placar Final: " + json.get("scores").toString());
                    System.out.println("===================================");

                    currentMatchId[0] = null;

                    // Opcional: Desinscrever do tópico da partida (pode ser complexo gerir)
                    // rabbitChannel.queueUnbind(queueName, EXCHANGE_NAME, "game." + matchIdAntigo);

                    System.out.println("\nPressione Enter para voltar ao menu..."); // Pede input para não sobrepor o menu
                }

            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem JSON: " + e.getMessage());
            }

            System.out.print("Comando: "); // Mostra o prompt novamente
        };

        CancelCallback cancelCallback = consumerTag -> {
            System.out.println(" [!] Consumidor cancelado: " + consumerTag);
        };

        rabbitChannel.basicConsume(queueName, true, deliverCallback, cancelCallback);
    }

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder().build();
        String baseApiUrl = "http://localhost:8080/api/";
        Scanner keyboard = new Scanner(System.in);
        Gson gson = new Gson();

        System.out.println("\n--- CARDGAME ---");
        System.out.println("DIGITE SEU NICKNAME: ");
        System.out.print("R: ");
        String userNick = keyboard.nextLine().toUpperCase();

        User user = new User(userNick);

        String gsonUser = gson.toJson(user);
        HttpRequest joinRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseApiUrl + "register-user"))
                .POST(HttpRequest.BodyPublishers.ofString(gsonUser))
                .header("Content-Type", "text/plain")
                .build();
        HttpResponse<String> joinResponse = httpClient.send(joinRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("SERVER: " + joinResponse.body());

        Thread rabbitListenerThread = new Thread(() -> {
            try {
                startRabbitMQListener(user.getIdUser());
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    System.err.println("Erro na thread do RabbitMQ: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        rabbitListenerThread.setDaemon(true);
        rabbitListenerThread.start();

        while (true) {
            System.out.println("\n--- MENU ---");
            System.out.println("JOGAR | ABRIR | SAIR | INVENTARIO | [FOGO|AGUA|NATUREZA] (em jogo)");
            System.out.print("Comando: ");
            String commandText = keyboard.nextLine().toUpperCase();

            try {
                switch (commandText) {
                    case "JOGAR":
                        if (currentMatchId[0] != null) {
                            System.out.println("Você já está numa partida!");
                            break;
                        }

                        String joinPayloadJson = gson.toJson(Map.of("playerId", user.getIdUser()));

                        joinRequest = HttpRequest.newBuilder()
                                .uri(URI.create(baseApiUrl + "matchmaking/join"))
                                // Envia o JSON
                                .POST(HttpRequest.BodyPublishers.ofString(user.getIdUser()))
                                // Define o Content-Type como application/json
                                .header("Content-Type", "application/json")
                                .build();

                        joinResponse = httpClient.send(joinRequest, HttpResponse.BodyHandlers.ofString());
                        System.out.println("SERVER: " + joinResponse.body());
                        System.out.println("A procurar partida...");
                        break;

                    case "ABRIR":
                        HttpRequest openRequest = HttpRequest.newBuilder()
                                .uri(URI.create(baseApiUrl + "servers/test-open-pack"))
                                .POST(HttpRequest.BodyPublishers.ofString(user.getIdUser()))
                                .build();

                        HttpResponse<String> openResponse = httpClient.send(openRequest, HttpResponse.BodyHandlers.ofString());
                        Skin skin = gson.fromJson(openResponse.body(), Skin.class);
                        user.getInventory().addSkin(skin);
                        System.out.println("SERVER: Parabéns! Adquiriu a skin (via Raft): " + skin.getNome());
                        break;

                    case "FOGO":
                    case "AGUA":
                    case "NATUREZA":
                        if (currentMatchId[0] == null) {
                            System.out.println("Você não está numa partida para fazer uma jogada.");
                            break;
                        }

                        PlayRequest play = new PlayRequest(user.getIdUser(), commandText);
                        String playJson = gson.toJson(play);

                        HttpRequest playRequest = HttpRequest.newBuilder()
                                .uri(URI.create(baseApiUrl + "game/" + currentMatchId[0] + "/play"))
                                .POST(HttpRequest.BodyPublishers.ofString(playJson))
                                .header("Content-Type", "application/json")
                                .build();
                        HttpResponse<String> playResponse = httpClient.send(playRequest, HttpResponse.BodyHandlers.ofString());
                        System.out.println("SERVER: " + playResponse.body());
                        break;

                    case "SAIR":
                        System.out.println("Saindo...");
                        keyboard.close();
                        rabbitListenerThread.interrupt(); // Sinaliza para a thread do listener parar
                        if (rabbitChannel != null && rabbitChannel.getConnection() != null) {
                            rabbitChannel.getConnection().close(); // Fecha a conexão
                        }
                        return; // Sai do main

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
            } catch (Exception e) {
                System.err.println("Erro ao processar comando: " + e.getMessage());
            }
        }
    }
}