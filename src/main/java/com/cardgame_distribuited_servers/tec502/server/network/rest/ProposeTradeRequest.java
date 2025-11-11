package com.cardgame_distribuited_servers.tec502.server.network.rest;

// O que o Player A envia para propor
public record ProposeTradeRequest(
        String toPlayerId, 
        String mySkinId,     
        String theirSkinId    
) {}