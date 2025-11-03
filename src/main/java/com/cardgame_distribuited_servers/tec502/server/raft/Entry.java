
package com.cardgame_distribuited_servers.tec502.server.raft;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.NotNull;

@RequiredArgsConstructor
@Getter
public class Entry {

    @NotNull
    private final Long key;
    private final String val;

}