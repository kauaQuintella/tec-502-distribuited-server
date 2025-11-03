package com.cardgame_distribuited_servers.tec502.server.game;

import com.cardgame_distribuited_servers.tec502.server.game.entity.Inventory;
import com.cardgame_distribuited_servers.tec502.server.game.entity.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    // Mapa em memória para guardar os utilizadores por ID (nickname)
    private final Map<String, User> activeUsers = new ConcurrentHashMap<>();

    /**
     * Regista um novo utilizador ou obtém o existente.
     * @paramplayerId O nickname do utilizador.
     * @return O objeto User.
     */
    public User loginOrRegisterUser(User user) {
        // computeIfAbsent garante que o utilizador é criado apenas se não existir
        // É thread-safe
        return activeUsers.computeIfAbsent(user.getIdUser(), id -> {
            System.out.println("Novo utilizador registado: " + id);
            return user; // Cria utilizador com inventário vazio
        });
    }

    /**
     * Obtém um utilizador pelo seu ID.
     * @param playerId O nickname.
     * @return Um Optional contendo o User, se existir.
     */
    public Optional<User> findUser(String playerId) {
        return Optional.ofNullable(activeUsers.get(playerId));
    }
}