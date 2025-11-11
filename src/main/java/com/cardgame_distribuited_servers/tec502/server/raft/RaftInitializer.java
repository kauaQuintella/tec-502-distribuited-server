package com.cardgame_distribuited_servers.tec502.server.raft;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RaftInitializer implements ApplicationRunner {

    private final RaftClientService raftClientService;
    private final Gson gson = new Gson();
    private boolean alreadyInitialized = false;

    public RaftInitializer(RaftClientService raftClientService) {
        this.raftClientService = raftClientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (alreadyInitialized) return;

        System.out.println("A inicializar o inventário de Skins no Raft...");
        List<Skin> initialSkins = generateInitialSkins();
        Collections.shuffle(initialSkins);
        AtomicLong keyCounter = new AtomicLong(1);

        for (Skin skin : initialSkins) {
            JsonElement skinPayload = gson.toJsonTree(skin);
            RaftEntry raftEntry = new RaftEntry("SKIN", skinPayload);
            String entryJson = gson.toJson(raftEntry);

            raftClientService.submitOperation(keyCounter.getAndIncrement(), entryJson);
        }

        System.out.println("Inventário de Skins inicializado no Raft com " + initialSkins.size() + " itens.");
        alreadyInitialized = true;
    }

    private List<Skin> generateInitialSkins() {
        List<Skin> skins = new ArrayList<>();
        String unowned = null;

        // Skins Raras - 20 de cada
        for (int i = 0; i < 20; i++) {
            skins.add(new Skin("FOGO_R_" + i, "Fogo Infernal", "Raro", unowned));
            skins.add(new Skin("AGUA_R_" + i, "Tsunami", "Raro", unowned));
            skins.add(new Skin("NATUREZA_R_" + i, "Avatar da Floresta", "Raro", unowned));
        }


        // Skins Comuns - 50 de cada
        for (int i = 0; i < 50; i++) {
            skins.add(new Skin("FOGO_C_" + i, "Chama Simples", "Comum", unowned));
            skins.add(new Skin("AGUA_C_" + i, "Gota de Orvalho", "Comum", unowned));
            skins.add(new Skin("NATUREZA_C_" + i, "Folha Verdejante", "Comum", unowned));
        }

        // Skins Lendárias - 5 de cada
        for (int i = 0; i < 5; i++) {
            skins.add(new Skin("FOGO_L_" + i, "Hades", "Lendário", unowned));
            skins.add(new Skin("AGUA_L_" + i, "Neptuno", "Lendário", unowned));
            skins.add(new Skin("NATUREZA_L_" + i, "Gaia", "Lendário", unowned));
        }

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrupted during sleep.");
        }

        Collections.shuffle(skins);
        System.out.println("SkinsManager inicializado com " + skins.size() + " skins únicas.");

        return skins;
    }
}