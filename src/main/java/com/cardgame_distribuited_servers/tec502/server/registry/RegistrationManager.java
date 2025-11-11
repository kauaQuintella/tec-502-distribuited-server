package com.cardgame_distribuited_servers.tec502.server.registry;

import com.cardgame_distribuited_servers.tec502.server.config.ServerProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RegistrationManager implements ApplicationRunner {

    private final ServerProperties serverProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public RegistrationManager(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @Override
    public void run(ApplicationArguments args) {

        ServerInfo selfInfo = new ServerInfo(
                serverProperties.getId(),
                serverProperties.getId(), 
                serverProperties.getPort()
        );

        for (String peerUrl : serverProperties.getPeerUrls()) {
            try {
                String registryUrl = peerUrl + "/api/registry/register";
                restTemplate.postForEntity(registryUrl, selfInfo, Void.class);
                System.out.println("Registado com sucesso no peer: " + peerUrl);
            } catch (Exception e) {
                System.err.println("Falha ao registar no peer " + peerUrl + ": " + e.getMessage());
            }
        }
    }
}