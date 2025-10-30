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
            // --- ALTERAÇÃO AQUI ---
            // 1. Converte a skin para um JsonElement
            JsonElement skinPayload = gson.toJsonTree(skin);
            // 2. Cria o wrapper RaftEntry com o tipo "SKIN"
            RaftEntry raftEntry = new RaftEntry("SKIN", skinPayload);
            // 3. Serializa o wrapper completo
            String entryJson = gson.toJson(raftEntry);
            // --- FIM DA ALTERAÇÃO ---

            raftClientService.submitOperation(keyCounter.getAndIncrement(), entryJson);
        }

        System.out.println("Inventário de Skins inicializado no Raft com " + initialSkins.size() + " itens.");
        alreadyInitialized = true;
    }

    private List<Skin> generateInitialSkins() {
        List<Skin> skins = new ArrayList<>();

        // Skins Raras - 50 de cada
        for (int i = 0; i < 50; i++) {
            skins.add(new Skin("FOGO_R_" + i, "Fogo Infernal", "Raro"));
            skins.add(new Skin("AGUA_R_" + i, "Tsunami", "Raro"));
            skins.add(new Skin("NATUREZA_R_" + i, "Avatar da Floresta", "Raro"));
        }

        try {
            TimeUnit.SECONDS.sleep(2); // Pauses for 2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrupted during sleep.");
        }

        // Skins Comuns - 140 de cada
        for (int i = 0; i < 140; i++) {
            skins.add(new Skin("FOGO_C_" + i, "Chama Simples", "Comum"));
            skins.add(new Skin("AGUA_C_" + i, "Gota de Orvalho", "Comum"));
            skins.add(new Skin("NATUREZA_C_" + i, "Folha Verdejante", "Comum"));
        }

        try {
            TimeUnit.SECONDS.sleep(2); // Pauses for 2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrupted during sleep.");
        }

        // Skins Lendárias - 10 de cada
        for (int i = 0; i < 10; i++) {
            skins.add(new Skin("FOGO_L_" + i, "Hades", "Lendário"));
            skins.add(new Skin("AGUA_L_" + i, "Neptuno", "Lendário"));
            skins.add(new Skin("NATUREZA_L_" + i, "Gaia", "Lendário"));
        }

        try {
            TimeUnit.SECONDS.sleep(2); // Pauses for 2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrupted during sleep.");
        }

        // Embaralha o estoque para que a ordem seja aleatória
        Collections.shuffle(skins);
        System.out.println("SkinsManager inicializado com " + skins.size() + " skins únicas.");

        return skins;
    }
}