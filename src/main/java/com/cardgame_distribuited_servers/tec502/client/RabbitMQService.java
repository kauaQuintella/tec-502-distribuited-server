package com.cardgame_distribuited_servers.tec502.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;

public class RabbitMQService {

    private final ClientState clientState;
    private Channel rabbitChannel;
    private Connection connection;
    private final String rabbitMqHost;

    public RabbitMQService(ClientState clientState, String rabbitMqHost) {
        this.clientState = clientState;
        this.rabbitMqHost = rabbitMqHost;
    }

    public void startListening() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.rabbitMqHost);
        connection = factory.newConnection();
        rabbitChannel = connection.createChannel();

        rabbitChannel.exchangeDeclare("game_events_exchange", "topic", true);
        String queueName = rabbitChannel.queueDeclare().getQueue();
        String routingKey = "user." + clientState.getUser().getIdUser();
        rabbitChannel.queueBind(queueName, "game_events_exchange", routingKey);

        System.out.println(" [*] Listener RabbitMQ iniciado. Aguardando notificações no tópico '" + routingKey + "'");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");

            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String eventType = json.get("type").getAsString();

                if ("MATCH_FOUND".equals(eventType)) {
                    String matchId = json.get("matchId").getAsString();
                    String opponentId = json.get("opponentId").getAsString();

                    clientState.handleMatchFound(matchId, opponentId);

                    String gameTopic = "game." + matchId;
                    rabbitChannel.queueBind(queueName, "game_events_exchange", gameTopic);
                    
                } else {
                    clientState.handleGameEvent(json);
                }

            } catch (Exception e) {
                clientState.printPrompt();
            }
        };

        CancelCallback cancelCallback = consumerTag -> {
            System.out.println(" [!] Consumidor cancelado: " + consumerTag);
        };

        rabbitChannel.basicConsume(queueName, true, deliverCallback, cancelCallback);
    }

    public void stop() throws Exception {
        if (rabbitChannel != null) rabbitChannel.close();
        if (connection != null) connection.close();
    }
}