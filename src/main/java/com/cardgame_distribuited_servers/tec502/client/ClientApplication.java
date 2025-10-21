package com.cardgame_distribuited_servers.tec502.client;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ClientApplication {

    private static final String EXCHANGE_NAME = "game_events_exchange";

    public static void main(String[] args) throws Exception {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");//Fora do Docker: "localhost"; Outro Container: "rabbitmq".
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);

        String queueName = channel.queueDeclare().getQueue();

        String userId = "kaua123";

        String routingKey = "user." + userId;
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [*] Cliente iniciado. Aguardando notificações no tópico '" + routingKey + "'");


        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [x] Notificação Recebida: '" + message + "'");
        };


        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });

        System.out.println(" [i] Pressione CTRL+C para sair.");
        Thread.currentThread().join();
    }
}