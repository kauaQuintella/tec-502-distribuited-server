package com.cardgame_distribuited_servers.tec502.server.game.inventory;

// Este é o objeto que será salvo no Raft como uma "TRADE_OFFER"
public record TradeOffer(
        String offerId,
        String fromPlayerId, 
        String toPlayerId,   
        String fromSkinId, 
        String toSkinId       
) {}