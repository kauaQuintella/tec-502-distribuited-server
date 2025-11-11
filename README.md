# tec-502-distribuited-server

## 1. Descrição

Este projeto é uma solução para um jogo de "Pedra, Papel e Tesoura" (FOGO, ÁGUA, NATUREZA) implementado em uma arquitetura de microsserviços distribuídos usando Spring Boot.

O objetivo principal foi migrar um protótipo de servidor centralizado para um sistema distribuído, tolerante a falhas e escalável, capaz de gerenciar o estado do jogo, inventário de jogadores e matchmaking sem um ponto único de falha.

Para alcançar isso, o sistema utiliza:

- **Múltiplos Servidores de Jogo**: Várias instâncias do aplicativo Spring Boot (app-1, app-2, app-3) que podem gerenciar qualquer jogador.
- **Cluster de Consenso Raft**: Um cluster (raft-server-1, raft-server-2, raft-server-3) que atua como a fonte única da verdade (Single Source of Truth) para todos os dados críticos, como skins, usuários, partidas em andamento e ofertas de troca.
- **Broker de Mensagens (RabbitMQ)**: Um serviço (rabbitmq-1) que gerencia a comunicação assíncrona do servidor para o cliente (modelo Publisher-Subscriber), notificando os jogadores sobre eventos como "partida encontrada", "jogada do oponente" ou "proposta de troca recebida".

## 2. Estrutura de Diretórios (Schema)

A estrutura do projeto, focada no código-fonte, é organizada da seguinte forma:

```
PBLREDES2/
├── .mvn/                     (Scripts do Maven)
├── src/
│   ├── main/
│   │   ├── java/com/cardgame_distribuited_servers/tec502/
│   │   │   ├── client/         (Aplicação Cliente CLI)
│   │   │   │   ├── game/entity/  (Entidades (Cliente))
│   │   │   │   ├── ClientApplication.java (Ponto de entrada (Cliente))
│   │   │   │   ├── ClientState.java       (Máquina de estados (Cliente))
│   │   │   │   └── RabbitMQService.java   (Listener RabbitMQ (Cliente))
│   │   │   │
│   │   │   └── server/         (Aplicação Servidor Spring)
│   │   │       ├── config/         (Configuração Spring)
│   │   │       ├── game/
│   │   │       │   ├── core/         (Lógica central do jogo)
│   │   │       │   ├── entity/       (Entidades (Servidor))
│   │   │       │   ├── inventory/    (Lógica de inventário e trocas)
│   │   │       │   └── matchmaking/  (Lógica de matchmaking)
│   │   │       │
│   │   │       ├── network/
│   │   │       │   ├── amqp/         (Publisher RabbitMQ)
│   │   │       │   └── rest/         (Controladores REST API)
│   │   │       │
│   │   │       ├── raft/           (Interação com o cluster Raft)
│   │   │       │   ├── Entry.java            (Modelo de dados do Raft)
│   │   │       │   ├── RaftClientService.java (Cliente Raft)
│   │   │       │   ├── RaftEntry.java         (Wrapper de dados Raft)
│   │   │       │   └── RaftInitializer.java   (Inicializador de skins no Raft)
│   │   │       │
│   │   │       └── registry/       (Service Discovery e Health Check)
│   │   │           ├── HealthCheckScheduler.java (Scheduler de Health Check)
│   │   │           ├── RegistrationManager.java  (Registrador de peers)
│   │   │           ├── RegistryService.java      (Serviço de registro)
│   │   │           └── ServerInfo.java         (Modelo de dados do servidor)
│   │   │
│   │   └── resources/
│   │       └── application.properties (Configurações)
│   │
│   └── test/
│       └── java/com/cardgame_distribuited_servers/tec502/ (Testes unitários)
│
├── compose.yaml          (Serviços Docker)
├── Dockerfile            (Build da imagem Docker)
├── pom.xml               (Dependências do Maven)
└── README.MD             (Este arquivo)
```

## 3. Arquitetura e Fluxo de Dados

A solução implementa os requisitos de distribuição usando o cluster Raft como um banco de dados distribuído e atômico.

### 3.1. O Padrão "RaftEntry"

