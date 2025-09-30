package com.cardgame_distribuited_servers.tec502.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    @GetMapping("/hello")
    public String sayHello() {
        return "Olá do nosso primeiro servidor distribuído!";
    }
}
