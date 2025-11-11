package com.cardgame_distribuited_servers.tec502.server.raft;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaftClientServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RaftClientService raftClientService; 

    private final String peer1 = "http://raft-server-1:8081";
    private final String peer2 = "http://raft-server-2:8082";
    private final String peer3 = "http://raft-server-3:8083";

    @Test
    void deveEncontrarLiderComSucesso() {

        when(restTemplate.getForObject(peer1 + "/api/v1/context", Map.class))
                .thenReturn(Map.of("state", "FOLLOWER"));
        when(restTemplate.getForObject(peer2 + "/api/v1/context", Map.class))
                .thenReturn(Map.of("state", "LEADER"));

        String leaderUrl = raftClientService.getLeaderUrl();

        assertEquals(peer2, leaderUrl);
    }

    @Test
    void deveEncontrarLiderMesmoSePeerAnteriorFalhar() {
       
        when(restTemplate.getForObject(peer1 + "/api/v1/context", Map.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        
        when(restTemplate.getForObject(peer2 + "/api/v1/context", Map.class))
                .thenReturn(Map.of("state", "LEADER"));

        String leaderUrl = raftClientService.getLeaderUrl();
        assertEquals(peer2, leaderUrl);
    }

    @Test
    void deveRetornarNullSeNenhumLiderForEncontrado() {
        when(restTemplate.getForObject(peer1 + "/api/v1/context", Map.class))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(restTemplate.getForObject(peer2 + "/api/v1/context", Map.class))
                .thenReturn(Map.of("state", "FOLLOWER"));
        when(restTemplate.getForObject(peer3 + "/api/v1/context", Map.class))
                .thenReturn(Map.of("state", "FOLLOWER"));

        String leaderUrl = raftClientService.getLeaderUrl();

        assertNull(leaderUrl);
    }

    @Test
    void submitOperationDeveFalharSeNenhumLiderForEncontrado() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("state", "FOLLOWER"));

        boolean success = raftClientService.submitOperation(1L, "test-value");

        assertFalse(success);
    }
}