package com.cardgame_distribuited_servers.tec502.client;
import com.cardgame_distribuited_servers.tec502.client.game.entity.User;
import com.google.gson.Gson;

import com.rabbitmq.client.*;
import java.net.http.HttpClient;
import java.util.Scanner;


public class ClientApplication {

    private static final String[] currentMatchId = {null};

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder().build();
        String baseApiUrl = "http://localhost:8080/api/";
        Scanner keyboard = new Scanner(System.in);
        Gson gson = new Gson();
        String[] oldMatchId = {null};

        System.out.println("\n--- CARDGAME ---");
        System.out.println("DIGITE SEU NICKNAME: ");
        System.out.print("R: ");
        String userNick = keyboard.nextLine().toUpperCase();

        User user = new User(userNick);

        // 1. Inicia o estado
        ClientState clientState = new ClientState(user);

        // 2. Inicia o serviço RabbitMQ em background
        RabbitMQService rabbitMQService = new RabbitMQService(clientState);
        Thread rabbitThread = new Thread(() -> {
            try {
                rabbitMQService.startListening();
            } catch (Exception e) {
                System.err.println("Thread do RabbitMQ falhou: " + e.getMessage());
            }
        });
        rabbitThread.setDaemon(true);
        rabbitThread.start();

        // 3. Loop principal da UI (não bloqueia mais)
        keyboard = new Scanner(System.in);
        while (true) {
            clientState.printPrompt();
            String commandText = keyboard.nextLine().toUpperCase();

            if (clientState.getCurrentState() == ClientState.State.MENU && "SAIR".equals(commandText)) {
                System.out.println("Saindo...");
                rabbitMQService.stop();
                rabbitThread.interrupt();
                break;
            }

            System.out.println("\n--- MENU ---");
            System.out.println("JOGAR | ABRIR | SAIR | INVENTARIO | [FOGO|AGUA|NATUREZA] (em jogo)");
            System.out.print("Comando: ");
            commandText = keyboard.nextLine().toUpperCase();

            clientState.handleCommand(commandText); // Aqui você precisa implementar a lógica HTTP (jogar, entrar na fila)
        }
        keyboard.close();
    }
}