package com.cardgame_distribuited_servers.tec502.server.game.inventory;

import com.cardgame_distribuited_servers.tec502.server.game.UserService;
import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import com.cardgame_distribuited_servers.tec502.server.raft.Entry;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkinsManagerTest {

    @Mock
    private RaftClientService raftClientService;

    @Mock
    private UserService userService; // Dependência injetada manualmente no construtor

    @InjectMocks
    private SkinsManager skinsManager;

    private final Gson gson = new Gson();
    private final String playerId = "player-test";
    private User testUser;

    @BeforeEach
    void setUp() {
        // REMOVA A CONFIGURAÇÃO DO MOCK DESTA SEÇÃO
        testUser = new User(playerId);
        // A linha abaixo foi removida daqui:
        // when(userService.findUser(playerId)).thenReturn(Optional.of(testUser));
    }

    @Test
    void deveRetornarEmptySeUsuarioNaoEncontrado() {
        when(userService.findUser("unknown")).thenReturn(Optional.empty());
        Optional<Skin> result = skinsManager.abrirPacote("unknown");
        assertTrue(result.isEmpty());
    }

    @Test
    void deveRetornarEmptySeNenhumaSkinDisponivel() {
        // ADICIONE A CONFIGURAÇÃO AQUI
        when(userService.findUser(playerId)).thenReturn(Optional.of(testUser));
        when(raftClientService.getAllEntries()).thenReturn(List.of());
        Optional<Skin> result = skinsManager.abrirPacote(playerId);
        assertTrue(result.isEmpty());
    }

    @Test
    void deveIgnorarEntradasQueNaoSaoSkins() {
        // ADICIONE A CONFIGURAÇÃO AQUI
        when(userService.findUser(playerId)).thenReturn(Optional.of(testUser));
        Entry matchEntry = createMatchmakingEntry(1L, "p-queue");
        when(raftClientService.getAllEntries()).thenReturn(List.of(matchEntry));

        Optional<Skin> result = skinsManager.abrirPacote(playerId);
        assertTrue(result.isEmpty());
        verify(raftClientService, never()).deleteEntry(anyLong());
    }

    @Test
    void deveAdquirirSkinComSucessoNaPrimeiraTentativa() {
        when(userService.findUser(playerId)).thenReturn(Optional.of(testUser));
        Skin skin = new Skin("S1", "Skin Rara", "Raro");
        Entry skinEntry = createSkinEntry(100L, skin);

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinEntry));
        when(raftClientService.deleteEntry(100L)).thenReturn(true);

        Optional<Skin> result = skinsManager.abrirPacote(playerId);

        assertTrue(result.isPresent());
        assertEquals("Skin Rara", result.get().getNome());
        assertEquals(1, testUser.getInventory().getSkins().size());
        assertEquals("Skin Rara", testUser.getInventory().getSkins().get(0).getNome());
        verify(raftClientService, times(1)).deleteEntry(100L);
    }

    @Test
    void deveFalharSeTodasTentativasDeDeleteFalharem() {
        Skin skin1 = new Skin("S1", "Skin 1", "Raro");
        Skin skin2 = new Skin("S2", "Skin 2", "Raro");
        Entry skinEntry1 = createSkinEntry(100L, skin1);
        Entry skinEntry2 = createSkinEntry(101L, skin2);

        when(raftClientService.getAllEntries()).thenReturn(List.of(skinEntry1, skinEntry2));
        // Todas as tentativas de delete falham (conflito)
        when(raftClientService.deleteEntry(anyLong())).thenReturn(false);

        Optional<Skin> result = skinsManager.abrirPacote(playerId);

        assertTrue(result.isEmpty());
        assertEquals(0, testUser.getInventory().getSkins().size());

        // O código tenta no máximo 'maxAttempts' (5), mas como só há 2 skins,
        // ele tentará 2 vezes (porque remove da lista local a cada falha)
        // O maxAttempts é Math.min(availableSkins.size(), 5) -> Math.min(2, 5) = 2
        verify(raftClientService, times(2)).deleteEntry(anyLong());
    }

    // Helper para criar entradas de Skin no formato RaftEntry
    private Entry createSkinEntry(Long key, Skin skin) {
        JsonElement payload = gson.toJsonTree(skin);
        RaftEntry raftEntry = new RaftEntry("SKIN", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }

    // Helper para criar entradas de matchmaking
    private Entry createMatchmakingEntry(Long key, String playerId) {
        String matchJson = "{\"playerId\":\"" + playerId + "\"}";
        JsonElement payload = gson.fromJson(matchJson, JsonElement.class);
        RaftEntry raftEntry = new RaftEntry("MATCHMAKING_ENTRY", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }
}