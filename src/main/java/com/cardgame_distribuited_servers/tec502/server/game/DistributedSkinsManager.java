// Crie em /server/game/DistributedSkinsManager.java
package com.cardgame_distribuited_servers.tec502.server.game;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.raft.operations.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class DistributedSkinsManager {

    private final RaftClientService raftClientService;
    private final Gson gson = new Gson();
    private final Random random = new Random();

    public DistributedSkinsManager(RaftClientService raftClientService) {
        this.raftClientService = raftClientService;
    }

    public Optional<Skin> abrirPacote() {
        List<Entry> availableEntries = raftClientService.getAllEntries();

        if (availableEntries.isEmpty()) {
            System.out.println("Nenhuma skin disponível no Raft.");
            return Optional.empty();
        }

        // Tentar aleatoriamente até conseguir uma skin ou esgotar as tentativas
        int attempts = 0;
        int maxAttempts = Math.min(availableEntries.size(), 5); // Limita as tentativas

        while (attempts < maxAttempts) {
            Entry randomEntry = availableEntries.get(random.nextInt(availableEntries.size()));
            Long keyToDelete = randomEntry.getKey();

            // Tenta apagar a entrada no Raft. Esta é a operação crítica!
            boolean success = raftClientService.deleteEntry(keyToDelete);

            if (success) {
                try {
                    // Se conseguiu apagar, desserializa a skin e retorna
                    Skin acquiredSkin = gson.fromJson(randomEntry.getVal(), Skin.class);
                    System.out.println("Skin adquirida com sucesso via Raft: " + acquiredSkin.getNome());
                    return Optional.of(acquiredSkin);
                } catch (Exception e) {
                    System.err.println("Erro ao desserializar skin do Raft: " + randomEntry.getVal());
                    // Continua a tentar com outra skin
                }
            } else {
                // Se falhou (talvez outro servidor já a reclamou), tenta novamente
                System.out.println("Falha ao reclamar a skin com chave " + keyToDelete + ". A tentar novamente...");
                // Recarrega a lista para obter o estado mais recente
                availableEntries = raftClientService.getAllEntries();
                if (availableEntries.isEmpty()) break; // Sai se o inventário ficou vazio entretanto
            }
            attempts++;
        }

        System.out.println("Não foi possível adquirir uma skin após " + maxAttempts + " tentativas.");
        return Optional.empty();
    }
}