Para armazenar diferentes tipos de dados (Skins, Usuários, Jogos) no Raft, que é um simples armazenamento de chave-valor (Long -> String), foi criado um wrapper: `RaftEntry.java`.

Todo dado salvo no Raft segue este formato JSON:

```json
{
  "type": "TIPO_DO_DADO", // Ex: "SKIN", "USER", "GAME_STATE", "MATCHMAKING_ENTRY"
  "payload": { ... }       // O objeto JSON real (uma Skin, um User, etc.)
}
```

Isso permite que os serviços filtrem as entradas do Raft por tipo.

### 3.2. Gerenciamento de Estado Distribuído (Raft)

O Raft é a chave para a tolerância a falhas. Todo estado que precisa ser consistente é escrito nele.

#### a. Aquisição de Pacotes (Skins)

A aquisição de "pacotes" (skins) deve ser justa e evitar duplicações.

1.  **Inicialização**: `RaftInitializer.java` é executado na inicialização de um dos servidores. Ele preenche o cluster Raft com centenas de entradas `RaftEntry(type="SKIN", ...)`. Skins "disponíveis" têm `ownerId: null`.

2.  **Abertura de Pacote**: Quando um usuário chama `POST /api/servers/test-open-pack` (`SkinsManager.abrirPacote`):
    - O serviço escaneia todo o cluster Raft (`raftClientService.getAllEntries()`).
    - Ele filtra por `type="SKIN"` e `ownerId == null`.
    - Ele escolhe uma skin aleatória dessa lista.

3.  **Ação Atômica**: O serviço tenta atualizar a entrada original da skin no Raft, definindo o `ownerId` para o `playerId` do jogador.

4.  **Garantia de Consistência**: Como o Raft exige consenso para escritas, se dois servidores tentarem pegar a mesma skin (mesma chave Raft) ao mesmo tempo, apenas uma operação de escrita (a primeira a chegar no líder Raft) terá sucesso. A outra falhará, e o `SkinsManager` tentará novamente com outra skin.

5.  **Notificação**: O jogador é notificado da nova skin via RabbitMQ (evento `INVENTORY_UPDATE`).

#### b. Matchmaking Distribuído

Jogadores em servidores diferentes devem poder jogar entre si.

1.  **Entrar na Fila**: Quando um usuário chama `POST /api/matchmaking/join` (`MatchmakingService.joinQueue`):
    - O serviço não armazena o jogador em uma fila local. Em vez disso, ele escreve uma nova entrada no Raft: `RaftEntry(type="MATCHMAKING_ENTRY", payload={...playerId...})`.

2.  **Formação de Partida**:
    - `MatchmakingScheduler.java` é executado em todos os servidores a cada 5 segundos.
    - Cada servidor escaneia o Raft por entradas `MATCHMAKING_ENTRY`.
    - Se um servidor encontrar dois ou mais jogadores:
        1.  **Ação Atômica**: Ele tenta deletar as duas entradas de matchmaking do Raft (`raftClientService.deleteEntry()`).
        2.  **Garantia de Consistência**: A operação de `delete` no Raft é atômica. Apenas um servidor no cluster (o primeiro a executar) conseguirá deletar com sucesso ambos os jogadores. Os outros servidores falharão ao tentar deletar (pois a entrada não existirá mais) e simplesmente aguardarão o próximo ciclo.
        3.  O servidor que obteve sucesso nas deleções se torna o "coordenador da partida".
        4.  O coordenador então chama `GameService.createGame()`.

#### c. Estado do Jogo (Tolerância a Falhas)

O estado da partida (jogadas, placar) não pode ser perdido se um servidor falhar.

1.  **Criação do Jogo**: `GameService.createGame` cria um objeto `GameState` e o salva no Raft como `RaftEntry(type="GAME_STATE", ...)`.

2.  **Receber Jogada**: Quando um usuário chama `POST /api/game/{matchId}/play` (`GameService.receivePlay`):
    - O servidor (qualquer um) busca o `GAME_STATE` do Raft.
    - Ele adiciona a jogada do jogador (ex: "FOGO") ao `GameState`.
    - Ele salva o `GameState` atualizado de volta no Raft.
    - Se for a segunda jogada da rodada:
        - Ele calcula o vencedor, atualiza o placar no `GameState`.
        - Limpa as jogadas (para a próxima rodada).
        - Salva o `GameState` (com o novo placar) de volta no Raft.

