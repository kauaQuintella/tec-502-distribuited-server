package com.cardgame_distribuited_servers.tec502.server.registry;

import org.springframework.web.bind.annotation.*;
import java.util.Collection;

@RestController
@RequestMapping("/api/registry")
public class RegistryController {

    private final RegistryService registryService;

    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/register")
    public void registerServer(@RequestBody ServerInfo serverInfo) {
        registryService.register(serverInfo);
    }

    @GetMapping("/servers")
    public Collection<ServerInfo> getRegisteredServers() {
        return registryService.getOnlineServers();
    }
}