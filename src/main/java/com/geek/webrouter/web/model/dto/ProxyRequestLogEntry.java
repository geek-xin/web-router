package com.geek.webrouter.web.model.dto;

import java.time.Instant;

public record ProxyRequestLogEntry(
        Instant timestamp,
        String routeId,
        String method,
        String path,
        String clientIp,
        int status,
        long durationMs
) {
    public ProxyRequestLogEntry withTimestamp(Instant value) {
        return new ProxyRequestLogEntry(value, routeId, method, path, clientIp, status, durationMs);
    }
}