3.  **Garantia de Tolerância a Falhas**: Como o estado é salvo no Raft a cada jogada, se o servidor que processou a jogada falhar, o estado da partida (placar e jogadas) está seguro.

#### d. Troca de Itens (Nova Funcionalidade)

A troca de skins usa o mesmo padrão de "bloqueio" atômico do matchmaking.

1.  **Propor Troca**: O Jogador A chama `POST /api/trade/propose` (`TradeService.proposeTrade`):
    - O serviço verifica se A e B realmente possuem as skins (lendo o Raft).
    - Ele cria uma `RaftEntry(type="TRADE_OFFER", ...)` no Raft.
    - Notifica o Jogador B via RabbitMQ (evento `TRADE_PROPOSED`).

2.  **Aceitar Troca**: O Jogador B chama `POST /api/trade/accept` (`TradeService.acceptTrade`):
    - **Ação Atômica**: O serviço tenta deletar a entrada `TRADE_OFFER` do Raft.
    - **Garantia de Consistência**: Se a deleção falhar (porque o Jogador A cancelou, ou a proposta expirou, ou o próprio Jogador B clicou duas vezes), a operação falha com um Conflito (409).
    - Se a deleção for bem-sucedida, o serviço tem o "lock" da troca.
    - Ele então atualiza as duas entradas `SKIN` no Raft, trocando os `ownerId`s.
    - Notifica ambos os jogadores via RabbitMQ (evento `TRADE_COMPLETE`).

### 3.3. Comunicação

O sistema utiliza dois canais de comunicação principais:

#### Comando (Cliente -> Servidor): API REST

O cliente (`ClientApplication`) usa requisições HTTP POST para enviar ações (comandos) ao servidor.
Ex: `POST /matchmaking/join`, `POST /game/{matchId}/play`, `POST /trade/propose`.

Qualquer instância do servidor `app` pode receber esses comandos, pois o estado está centralizado no Raft.

#### Evento (Servidor -> Cliente): RabbitMQ (Pub/Sub)

O servidor (`EventPublisherService`) nunca responde diretamente com o estado. Ele apenas publica eventos em um "tópico" no RabbitMQ.

O cliente (`RabbitMQService`) se inscreve em tópicos específicos:

- `user.<playerId>`: Para eventos pessoais (ex: `MATCH_FOUND`, `TRADE_PROPOSED`).
- `game.<matchId>`: Para eventos de jogo (ex: `PLAYER_ACTION`, `ROUND_RESULT`, `GAME_OVER`).

Isso desacopla o cliente do servidor e permite que as atualizações de estado sejam enviadas em tempo real para os jogadores.

### 3.4. Service Discovery e Health Checks (REST)

Embora o Raft atue como mediador para *dados de jogo*, os servidores de aplicação (`app-1`, `app-2`, `app-3`) utilizam uma API REST simples entre si para descoberta de serviço (Service Discovery) e verificação de atividade (Health Checks).

-   **Registro (Startup):** Na inicialização, cada servidor (`RegistrationManager.java`) envia um `POST /api/registry/register` para todos os outros *peers* listados em suas variáveis de ambiente.
-   **Health Check (Contínuo):** Periodicamente, cada servidor (`HealthCheckScheduler.java`) executa um *health check* (chamando `GET /actuator/health`) em todos os outros servidores que ele conhece.
-   **Remoção:** Se um servidor falhar no *health check* (ex: timeout ou conexão recusada), ele é removido da lista de servidores ativos (`RegistryService.java`).

Isso cumpre o requisito de comunicação inter-servidor via REST e garante que a lógica de matchmaking (embora mediada pelo Raft) não dependa de nós de aplicação que estejam offline.

## 4. Referências

1.  [https://github.com/pleshakoff/raft](https://github.com/pleshakoff/raft) (Biblioteca/Servidor Raft utilizado para consenso)
