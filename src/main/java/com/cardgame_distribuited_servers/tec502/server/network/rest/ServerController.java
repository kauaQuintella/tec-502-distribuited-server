package com.cardgame_distribuited_servers.tec502.server.network.rest;

import com.cardgame_distribuited_servers.tec502.server.game.UserService;
import com.cardgame_distribuited_servers.tec502.server.game.inventory.SkinsManager;
import com.cardgame_distribuited_servers.tec502.server.game.core.GameService;
import com.cardgame_distribuited_servers.tec502.server.game.entity.PlayRequest;
import com.cardgame_distribuited_servers.tec502.server.game.entity.Skin;
import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import com.cardgame_distribuited_servers.tec502.server.game.matchmaking.MatchmakingService;
import com.cardgame_distribuited_servers.tec502.server.raft.RaftClientService;
import com.google.gson.Gson;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.cardgame_distribuited_servers.tec502.server.network.amqp.EventPublisherService;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ServerController {

    private final EventPublisherService eventPublisherService;
    private final RaftClientService raftClientService;
    private final SkinsManager skinsManager; // Adicione o novo manager
    private final MatchmakingService matchmakingService;
    private final GameService gameService;
    private final UserService userService;
    private Gson gson;

    public ServerController(EventPublisherService eventPublisherService,
                            RaftClientService raftClientService,
                            SkinsManager skinsManager,
                            MatchmakingService matchmakingService,
                            GameService gameService,
                            UserService userService
    ) {
        this.eventPublisherService = eventPublisherService;
        this.raftClientService = raftClientService;
        this.skinsManager = skinsManager;
        this.matchmakingService = matchmakingService;
        this.gameService = gameService;
        this.gson = new Gson();
        this.userService = new UserService();
    }

    @PostMapping("/servers/test-publish/{userId}")
    public String testPublish(@PathVariable String userId) {
        String testMatchId = "match-" + System.currentTimeMillis();
        eventPublisherService.publishMatchFoundEvent(userId, testMatchId);
        return "Mensagem de teste publicada para o usuário: " + userId;
    }

    @PostMapping("/servers/test-raft")
    public String testRaft(@RequestBody String idUser) {
        long key = System.currentTimeMillis();
        String value = "teste-" + key;
        skinsManager.abrirPacote(idUser);
        return "Operação de teste submetida ao Raft com a chave: " + key;
    }

    @PostMapping("/servers/test-open-pack") // Renomeado para clareza
    public String testOpenPack(@RequestBody String idUser) {
        System.out.println("Recebido pedido de teste para abrir pacote..."); // Log para saber que o endpoint foi chamado
        Optional<Skin> acquiredSkin = skinsManager.abrirPacote(idUser);

        return acquiredSkin.map(skin -> gson.toJson(skin)).orElse("Erro ao abrir pacote");
    }

    @PostMapping("/register-user")
    public String register(@RequestBody String gsonUser) {

        User user = gson.fromJson(gsonUser, User.class);
        User userConfirm = userService.loginOrRegisterUser(user);
        return "Usuário Registrado com sucesso! Bem vindo " + user.getNickname();
    }

    @PostMapping("/matchmaking/join")
    public ResponseEntity<String> joinMatchmakingQueue(@RequestBody String idUser) { // Recebe o objeto JoinRequest

        if (idUser == null || idUser.isEmpty()) {
            return ResponseEntity.badRequest().body("playerId é obrigatório.");
        }
        System.out.println("Recebido pedido joinQueue para: " + idUser); // Log Adicionado
        matchmakingService.joinQueue(idUser);
        return ResponseEntity.ok("Entrou na fila de matchmaking.");
    }

    @PostMapping("/game/{matchId}/play")
    public ResponseEntity<String> receivePlay(
            @PathVariable String matchId,
            @RequestBody PlayRequest playRequest) {

        try {
            gameService.receivePlay(matchId, playRequest);
            return ResponseEntity.ok("Jogada recebida.");
        } catch (Exception e) {
            System.err.println("Erro ao processar jogada: " + e.getMessage());
            return ResponseEntity.status(500).body("Erro ao processar jogada.");
        }
    }
}
