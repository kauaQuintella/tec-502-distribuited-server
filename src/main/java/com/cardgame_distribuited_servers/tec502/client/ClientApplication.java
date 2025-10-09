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

        // Declara a exchange para garantir que ela existe.
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        // --- LÓGICA DE INSCRIÇÃO (SUBSCRIBE) ---
        // Cria uma fila exclusiva, temporária e que será auto-deletada.
        String queueName = channel.queueDeclare().getQueue();

        // O ID do usuário logado. No seu jogo real, isso viria após o login.
        String userId = "kaua123";

        // Cria um "binding" (ligação) entre a exchange e a nossa fila.
        // Apenas mensagens com a routing key "user.kaua123" serão entregues a esta fila.
        String routingKey = "user." + userId;
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [*] Cliente iniciado. Aguardando notificações no tópico '" + routingKey + "'");

        // --- PROCESSAMENTO DAS MENSAGENS RECEBIDAS ---
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [x] Notificação Recebida: '" + message + "'");
            // AQUI você adicionaria a lógica para interpretar o JSON e atualizar a interface do jogo.
        };

        // Começa a escutar a fila indefinidamente.
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });

        // Mantenha a aplicação rodando para continuar ouvindo.
        // Em uma aplicação real, isso seria gerenciado por threads.
        System.out.println(" [i] Pressione CTRL+C para sair.");
        Thread.currentThread().join();
    }
}