package com.cardgame_distribuited_servers.tec502.server.network.rest;

// O que o Player B envia para aceitar
public record AcceptTradeRequest(
        String offerId      
) {}