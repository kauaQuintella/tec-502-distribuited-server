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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private RaftClientService raftClientService;
    @Mock
    private EventPublisherService eventPublisher;

    @InjectMocks
    private TradeService tradeService;

    @Captor
    private ArgumentCaptor<String> raftValueCaptor;
    @Captor
    private ArgumentCaptor<Long> raftKeyCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private final Gson gson = new Gson();
    private final String playerAId = "player-A";
    private final String playerBId = "player-B";

    private Skin skinA;
    private Skin skinB;
    private Entry skinAEntry;
    private Entry skinBEntry;

    @BeforeEach
    void setUp() {
        skinA = new Skin("SKIN_A", "Skin do A", "Raro", playerAId);
        skinB = new Skin("SKIN_B", "Skin do B", "Comum", playerBId);

        skinAEntry = createSkinRaftEntry(100L, skinA);
        skinBEntry = createSkinRaftEntry(101L, skinB);
    }

    private Entry createRaftEntry(long key, String type, Object payload) {
        JsonElement payloadJson = gson.toJsonTree(payload);
        RaftEntry raftEntry = new RaftEntry(type, payloadJson);
        return new Entry(key, gson.toJson(raftEntry));
    }
    private Entry createSkinRaftEntry(long key, Skin skin) {
        return createRaftEntry(key, "SKIN", skin);
    }
    private Entry createOfferRaftEntry(long key, TradeOffer offer) {
        return createRaftEntry(key, "TRADE_OFFER", offer);
    }


    @Test
    void deveProporTrocaComSucesso() {

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinAEntry, skinBEntry));

        ProposeTradeRequest request = new ProposeTradeRequest(playerBId, "SKIN_A", "SKIN_B");
        ResponseEntity<String> response = tradeService.proposeTrade(playerAId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(raftClientService).submitOperation(anyLong(), raftValueCaptor.capture());
        RaftEntry savedEntry = gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class);
        assertEquals("TRADE_OFFER", savedEntry.type());

        verify(eventPublisher).publishMessage(eq("user." + playerBId), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("TRADE_PROPOSED"));
    }

    @Test
    void naoDeveProporTrocaSeNaoPossuirASkin() {
        skinA = skinA.withNewOwner("outro-player");
        skinAEntry = createSkinRaftEntry(100L, skinA);
        when(raftClientService.getAllEntries()).thenReturn(List.of(skinAEntry, skinBEntry));

        ProposeTradeRequest request = new ProposeTradeRequest(playerBId, "SKIN_A", "SKIN_B");
        ResponseEntity<String> response = tradeService.proposeTrade(playerAId, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(raftClientService, never()).submitOperation(anyLong(), any());
    }

    @Test
    void deveAceitarTrocaComSucesso() {
        long offerKey = 200L;
        TradeOffer offer = new TradeOffer("offer-123", playerAId, playerBId, "SKIN_A", "SKIN_B");
        Entry offerEntry = createOfferRaftEntry(offerKey, offer);

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinAEntry, skinBEntry, offerEntry));
        when(raftClientService.deleteEntry(offerKey)).thenReturn(true);
        when(raftClientService.submitOperation(anyLong(), anyString())).thenReturn(true);

        AcceptTradeRequest request = new AcceptTradeRequest("offer-123");
        ResponseEntity<String> response = tradeService.acceptTrade(playerBId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(raftClientService).deleteEntry(offerKey);
        verify(raftClientService, times(2)).submitOperation(raftKeyCaptor.capture(), raftValueCaptor.capture());

        Skin updatedSkinA = gson.fromJson(gson.fromJson(raftValueCaptor.getAllValues().get(0), RaftEntry.class).payload(), Skin.class);
        assertEquals(playerBId, updatedSkinA.ownerId());

        Skin updatedSkinB = gson.fromJson(gson.fromJson(raftValueCaptor.getAllValues().get(1), RaftEntry.class).payload(), Skin.class);
        assertEquals(playerAId, updatedSkinB.ownerId());

        verify(eventPublisher, times(2)).publishMessage(anyString(), messageCaptor.capture());
        assertTrue(messageCaptor.getAllValues().get(0).contains("TRADE_COMPLETE"));
        assertTrue(messageCaptor.getAllValues().get(1).contains("TRADE_COMPLETE"));
    }

    @Test
    void naoDeveAceitarTrocaSeConflitoNoRaft() {
        long offerKey = 200L;
        TradeOffer offer = new TradeOffer("offer-123", playerAId, playerBId, "SKIN_A", "SKIN_B");
        Entry offerEntry = createOfferRaftEntry(offerKey, offer);

        when(raftClientService.getAllEntries()).thenReturn(List.of(offerEntry));
        when(raftClientService.deleteEntry(offerKey)).thenReturn(false);

        AcceptTradeRequest request = new AcceptTradeRequest("offer-123");
        ResponseEntity<String> response = tradeService.acceptTrade(playerBId, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(raftClientService, never()).submitOperation(anyLong(), anyString());
    }

    @Test
    void naoDeveAceitarTrocaSeSkinNaoPertencerMaisAoDono() {
        long offerKey = 200L;
        TradeOffer offer = new TradeOffer("offer-123", playerAId, playerBId, "SKIN_A", "SKIN_B");
        Entry offerEntry = createOfferRaftEntry(offerKey, offer);

        skinA = skinA.withNewOwner("player-C");
        skinAEntry = createSkinRaftEntry(100L, skinA);

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinAEntry, skinBEntry, offerEntry));
        when(raftClientService.deleteEntry(offerKey)).thenReturn(true); // Deletou com sucesso

        AcceptTradeRequest request = new AcceptTradeRequest("offer-123");
        ResponseEntity<String> response = tradeService.acceptTrade(playerBId, request);

        assertEquals(HttpStatus.PRECONDITION_FAILED, response.getStatusCode());
        verify(raftClientService, never()).submitOperation(anyLong(), anyString());
    }
}