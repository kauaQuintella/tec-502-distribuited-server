package com.cardgame_distribuited_servers.tec502.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ServerProperties {

    @Value("${server.id}")
    private String id;

    @Value("${server.port}")
    private int port;

    @Value("#{'${server.peer-urls}'.split(',')}")
    private List<String> peerUrls;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public List<String> getPeerUrls() { return peerUrls; }
    public void setPeerUrls(List<String> peerUrls) { this.peerUrls = peerUrls; }
}