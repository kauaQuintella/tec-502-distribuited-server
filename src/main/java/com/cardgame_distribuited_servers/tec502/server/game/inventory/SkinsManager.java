package com.cardgame_distribuited_servers.tec502.server.game.inventory;

import com.cardgame_distribuited_servers.tec502.server.game.UserService;
import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SkinsManager {

    private final RaftClientService raftClientService;
    private final Gson gson = new Gson();
    private final Random random = new Random();
    private final UserService userService;

    public SkinsManager(RaftClientService raftClientService, UserService userService) {
        this.raftClientService = raftClientService;
        this.userService = userService;
    }

    public Optional<Skin> abrirPacote(String playerId) {
        Optional<User> userOpt = userService.findUser(playerId);
        if (userOpt.isEmpty()) {
            System.err.println("Tentativa de abrir pacote por utilizador desconhecido: " + playerId);
            return Optional.empty();
        }
        User user = userOpt.get();

        List<Entry> allEntries = raftClientService.getAllEntries();

        // --- LÓGICA DE FILTRAGEM ATUALIZADA ---
        List<Map.Entry<Long, Skin>> availableSkins = new ArrayList<>();
        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            if (jsonValue == null || jsonValue.isEmpty()) continue;

            try {
                // 1. Desserializa para o wrapper genérico
                RaftEntry raftEntryWrapper = gson.fromJson(jsonValue, RaftEntry.class);

                // 2. Verifica o tipo
                if (raftEntryWrapper != null && "SKIN".equals(raftEntryWrapper.type())) {
                    // 3. Se for o tipo certo, desserializa o payload interno
                    Skin skin = gson.fromJson(raftEntryWrapper.payload(), Skin.class);

                    if (skin != null && skin.getId() != null) {
                        availableSkins.add(new AbstractMap.SimpleEntry<>(raftEntry.getKey(), skin));
                    }
                }
                // Se o tipo for "MATCHMAKING_ENTRY" ou outro, é simplesmente ignorado.

            } catch (JsonSyntaxException e) {
                // Ignora JSON malformado ou que não é um RaftEntry (talvez dados antigos)
            } catch (Exception e) {
                System.err.println("Erro inesperado ao processar entrada de skin: " + e.getMessage());
            }
        }
        // --- FIM DA LÓGICA ATUALIZADA ---

        if (availableSkins.isEmpty()) {
            System.out.println("Nenhuma skin disponível no Raft para " + playerId);
            return Optional.empty();
        }

        // Tenta adquirir a skin a partir da LISTA FILTRADA
        int attempts = 0;
        int maxAttempts = Math.min(availableSkins.size(), 5);

        while (attempts < maxAttempts) {
            Map.Entry<Long, Skin> randomEntry = availableSkins.get(random.nextInt(availableSkins.size()));
            Long keyToDelete = randomEntry.getKey();

            boolean success = raftClientService.deleteEntry(keyToDelete);

            if (success) {
                Skin acquiredSkin = randomEntry.getValue(); // Já temos o objeto Skin!
                user.getInventory().addSkin(acquiredSkin);
                System.out.println("Skin " + acquiredSkin.getNome() + " adquirida por " + playerId + ".");

                // TODO: Notificar o cliente via RabbitMQ

                return Optional.of(acquiredSkin);
            } else {
                System.out.println("Conflito ao reclamar skin (Key: " + keyToDelete + ") para " + playerId + ".");
                availableSkins.remove(randomEntry); // Remove da lista local para não tentar de novo
                if (availableSkins.isEmpty()) break;
            }
            attempts++;
        }

        System.out.println("Não foi possível adquirir uma skin após " + maxAttempts + " tentativas.");
        return Optional.empty();
    }
}