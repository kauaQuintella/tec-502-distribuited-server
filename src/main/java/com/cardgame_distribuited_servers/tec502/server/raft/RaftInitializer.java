package com.cardgame_distribuited_servers.tec502.server.raft;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.google.gson.Gson;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RaftInitializer implements ApplicationRunner {

    private final RaftClientService raftClientService;
    private final Gson gson = new Gson();
    private boolean alreadyInitialized = false; // Flag simples para evitar múltiplas inicializações

    public RaftInitializer(RaftClientService raftClientService) {
        this.raftClientService = raftClientService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // TODO: Implementar uma forma mais robusta de verificar se já foi inicializado
        if (alreadyInitialized) return;

        System.out.println("A inicializar o inventário de Skins no Raft...");
        List<Skin> initialSkins = generateInitialSkins();
        Collections.shuffle(initialSkins); // Embaralha para aleatoriedade

        AtomicLong keyCounter = new AtomicLong(1); // Usar AtomicLong para segurança em ambientes concorrentes

        for (Skin skin : initialSkins) {
            String skinJson = gson.toJson(skin);
            raftClientService.submitOperation(keyCounter.getAndIncrement(), skinJson);
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

        // Skins Comuns - 140 de cada
        for (int i = 0; i < 140; i++) {
            skins.add(new Skin("FOGO_C_" + i, "Chama Simples", "Comum"));
            skins.add(new Skin("AGUA_C_" + i, "Gota de Orvalho", "Comum"));
            skins.add(new Skin("NATUREZA_C_" + i, "Folha Verdejante", "Comum"));
        }

        // Skins Lendárias - 10 de cada
        for (int i = 0; i < 10; i++) {
            skins.add(new Skin("FOGO_L_" + i, "Hades", "Lendário"));
            skins.add(new Skin("AGUA_L_" + i, "Neptuno", "Lendário"));
            skins.add(new Skin("NATUREZA_L_" + i, "Gaia", "Lendário"));
        }

        // Embaralha o estoque para que a ordem seja aleatória
        Collections.shuffle(skins);
        System.out.println("SkinsManager inicializado com " + skins.size() + " skins únicas.");

        return skins;
    }
}