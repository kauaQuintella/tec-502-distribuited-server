package com.cardgame_distribuited_servers.tec502.server.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckSchedulerTest {

    @Mock
    private RegistryService registryService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private HealthCheckScheduler healthCheckScheduler;

    @Test
    void deveRemoverServidorSeHealthCheckFalhar() {

        ServerInfo serverOk = new ServerInfo("app-1", "app-1", 8080);
        ServerInfo serverFail = new ServerInfo("app-2", "app-2", 8080);
        when(registryService.getOnlineServers()).thenReturn(List.of(serverOk, serverFail));

        when(restTemplate.getForObject("http://app-1:8080/actuator/health", String.class))
                .thenReturn("{\"status\":\"UP\"}");

       when(restTemplate.getForObject("http://app-2:8080/actuator/health", String.class))
                .thenThrow(new ResourceAccessException("Connection refused"));


        healthCheckScheduler.performHealthChecks();

        verify(registryService).unregister("app-2");
        verify(registryService, never()).unregister("app-1");
    }
}