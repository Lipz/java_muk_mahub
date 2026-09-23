package com.dev.monitor.dto.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerLogPayload(
    String serverId,
    String systemId,
    String systemName,
    String serverName,
    String serverIp,
    String path,
    String channel,
    String timestamp,
    String message
) {}
