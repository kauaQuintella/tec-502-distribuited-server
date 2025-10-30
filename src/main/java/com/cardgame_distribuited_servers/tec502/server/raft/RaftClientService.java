package com.cardgame_distribuited_servers.tec502.server.raft;

import com.google.gson.JsonElement;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;

@Service
public class RaftClientService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String RAFT_CLIENT_URL = "http://raft-client:8080/api/v1";
    private final List<String> raftPeers = List.of(
            "http://raft-server-1:8081",
            "http://raft-server-2:8082",
            "http://raft-server-3:8083"
    );

    public boolean submitOperation(Long key, String value) {
        String leaderUrl = getLeaderUrl();
        if (leaderUrl == null) {
            System.err.println("Operação falhou: Nenhum líder do Raft disponível.");
        }

        Entry operation = new Entry(key, value);

        try {
            // Envia a operação POST para o endpoint /log do líder
            restTemplate.postForObject(leaderUrl + "/api/v1/log", operation, String.class);
            System.out.println("Operação submetida ao líder do Raft (" + leaderUrl + "): " + operation);

        } catch (Exception e) {
            System.err.println("Falha ao submeter operação ao líder do Raft: " + e.getMessage());
            return false;
        }
        return true;
    }

    public String getLeaderUrl() {
        for (String peerUrl : raftPeers) {
            try {
                // Cada nó do Raft expõe um endpoint /context que nos diz o seu estado
                Map<String, Object> context = restTemplate.getForObject(peerUrl + "/api/v1/context", Map.class);
                if (context != null && "LEADER".equals(context.get("state"))) {
                    System.out.println("Líder do Raft encontrado: " + peerUrl);
                    return peerUrl;
                }
            } catch (Exception e) {
                // Ignora os nós que estão offline
            }
        }
        System.err.println("Nenhum líder do Raft foi encontrado.");
        return null;
    }

    public List<Entry> getAllEntries() {
        String leaderUrl = getLeaderUrl(); // Podemos ler de qualquer nó, mas usar o líder é mais simples
        if (leaderUrl == null) return List.of();

        try {
            // O endpoint /storage do Raft retorna a lista de todas as chaves-valores
            ResponseEntity<List<Entry>> response = restTemplate.exchange(
                    leaderUrl + "/api/v1/storage",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Entry>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Falha ao obter entradas do Raft: " + e.getMessage());
            return List.of();
        }
    }

    public boolean deleteEntry(Long key) {
        String leaderUrl = getLeaderUrl();
        if (leaderUrl == null) {
            System.err.println("Operação DELETE falhou: Nenhum líder do Raft disponível.");
            return false;
        }

        try {
            // O endpoint /log/{key} com método DELETE apaga a entrada
            restTemplate.delete(leaderUrl + "/api/v1/log/" + key);
            System.out.println("Operação DELETE submetida ao líder do Raft para a chave: " + key);
            return true;
        } catch (Exception e) {
            System.err.println("Falha ao submeter DELETE ao líder do Raft para a chave " + key + ": " + e.getMessage());
            // Num cenário real, precisaríamos de tratar erros específicos (ex: 404 Not Found se a chave já não existe)
            return false;
        }
    }
}
