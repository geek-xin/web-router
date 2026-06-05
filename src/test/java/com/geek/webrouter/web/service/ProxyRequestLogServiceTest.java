package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRequestLogServiceTest {

    @Test
    void recordsTotalsAndRequestsByIp() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "test", "GET", "/test/a", "127.0.0.1", 200, 12));
        service.record(new ProxyRequestLogEntry(
                null, "test", "POST", "/test/b", "192.168.1.10", 201, 20));
        service.record(new ProxyRequestLogEntry(
                null, "test", "GET", "/test/c", "127.0.0.1", 404, 8));

        var snapshot = service.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(3);
        assertThat(snapshot.totalDurationMs()).isEqualTo(40);
        assertThat(snapshot.uniqueIpCount()).isEqualTo(2);
        assertThat(snapshot.requestsByIp())
                .containsEntry("127.0.0.1", 2L)
                .containsEntry("192.168.1.10", 1L);
        assertThat(snapshot.pathStats())
                .containsEntry("/test/a", 1L)
                .containsEntry("/test/b", 1L)
                .containsEntry("/test/c", 1L);
        assertThat(snapshot.pathDurationStats())
                .containsEntry("/test/a", 12L)
                .containsEntry("/test/b", 20L)
                .containsEntry("/test/c", 8L);
        assertThat(snapshot.recentLogs()).hasSize(3);
        assertThat(snapshot.recentLogs().getFirst().path()).isEqualTo("/test/c");
    }

    @Test
    void routeSnapshotOnlyIncludesLogsForRequestedRoute() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/route-a/one", "127.0.0.1", 200, 12));
        service.record(new ProxyRequestLogEntry(
                null, "route-b", "POST", "/route-b/two", "10.0.0.2", 201, 20));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/route-a/three", "127.0.0.1", 404, 8));

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.totalRequests()).isEqualTo(2);
        assertThat(snapshot.totalDurationMs()).isEqualTo(20);
        assertThat(snapshot.uniqueIpCount()).isEqualTo(1);
        assertThat(snapshot.requestsByIp()).containsOnlyKeys("127.0.0.1");
        assertThat(snapshot.pathStats())
                .containsEntry("/route-a/one", 1L)
                .containsEntry("/route-a/three", 1L)
                .doesNotContainKey("/route-b/two");
        assertThat(snapshot.pathDurationStats())
                .containsEntry("/route-a/one", 12L)
                .containsEntry("/route-a/three", 8L)
                .doesNotContainKey("/route-b/two");
        assertThat(snapshot.recentLogs())
                .extracting(ProxyRequestLogEntry::routeId)
                .containsExactly("route-a", "route-a");
        assertThat(snapshot.recentLogs().getFirst().path()).isEqualTo("/route-a/three");
    }
    @Test
    void routeSnapshotAggregatesDerivedRouteIdsForMultiplePrefixes() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/api/one", "127.0.0.1", 200, 12));
        service.record(new ProxyRequestLogEntry(
                null, "route-a__1", "GET", "/admin/two", "127.0.0.1", 200, 9));
        service.record(new ProxyRequestLogEntry(
                null, "route-b", "GET", "/other", "10.0.0.2", 200, 7));

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.totalRequests()).isEqualTo(2);
        assertThat(snapshot.totalDurationMs()).isEqualTo(21);
        assertThat(snapshot.uniqueIpCount()).isEqualTo(1);
        assertThat(snapshot.requestsByIp()).containsEntry("127.0.0.1", 2L);
        assertThat(snapshot.pathStats())
                .containsEntry("/api/one", 1L)
                .containsEntry("/admin/two", 1L)
                .doesNotContainKey("/other");
        assertThat(snapshot.pathDurationStats())
                .containsEntry("/api/one", 12L)
                .containsEntry("/admin/two", 9L)
                .doesNotContainKey("/other");
        assertThat(snapshot.recentLogs())
                .extracting(ProxyRequestLogEntry::routeId)
                .containsExactly("route-a__1", "route-a");
    }

}
