package com.cardgame_distribuited_servers.tec502.server.game.inventory;

import com.cardgame_distribuited_servers.tec502.server.game.UserService;
import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin; // <-- Usa Skin
import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService; // <-- Importar
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SkinsManager {

    private final RaftClientService raftClientService;
    private final Gson gson = new Gson();
    private final Random random = new Random();
    private final UserService userService;
    private final EventPublisherService eventPublisher;

    public SkinsManager(RaftClientService raftClientService, UserService userService, EventPublisherService eventPublisher) {
        this.raftClientService = raftClientService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Skin> abrirPacote(String playerId) {
        Optional<User> userOpt = userService.findUser(playerId); 
        if (userOpt.isEmpty()) {
            System.err.println("Tentativa de abrir pacote por utilizador desconhecido: " + playerId);
            return Optional.empty();
        }

        List<Map.Entry<Long, Skin>> unownedSkins = new ArrayList<>();
        List<Entry> allEntries = raftClientService.getAllEntries();

        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            if (jsonValue == null || jsonValue.isEmpty()) continue;

            try {
                RaftEntry raftEntryWrapper = gson.fromJson(jsonValue, RaftEntry.class);
                if (raftEntryWrapper != null && "SKIN".equals(raftEntryWrapper.type())) {
                    Skin skin = gson.fromJson(raftEntryWrapper.payload(), Skin.class);

                    if (skin != null && skin.ownerId() == null) {
                        unownedSkins.add(new AbstractMap.SimpleEntry<>(raftEntry.getKey(), skin));
                    }
                }
            } catch (JsonSyntaxException e) { /* ignora */ }
        }

        if (unownedSkins.isEmpty()) {
            System.out.println("Nenhuma skin disponível no pool do Raft para " + playerId);
            return Optional.empty();
        }

        Collections.shuffle(unownedSkins);
        Skin acquiredSkin = null;
        boolean success = false;
        int attempts = 0;

        while (attempts < 5 && !success && !unownedSkins.isEmpty()) {
            Map.Entry<Long, Skin> entryToClaim = unownedSkins.remove(0);
            long raftKey = entryToClaim.getKey();
            Skin skinToClaim = entryToClaim.getValue();

            Skin updatedSkin = skinToClaim.withNewOwner(playerId);

            JsonElement payload = gson.toJsonTree(updatedSkin);
            RaftEntry raftEntry = new RaftEntry("SKIN", payload);
            String entryJson = gson.toJson(raftEntry);

            if (raftClientService.submitOperation(raftKey, entryJson)) {
                success = true;
                acquiredSkin = updatedSkin;
            }
            attempts++;
        }

        if (success) {
            System.out.println("Skin " + acquiredSkin.nome() + " adquirida por " + playerId + ".");

            String topic = "user." + playerId;
            String message = gson.toJson(Map.of("type", "INVENTORY_UPDATE", "action", "ADD", "skin", acquiredSkin));
            eventPublisher.publishMessage(topic, message);

            return Optional.of(acquiredSkin);
        }

        System.out.println("Não foi possível adquirir uma skin após " + attempts + " tentativas.");
        return Optional.empty();
    }
}