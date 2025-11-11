package com.cardgame_distribuited_servers.tec502.server.game.matchmaking;

import com.cardgame_distribuited_servers.tec502.server.game.core.GameService;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock
    private RaftClientService raftClientService;
    @Mock
    private EventPublisherService eventPublisherService;
    @Mock
    private GameService gameService;

    @InjectMocks
    private MatchmakingService matchmakingService;

    @Captor
    private ArgumentCaptor<Long> keyCaptor;
    @Captor
    private ArgumentCaptor<String> valueCaptor;

    private final Gson gson = new Gson();

    @Test
    void deveSubmeterEntradaCorretaParaRaftAoEntrarNaFila() {
        String playerId = "player-join";
        matchmakingService.joinQueue(playerId);

        verify(raftClientService).submitOperation(keyCaptor.capture(), valueCaptor.capture());

        String submittedJson = valueCaptor.getValue();
        RaftEntry submittedEntry = gson.fromJson(submittedJson, RaftEntry.class);

        assertEquals("MATCHMAKING_ENTRY", submittedEntry.type());

        MatchmakingQueueEntry queueEntry = gson.fromJson(submittedEntry.payload(), MatchmakingQueueEntry.class);
        assertEquals(playerId, queueEntry.playerId());
    }

    @Test
    void naoDeveFormarPartidaComMenosDeDoisJogadores() {
        Entry p1Entry = createMatchmakingEntry(1L, "p1", 1000L);
        when(raftClientService.getAllEntries()).thenReturn(List.of(p1Entry));

        matchmakingService.findAndFormMatch();

        verify(gameService, never()).createGame(any(), any(), any());
        verify(raftClientService, never()).deleteEntry(anyLong());
    }

    @Test
    void deveFormarPartidaQuandoDoisJogadoresEstaoDisponiveis() {
        Entry p1Entry = createMatchmakingEntry(1L, "p1", 1000L);
        Entry p2Entry = createMatchmakingEntry(2L, "p2", 1001L);

        when(raftClientService.getAllEntries()).thenReturn(List.of(p1Entry, p2Entry));
        when(raftClientService.deleteEntry(1L)).thenReturn(true);
        when(raftClientService.deleteEntry(2L)).thenReturn(true);

        matchmakingService.findAndFormMatch();

        ArgumentCaptor<Long> deleteCaptor = ArgumentCaptor.forClass(Long.class);
        verify(raftClientService, times(2)).deleteEntry(deleteCaptor.capture());
        assertTrue(deleteCaptor.getAllValues().containsAll(List.of(1L, 2L)));

        verify(gameService, times(1)).createGame(anyString(), eq("p1"), eq("p2"));
    }

    @Test
    void naoDeveFormarPartidaSeHouverConflitoNoRaft() {
        Entry p1Entry = createMatchmakingEntry(1L, "p1", 1000L);
        Entry p2Entry = createMatchmakingEntry(2L, "p2", 1001L);

        when(raftClientService.getAllEntries()).thenReturn(List.of(p1Entry, p2Entry));
        when(raftClientService.deleteEntry(1L)).thenReturn(true);
        when(raftClientService.deleteEntry(2L)).thenReturn(false);

        matchmakingService.findAndFormMatch();

        verify(gameService, never()).createGame(any(), any(), any());
    }

    @Test
    void deveIgnorarEntradasDeSkinAoFormarPartida() {
        Entry p1Entry = createMatchmakingEntry(1L, "p1", 1000L);
        Entry skinEntry = createSkinEntry(2L, "SKIN_RARE");
        Entry p2Entry = createMatchmakingEntry(3L, "p2", 1002L);

        when(raftClientService.getAllEntries()).thenReturn(List.of(p1Entry, skinEntry, p2Entry));
        when(raftClientService.deleteEntry(1L)).thenReturn(true);
        when(raftClientService.deleteEntry(3L)).thenReturn(true);

        matchmakingService.findAndFormMatch();

        ArgumentCaptor<Long> deleteCaptor = ArgumentCaptor.forClass(Long.class);
        verify(raftClientService, times(2)).deleteEntry(deleteCaptor.capture());
        assertTrue(deleteCaptor.getAllValues().containsAll(List.of(1L, 3L)));

        verify(gameService, times(1)).createGame(anyString(), eq("p1"), eq("p2"));
    }

    private Entry createMatchmakingEntry(Long key, String playerId, long timestamp) {
        MatchmakingQueueEntry queueEntry = new MatchmakingQueueEntry(timestamp, playerId);
        JsonElement payload = gson.toJsonTree(queueEntry);
        RaftEntry raftEntry = new RaftEntry("MATCHMAKING_ENTRY", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }

    private Entry createSkinEntry(Long key, String skinName) {
        String skinJson = "{\"id\":\"" + skinName + "\", \"nome\":\"" + skinName + "\"}";
        JsonElement payload = gson.fromJson(skinJson, JsonElement.class);
        RaftEntry raftEntry = new RaftEntry("SKIN", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }
}