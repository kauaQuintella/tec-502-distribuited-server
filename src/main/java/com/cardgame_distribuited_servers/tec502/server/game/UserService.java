package com.cardgame_distribuited_servers.tec502.server.game;

import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final RaftClientService raftClientService;
    private final Gson gson;

    private final AtomicLong userRaftKeyCounter = new AtomicLong(1_000_000_000L);

    public UserService(RaftClientService raftClientService) {
        this.raftClientService = raftClientService;
        this.gson = new Gson();
    }

    public User loginOrRegisterUser(User user) {
    
        Optional<User> existingUser = findUserInRaft(user.getIdUser());

        if (existingUser.isPresent()) {
            System.out.println("Utilizador existente encontrado no Raft: " + user.getIdUser());
            return existingUser.get();
        }

        System.out.println("Novo utilizador. A registar no Raft: " + user.getIdUser());
        try {
            JsonElement userPayload = gson.toJsonTree(user);
            RaftEntry raftEntry = new RaftEntry("USER", userPayload);
            String entryJson = gson.toJson(raftEntry);

            long raftKey = userRaftKeyCounter.getAndIncrement();
            raftClientService.submitOperation(raftKey, entryJson);

            return user;
        } catch (Exception e) {
            System.err.println("Falha grave ao salvar novo Utilizador no Raft: " + e.getMessage());
            throw new RuntimeException("Falha ao registar utilizador no cluster", e);
        }
    }

    public Optional<User> findUser(String playerId) {
        return findUserInRaft(playerId);
    }

    private Optional<User> findUserInRaft(String playerId) {
        List<Entry> allEntries = raftClientService.getAllEntries();

        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            if (jsonValue == null || jsonValue.isEmpty()) continue;

            try {
                RaftEntry raftEntryWrapper = gson.fromJson(jsonValue, RaftEntry.class);

                if (raftEntryWrapper != null && "USER".equals(raftEntryWrapper.type())) {
                    User user = gson.fromJson(raftEntryWrapper.payload(), User.class);

                    if (user != null && playerId.equals(user.getIdUser())) {
                        return Optional.of(user);
                    }
                }
            } catch (Exception e) {
                // Ignora entradas malformadas ou de outro tipo (SKIN, GAME_STATE, etc)
            }
        }
        return Optional.empty();
    }
}