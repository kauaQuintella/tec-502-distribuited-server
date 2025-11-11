package com.cardgame_distribuited_servers.tec502.client;
import com.cardgame_distribuited_servers.tec502.client.game.entity.User;
import com.google.gson.Gson;

import com.rabbitmq.client.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;


public class ClientApplication {

    private static final String[] currentMatchId = {null};

    public static void main(String[] args) throws Exception {

        String rabbitMqIp = "localhost";

        HttpClient httpClient = HttpClient.newBuilder().build();
        String baseApiUrl = "http://localhost:8080/api/";
        Scanner keyboard = new Scanner(System.in);
        Gson gson = new Gson();
        String commandText;

        System.out.println("\n--- CARDGAME ---");
        System.out.println("DIGITE SEU NICKNAME: ");
        System.out.print("R: ");
        String userNick = keyboard.nextLine().toUpperCase();

        User user = new User(userNick);

        try {
            String gsonUser = gson.toJson(user);
            HttpRequest registerRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseApiUrl + "register-user"))
                    .POST(HttpRequest.BodyPublishers.ofString(gsonUser))
                    .header("Content-Type", "application/json") 
                    .build();
            HttpResponse<String> registerResponse = httpClient.send(registerRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("SERVER: " + registerResponse.body());
        } catch (Exception e) {
            System.err.println("Falha ao registrar no servidor. Saindo. " + e.getMessage());
            return;
        }

        ClientState clientState = new ClientState(user, baseApiUrl);
        RabbitMQService rabbitMQService = new RabbitMQService(clientState, rabbitMqIp);
        Thread rabbitThread = new Thread(() -> {
            try {
                rabbitMQService.startListening();
            } catch (Exception e) {
                System.err.println("Thread do RabbitMQ falhou: " + e.getMessage());
            }
        });
        rabbitThread.setDaemon(true);
        rabbitThread.start();

        keyboard = new Scanner(System.in);
        clientState.printPrompt();

        while (true) {
            commandText = keyboard.nextLine();

            if (clientState.getCurrentState() == ClientState.State.MENU && "SAIR".equalsIgnoreCase(commandText)) {
                System.out.println("Saindo...");
                rabbitMQService.stop();
                rabbitThread.interrupt();
                break;
            }

            clientState.handleCommand(commandText);
            clientState.printPrompt();
        }
        keyboard.close();
    }
}