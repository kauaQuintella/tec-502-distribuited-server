package com.cardgame_distribuited_servers.tec502.server.raft;

import com.google.gson.JsonElement;

public record RaftEntry(String type, JsonElement payload) {}

