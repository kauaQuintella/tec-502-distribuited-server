package com.cardgame_distribuited_servers.tec502.server.registry;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class HealthCheckScheduler {

    private final RegistryService registryService;
    private final RestTemplate restTemplate = new RestTemplate();

    public HealthCheckScheduler(RegistryService registryService) {
        this.registryService = registryService;
    }

    // Executa a cada 10 segundos
    @Scheduled(fixedRate = 10000)
    public void performHealthChecks() {
        System.out.println("A executar Health Checks...");
        // Pega uma cópia da lista para evitar problemas de concorrência
        var serversToCheck = List.copyOf(registryService.getOnlineServers());

        for (ServerInfo server : serversToCheck) {
            try {
                String healthUrl = "http://" + server.address() + ":" + server.port() + "/actuator/health";
                // Se a chamada falhar (lançar exceção), o servidor está offline
                restTemplate.getForObject(healthUrl, String.class);
            } catch (Exception e) {
                System.err.println("Health check falhou para " + server.serverId() + ". A remover da lista.");
                registryService.unregister(server.serverId());
            }
        }
    }
}