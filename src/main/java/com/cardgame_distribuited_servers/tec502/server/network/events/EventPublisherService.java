package com.cardgame_distribuited_servers.tec502.server.network.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    public EventPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatchFoundEvent(String userId, String matchId) {
        String topic = "user." + userId;
        String message = "{\"event\": \"MATCH_FOUND\", \"matchId\": \"" + matchId + "\"}";

        rabbitTemplate.convertAndSend("game_events_exchange", topic, message);

        System.out.println("Publicado no tópico " + topic + ": " + message);
    }
}
