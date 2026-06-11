package com.geek.webrouter.web.controller;

import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ProxyRequestLogControllerTest {

    @Test
    void routeSnapshotOnlyReturnsLogsForRequestedRoute() {
        ProxyRequestLogService service = new ProxyRequestLogService();
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/route-a/one", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-b", "POST", "/route-b/two", "10.0.0.2", 201, 18));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/route-a/three", "127.0.0.1", 404, 7,
                "", "", "", "127.0.0.1:9191"));

        WebTestClient client = WebTestClient.bindToController(new ProxyRequestLogController(service)).build();

        client.get()
                .uri("/admin/api/proxy-logs/routes/route-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalRequests").isEqualTo(2)
                .jsonPath("$.data.totalDurationMs").isEqualTo(17)
                .jsonPath("$.data.uniqueIpCount").isEqualTo(1)
                .jsonPath("$.data.requestsByIp['127.0.0.1']").isEqualTo(2)
                .jsonPath("$.data.pathStats['/route-a/one']").isEqualTo(1)
                .jsonPath("$.data.pathStats['/route-a/three']").isEqualTo(1)
                .jsonPath("$.data.durationTopLogs.length()").isEqualTo(2)
                .jsonPath("$.data.durationTopLogs[0].path").isEqualTo("/route-a/one")
                .jsonPath("$.data.recentLogs.length()").isEqualTo(2)
                .jsonPath("$.data.recentLogs[0].routeId").isEqualTo("route-a")
                .jsonPath("$.data.recentLogs[0].path").isEqualTo("/route-a/three")
                .jsonPath("$.data.recentLogs[0].accessAddress").isEqualTo("127.0.0.1:9191");
    }
}
