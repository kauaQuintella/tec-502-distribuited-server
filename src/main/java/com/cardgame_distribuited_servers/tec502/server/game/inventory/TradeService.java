package com.cardgame_distribuited_servers.tec502.server.game.inventory;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
import com.cardgame_distribuited_servers.tec502.server.network.rest.AcceptTradeRequest;
import com.cardgame_distribuited_servers.tec502.server.network.rest.ProposeTradeRequest;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TradeService {

    private final RaftClientService raftClientService;
    private final EventPublisherService eventPublisher;
    private final Gson gson = new Gson();
    private final AtomicLong tradeRaftKeyCounter = new AtomicLong(3_000_000_000L);

    public TradeService(RaftClientService raftClientService, EventPublisherService eventPublisher) {
        this.raftClientService = raftClientService;
        this.eventPublisher = eventPublisher;
    }

    public ResponseEntity<String> proposeTrade(String fromPlayerId, ProposeTradeRequest req) {
        Optional<RaftSkinData> mySkinData = findSkinInRaft(req.mySkinId());
        if (mySkinData.isEmpty() || !fromPlayerId.equals(mySkinData.get().skin().ownerId())) {
            return ResponseEntity.status(400).body("Você não possui a skin que está a oferecer.");
        }

        Optional<RaftSkinData> theirSkinData = findSkinInRaft(req.theirSkinId());
        if (theirSkinData.isEmpty() || !req.toPlayerId().equals(theirSkinData.get().skin().ownerId())) {
            return ResponseEntity.status(400).body("O outro jogador não possui a skin que você está a pedir.");
        }

        String offerId = UUID.randomUUID().toString();
        TradeOffer offer = new TradeOffer(offerId, fromPlayerId, req.toPlayerId(), req.mySkinId(), req.theirSkinId());

        JsonElement payload = gson.toJsonTree(offer);
        RaftEntry raftEntry = new RaftEntry("TRADE_OFFER", payload);
        long raftKey = tradeRaftKeyCounter.getAndIncrement();

        raftClientService.submitOperation(raftKey, gson.toJson(raftEntry));

        String topic = "user." + req.toPlayerId();
        String message = gson.toJson(Map.of("type", "TRADE_PROPOSED", "offer", offer));
        eventPublisher.publishMessage(topic, message);

        return ResponseEntity.ok("Proposta de troca enviada.");
    }

    public ResponseEntity<String> acceptTrade(String acceptingPlayerId, AcceptTradeRequest req) {
        Optional<RaftOfferData> offerDataOpt = findOfferInRaft(req.offerId());

        if (offerDataOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Proposta expirada ou inválida.");
        }

        RaftOfferData offerData = offerDataOpt.get();
        TradeOffer offer = offerData.offer();

        if (!offer.toPlayerId().equals(acceptingPlayerId)) {
            return ResponseEntity.status(403).body("Você não é o destinatário desta proposta.");
        }

        boolean deleted = raftClientService.deleteEntry(offerData.raftKey());
        if (!deleted) {
            return ResponseEntity.status(409).body("Conflito: A troca já foi processada.");
        }

        RaftSkinData skinA = findSkinInRaft(offer.fromSkinId()).orElse(null); // Skin do Player A
        RaftSkinData skinB = findSkinInRaft(offer.toSkinId()).orElse(null);   // Skin do Player B

        if (skinA == null || !offer.fromPlayerId().equals(skinA.skin().ownerId()) ||
                skinB == null || !offer.toPlayerId().equals(skinB.skin().ownerId())) {
            return ResponseEntity.status(412).body("Pré-condição falhou: Um dos jogadores não possui mais a skin.");
        }

        Skin updatedSkinA = skinA.skin().withNewOwner(offer.toPlayerId()); // A -> B
        Skin updatedSkinB = skinB.skin().withNewOwner(offer.fromPlayerId()); // B -> A

        updateSkinInRaft(skinA.raftKey(), updatedSkinA);
        updateSkinInRaft(skinB.raftKey(), updatedSkinB);

        String msgA = gson.toJson(Map.of("type", "TRADE_COMPLETE", "status", "SUCCESS", "received", updatedSkinB, "sent", updatedSkinA));
        String msgB = gson.toJson(Map.of("type", "TRADE_COMPLETE", "status", "SUCCESS", "received", updatedSkinA, "sent", updatedSkinB));

        eventPublisher.publishMessage("user." + offer.fromPlayerId(), msgA);
        eventPublisher.publishMessage("user." + offer.toPlayerId(), msgB);

        return ResponseEntity.ok("Troca concluída com sucesso.");
    }

    private Optional<RaftSkinData> findSkinInRaft(String skinId) {
        List<Entry> allEntries = raftClientService.getAllEntries();
        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            if (jsonValue == null || jsonValue.isEmpty()) continue; // Ignora nulos

            try {
                RaftEntry wrapper = gson.fromJson(jsonValue, RaftEntry.class);

                if (wrapper != null && "SKIN".equals(wrapper.type()) && wrapper.payload() != null) {
                    Skin skin = gson.fromJson(wrapper.payload(), Skin.class);
                    if (skin != null && skinId.equals(skin.id())) {
                        return Optional.of(new RaftSkinData(raftEntry.getKey(), skin));
                    }
                }
            } catch (JsonSyntaxException e) {
                System.err.println("Ignorando entrada Raft malformada (não é um RaftEntry): Key=" + raftEntry.getKey());
            } catch (Exception e) {
                System.err.println("Erro inesperado ao processar findSkinInRaft: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    private Optional<RaftOfferData> findOfferInRaft(String offerId) {
        List<Entry> allEntries = raftClientService.getAllEntries();
        for (Entry raftEntry : allEntries) {
            String jsonValue = raftEntry.getVal();
            if (jsonValue == null || jsonValue.isEmpty()) continue; // Ignora nulos

            try {
                RaftEntry wrapper = gson.fromJson(jsonValue, RaftEntry.class);

                if (wrapper != null && "TRADE_OFFER".equals(wrapper.type()) && wrapper.payload() != null) {
                    TradeOffer offer = gson.fromJson(wrapper.payload(), TradeOffer.class);
                    if (offer != null && offerId.equals(offer.offerId())) {
                        return Optional.of(new RaftOfferData(raftEntry.getKey(), offer));
                    }
                }
            } catch (JsonSyntaxException e) {
                System.err.println("Ignorando entrada Raft malformada (não é um RaftEntry): Key=" + raftEntry.getKey());
            } catch (Exception e) {
                System.err.println("Erro inesperado ao processar findOfferInRaft: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    private void updateSkinInRaft(long raftKey, Skin skin) {
        JsonElement payload = gson.toJsonTree(skin);
        RaftEntry raftEntry = new RaftEntry("SKIN", payload);
        raftClientService.submitOperation(raftKey, gson.toJson(raftEntry));
    }

    private record RaftSkinData(long raftKey, Skin skin) {}
    private record RaftOfferData(long raftKey, TradeOffer offer) {}
}