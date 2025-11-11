package com.cardgame_distribuited_servers.tec502.server.game.core;

import com.cardgame_distribuited_servers.tec502.server.game.entity.PlayRequest;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private EventPublisherService eventPublisher;

    @Mock
    private RaftClientService raftClientService;

    @InjectMocks
    private GameService gameService;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<Long> raftKeyCaptor; // Captor para a chave Raft
    @Captor
    private ArgumentCaptor<String> raftValueCaptor; // Captor para o valor Raft

    private final Gson gson = new Gson();
    private final String matchId = "test-match-123";
    private final String player1Id = "player-1";
    private final String player2Id = "player-2";

    @BeforeEach
    void setUp() {
        // Configuração 'lenient' (indulgente) para mocks que nem sempre são chamados
        lenient().when(raftClientService.submitOperation(anyLong(), any())).thenReturn(true);
        lenient().when(raftClientService.deleteEntry(anyLong())).thenReturn(true);
    }

    // Helper para criar uma 'Entry' (entrada Raft) simulada para um GameState
    private Entry createGameRaftEntry(long key, GameState gameState) {
        JsonElement payload = gson.toJsonTree(gameState);
        RaftEntry raftEntry = new RaftEntry("GAME_STATE", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }

    @Test
    void deveCriarJogoENotificarJogadores() {
        // Ação
        gameService.createGame(matchId, player1Id, player2Id);

        // Verificação (Verifica se salvou no Raft)
        verify(raftClientService).submitOperation(anyLong(), raftValueCaptor.capture());
        RaftEntry savedEntry = gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class);
        GameState savedGame = gson.fromJson(savedEntry.payload(), GameState.class);

        assertEquals("GAME_STATE", savedEntry.type());
        assertEquals(matchId, savedGame.getMatchId());

        // Verificação (Verifica se notificou os jogadores)
        verify(eventPublisher, times(2)).publishMessage(topicCaptor.capture(), messageCaptor.capture());

        assertEquals("user." + player1Id, topicCaptor.getAllValues().get(0));
        GameEvent.MatchFound eventP1 = gson.fromJson(messageCaptor.getAllValues().get(0), GameEvent.MatchFound.class);
        assertEquals(matchId, eventP1.matchId());
        assertEquals(player2Id, eventP1.opponentId());
    }

    @Test
    void deveReceberPrimeiraJogadaENotificarOponente() {
        // Configuração: Simula que o jogo existe no Raft
        long gameRaftKey = 12345L;
        GameState initialGame = new GameState(matchId, player1Id, player2Id);
        Entry gameEntry = createGameRaftEntry(gameRaftKey, initialGame);
        when(raftClientService.getAllEntries()).thenReturn(List.of(gameEntry));

        // Ação
        PlayRequest playRequest = new PlayRequest(player1Id, "FOGO");
        gameService.receivePlay(matchId, playRequest);

        // Verificação (Notificação Pub/Sub)
        verify(eventPublisher, times(1)).publishMessage(topicCaptor.capture(), messageCaptor.capture());
        assertEquals("game." + matchId, topicCaptor.getValue());
        GameEvent.PlayerAction event = gson.fromJson(messageCaptor.getValue(), GameEvent.PlayerAction.class);
        assertEquals("PLAYER_ACTION", event.type());
        assertEquals("JOGOU", event.action()); // Ação não é vazada

        // Verificação (Salvamento no Raft)
        // Deve salvar o estado atualizado (com 1 jogada)
        verify(raftClientService).submitOperation(eq(gameRaftKey), raftValueCaptor.capture());
        GameState updatedGame = gson.fromJson(gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class).payload(), GameState.class);

        assertEquals(1, updatedGame.getCurrentPlays().size());
        assertEquals("FOGO", updatedGame.getCurrentPlays().get(player1Id));
    }

    @Test
    void deveProcessarEmpateNaRodada() {
        // Configuração: Simula que o P1 já jogou
        long gameRaftKey = 12345L;
        GameState gameP1Played = new GameState(matchId, player1Id, player2Id);
        gameP1Played.getCurrentPlays().put(player1Id, "AGUA"); // P1 já jogou

        Entry gameEntry = createGameRaftEntry(gameRaftKey, gameP1Played);
        when(raftClientService.getAllEntries()).thenReturn(List.of(gameEntry));

        // Ação
        PlayRequest playRequestP2 = new PlayRequest(player2Id, "AGUA");
        gameService.receivePlay(matchId, playRequestP2); // P2 joga

        // Verificação (Notificação Pub/Sub)
        // 1x para PLAYER_ACTION (do P2), 1x para ROUND_RESULT
        verify(eventPublisher, times(2)).publishMessage(topicCaptor.capture(), messageCaptor.capture());

        // Pega a última mensagem (RoundResult)
        String roundResultJson = messageCaptor.getAllValues().get(1);
        GameEvent.RoundResult result = gson.fromJson(roundResultJson, GameEvent.RoundResult.class);

        assertEquals("ROUND_RESULT", result.type());
        assertNull(result.winnerId()); // Empate
        assertEquals("Ambos jogaram AGUA", result.reason());
        assertEquals(0, result.scores().get(player1Id)); // Placar 0

        // Verificação (Salvamento no Raft)
        // Deve salvar o estado atualizado (placar 0, jogadas limpas)
        verify(raftClientService, times(2)).submitOperation(eq(gameRaftKey), raftValueCaptor.capture());
        GameState finalGame = gson.fromJson(gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class).payload(), GameState.class);

        assertEquals(0, finalGame.getCurrentPlays().size()); // Jogadas limpas
        assertEquals(0, finalGame.getScores().get(player1Id));
    }

    @Test
    void deveProcessarVitoriaNaRodada() {
        // Configuração: Simula que o P1 já jogou
        long gameRaftKey = 12345L;
        GameState gameP1Played = new GameState(matchId, player1Id, player2Id);
        gameP1Played.getCurrentPlays().put(player1Id, "FOGO"); // P1 já jogou

        Entry gameEntry = createGameRaftEntry(gameRaftKey, gameP1Played);
        when(raftClientService.getAllEntries()).thenReturn(List.of(gameEntry));

        // Ação
        PlayRequest playRequestP2 = new PlayRequest(player2Id, "NATUREZA");
        gameService.receivePlay(matchId, playRequestP2); // P2 joga

        // Verificação (Notificação Pub/Sub)
        verify(eventPublisher, times(2)).publishMessage(topicCaptor.capture(), messageCaptor.capture());
        String roundResultJson = messageCaptor.getAllValues().get(1);
        GameEvent.RoundResult result = gson.fromJson(roundResultJson, GameEvent.RoundResult.class);

        assertEquals("ROUND_RESULT", result.type());
        assertEquals(player1Id, result.winnerId()); // P1 Venceu
        assertEquals("FOGO ganha de NATUREZA", result.reason());
        assertEquals(1, result.scores().get(player1Id)); // Placar 1

        // Verificação (Salvamento no Raft)
        // Deve salvar o estado atualizado (placar 1, jogadas limpas)
        verify(raftClientService, times(2)).submitOperation(eq(gameRaftKey), raftValueCaptor.capture());
        GameState finalGame = gson.fromJson(gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class).payload(), GameState.class);

        assertEquals(0, finalGame.getCurrentPlays().size()); // Jogadas limpas
        assertEquals(1, finalGame.getScores().get(player1Id));
    }

    @Test
    void deveProcessarJogadasAteOFimDoJogo() {
        long gameRaftKey = 12345L;

        // --- Setup Inicial (Jogo Vazio) ---
        GameState game = new GameState(matchId, player1Id, player2Id);
        Entry gameEntry = createGameRaftEntry(gameRaftKey, game);
        when(raftClientService.getAllEntries()).thenReturn(List.of(gameEntry));

        // --- Rodada 1: P1 Vence (1-0) ---
        gameService.receivePlay(matchId, new PlayRequest(player1Id, "AGUA"));
        // Atualiza o mock para o próximo scan
        game.getCurrentPlays().put(player1Id, "AGUA");
        when(raftClientService.getAllEntries()).thenReturn(List.of(createGameRaftEntry(gameRaftKey, game)));

        gameService.receivePlay(matchId, new PlayRequest(player2Id, "FOGO"));
        // Atualiza o mock (estado pós-Rodada 1)
        game.getScores().put(player1Id, 1);
        game.clearCurrentPlays();
        when(raftClientService.getAllEntries()).thenReturn(List.of(createGameRaftEntry(gameRaftKey, game)));

        // --- Rodada 2: P1 Vence (2-0) -> FIM DE JOGO ---
        gameService.receivePlay(matchId, new PlayRequest(player1Id, "NATUREZA"));
        // Atualiza o mock
        game.getCurrentPlays().put(player1Id, "NATUREZA");
        when(raftClientService.getAllEntries()).thenReturn(List.of(createGameRaftEntry(gameRaftKey, game)));

        gameService.receivePlay(matchId, new PlayRequest(player2Id, "AGUA"));

        // Verificação (Notificações)
        // R1: 2x ACTION, 1x RESULT
        // R2: 2x ACTION, 1x RESULT, 1x GAME_OVER
        // Total: 7 publicações
        verify(eventPublisher, times(7)).publishMessage(topicCaptor.capture(), messageCaptor.capture());

        String lastMessageJson = messageCaptor.getValue();
        GameEvent.GameOver gameOver = gson.fromJson(lastMessageJson, GameEvent.GameOver.class);

        assertEquals("GAME_OVER", gameOver.type());
        assertEquals(player1Id, gameOver.winnerId());
        assertEquals(2, gameOver.scores().get(player1Id));

        // Verificação (Raft)
        // Deve ter deletado o jogo do Raft no final
        verify(raftClientService).deleteEntry(eq(gameRaftKey));
    }
}