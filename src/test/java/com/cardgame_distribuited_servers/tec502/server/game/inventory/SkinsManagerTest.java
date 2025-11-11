package com.cardgame_distribuited_servers.tec502.server.game.inventory;

import com.cardgame_distribuited_servers.tec502.server.game.UserService;
import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkinsManagerTest {

    @Mock
    private RaftClientService raftClientService;
    @Mock
    private UserService userService;
    @Mock
    private EventPublisherService eventPublisher;

    @InjectMocks
    private SkinsManager skinsManager;

    @Captor
    private ArgumentCaptor<String> raftValueCaptor;
    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private final Gson gson = new Gson();
    private final String playerId = "player-test";
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(playerId);
        lenient().when(userService.findUser(playerId)).thenReturn(Optional.of(testUser));
    }

    private Entry createSkinEntry(Long key, Skin skin) {
        JsonElement payload = gson.toJsonTree(skin);
        RaftEntry raftEntry = new RaftEntry("SKIN", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }

    private Entry createMatchmakingEntry(Long key, String playerId) {
        String matchJson = "{\"type\":\"MATCHMAKING_ENTRY\", \"payload\":{\"playerId\":\"" + playerId + "\"}}";
        return new Entry(key, matchJson);
    }

    @Test
    void deveRetornarEmptySeUsuarioNaoEncontrado() {
        when(userService.findUser("unknown")).thenReturn(Optional.empty());
        Optional<Skin> result = skinsManager.abrirPacote("unknown");
        assertTrue(result.isEmpty());
        verify(raftClientService, never()).getAllEntries();
    }

    @Test
    void deveRetornarEmptySeNenhumaSkinDisponivel() {
        when(raftClientService.getAllEntries()).thenReturn(List.of());
        Optional<Skin> result = skinsManager.abrirPacote(playerId);
        assertTrue(result.isEmpty());
    }

    @Test
    void deveIgnorarSkinsJaPossuidas() {
        Skin ownedSkin = new Skin("S1", "Skin Possuída", "Raro", "outro-player");
        Entry skinEntry = createSkinEntry(100L, ownedSkin);
        when(raftClientService.getAllEntries()).thenReturn(List.of(skinEntry));

        Optional<Skin> result = skinsManager.abrirPacote(playerId);

        assertTrue(result.isEmpty());
        verify(raftClientService, never()).submitOperation(anyLong(), any());
    }

    @Test
    void deveIgnorarEntradasQueNaoSaoSkins() {
        Entry matchEntry = createMatchmakingEntry(1L, "p-queue");
        when(raftClientService.getAllEntries()).thenReturn(List.of(matchEntry));

        Optional<Skin> result = skinsManager.abrirPacote(playerId);
        assertTrue(result.isEmpty());
        verify(raftClientService, never()).submitOperation(anyLong(), any());
    }

    @Test
    void deveAdquirirSkinComSucesso() {
        long skinRaftKey = 100L;
        Skin unownedSkin = new Skin("S1", "Skin Rara", "Raro", null);
        Entry skinEntry = createSkinEntry(skinRaftKey, unownedSkin);

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinEntry));
        when(raftClientService.submitOperation(eq(skinRaftKey), anyString())).thenReturn(true);

        Optional<Skin> result = skinsManager.abrirPacote(playerId);

        assertTrue(result.isPresent());
        assertEquals("Skin Rara", result.get().nome());
        assertEquals(playerId, result.get().ownerId()); 

        verify(raftClientService).submitOperation(eq(skinRaftKey), raftValueCaptor.capture());

        RaftEntry savedEntry = gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class);
        Skin savedSkin = gson.fromJson(savedEntry.payload(), Skin.class);

        assertEquals("SKIN", savedEntry.type());
        assertEquals(playerId, savedSkin.ownerId()); 

        verify(eventPublisher).publishMessage(topicCaptor.capture(), messageCaptor.capture());
        assertEquals("user." + playerId, topicCaptor.getValue());
        assertTrue(messageCaptor.getValue().contains("INVENTORY_UPDATE"));
    }

    @Test
    void deveFalharSeTodasTentativasDeAtualizacaoFalharem() {
        Skin unownedSkin1 = new Skin("S1", "Skin 1", "Raro", null);
        Skin unownedSkin2 = new Skin("S2", "Skin 2", "Raro", null);
        Entry skinEntry1 = createSkinEntry(100L, unownedSkin1);
        Entry skinEntry2 = createSkinEntry(101L, unownedSkin2);

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinEntry1, skinEntry2));
        when(raftClientService.submitOperation(anyLong(), anyString())).thenReturn(false);

        Optional<Skin> result = skinsManager.abrirPacote(playerId);

        assertTrue(result.isEmpty());
        verify(raftClientService, times(2)).submitOperation(anyLong(), anyString());
    }
}