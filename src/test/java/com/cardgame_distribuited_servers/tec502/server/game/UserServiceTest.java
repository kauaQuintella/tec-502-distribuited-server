package com.cardgame_distribuited_servers.tec502.server.game;

import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private RaftClientService raftClientService;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<String> raftValueCaptor;

    private final Gson gson = new Gson();

    private Entry createUserRaftEntry(long key, User user) {
        JsonElement payload = gson.toJsonTree(user);
        RaftEntry raftEntry = new RaftEntry("USER", payload);
        return new Entry(key, gson.toJson(raftEntry));
    }

    @Test
    void deveRegistrarNovoUsuarioQuandoNaoExistir() {
        User newUser = new User("player-new");

        when(raftClientService.getAllEntries()).thenReturn(List.of());
        when(raftClientService.submitOperation(anyLong(), any())).thenReturn(true);

        User result = userService.loginOrRegisterUser(newUser);

        assertEquals(newUser.getIdUser(), result.getIdUser());

        verify(raftClientService).submitOperation(anyLong(), raftValueCaptor.capture());
        RaftEntry savedEntry = gson.fromJson(raftValueCaptor.getValue(), RaftEntry.class);
        User savedUser = gson.fromJson(savedEntry.payload(), User.class);

        assertEquals("USER", savedEntry.type());
        assertEquals(newUser.getIdUser(), savedUser.getIdUser());
    }

    @Test
    void deveRetornarUsuarioExistenteSeJaEstiverNoRaft() {
        User userToLogin = new User("player-existing");

        Entry userEntry = createUserRaftEntry(1L, userToLogin);
        when(raftClientService.getAllEntries()).thenReturn(List.of(userEntry));

        User result = userService.loginOrRegisterUser(userToLogin);

        assertEquals(userToLogin.getIdUser(), result.getIdUser());

        verify(raftClientService, never()).submitOperation(anyLong(), any());
    }

    @Test
    void deveEncontrarUsuarioPeloId() {
        User user1 = new User("player1");
        User user2 = new User("player2");
        Entry entry1 = createUserRaftEntry(1L, user1);
        Entry entry2 = createUserRaftEntry(2L, user2);

        when(raftClientService.getAllEntries()).thenReturn(List.of(entry1, entry2));

        Optional<User> result = userService.findUser(user2.getIdUser());

        assertTrue(result.isPresent());
        assertEquals(user2.getIdUser(), result.get().getIdUser());
    }

    @Test
    void naoDeveEncontrarUsuarioInexistente() {
        User user1 = new User("player1");
        Entry entry1 = createUserRaftEntry(1L, user1);

        when(raftClientService.getAllEntries()).thenReturn(List.of(entry1));
        
        Optional<User> result = userService.findUser("id-desconhecido");

        assertTrue(result.isEmpty());
    }
}