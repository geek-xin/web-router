package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
                null, "test", "GET", "/test/c", "127.0.0.1", 404, 8,
                "", "", "", "127.0.0.1:9191"));

        var snapshot = service.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(3);
        assertThat(snapshot.failedRequests()).isEqualTo(1);
        assertThat(snapshot.slowRequests()).isZero();
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
        assertThat(snapshot.pathMaxDurationStats())
                .containsEntry("/test/a", 12L)
                .containsEntry("/test/b", 20L)
                .containsEntry("/test/c", 8L);
        assertThat(snapshot.durationTopLogs())
                .extracting(ProxyRequestLogEntry::path)
                .containsExactly("/test/b", "/test/a", "/test/c");
        assertThat(snapshot.recentLogs()).hasSize(3);
        assertThat(snapshot.recentLogs().getFirst().path()).isEqualTo("/test/c");
        assertThat(snapshot.recentLogs().getFirst().accessAddress()).isEqualTo("127.0.0.1:9191");
    }

    @Test
    void recordsMaximumDurationByPath() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/same", "127.0.0.1", 200, 12));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/same", "127.0.0.1", 200, 42));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/same", "127.0.0.1", 200, 8));

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.pathStats()).containsEntry("/same", 3L);
        assertThat(snapshot.pathDurationStats()).containsEntry("/same", 62L);
        assertThat(snapshot.pathMaxDurationStats()).containsEntry("/same", 42L);
    }

    @Test
    void countsSlowRequestsAboveThreshold() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/fast", "127.0.0.1", 200, 999));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/slow", "127.0.0.1", 200, 1000));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/slower", "127.0.0.1", 200, 2500));

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.failedRequests()).isZero();
        assertThat(snapshot.slowRequests()).isEqualTo(2);
        assertThat(snapshot.totalRequests()).isEqualTo(3);
    }

    @Test
    void failedAndSlowCountsAreFullScopeBeyondRecentLogWindow() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/old-failure", "127.0.0.1", 500, 2000));
        for (int index = 0; index < 100; index += 1) {
            service.record(new ProxyRequestLogEntry(
                    null, "route-a", "GET", "/recent-" + index, "127.0.0.1", 200, 100 + index));
        }

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.recentLogs())
                .extracting(ProxyRequestLogEntry::path)
                .doesNotContain("/old-failure");
        assertThat(snapshot.failedRequests()).isEqualTo(1);
        assertThat(snapshot.slowRequests()).isEqualTo(1);
        assertThat(snapshot.totalRequests()).isEqualTo(101);
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
        assertThat(snapshot.pathMaxDurationStats())
                .containsEntry("/route-a/one", 12L)
                .containsEntry("/route-a/three", 8L)
                .doesNotContainKey("/route-b/two");
        assertThat(snapshot.durationTopLogs())
                .extracting(ProxyRequestLogEntry::path)
                .containsExactly("/route-a/one", "/route-a/three");
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
        assertThat(snapshot.pathMaxDurationStats())
                .containsEntry("/api/one", 12L)
                .containsEntry("/admin/two", 9L)
                .doesNotContainKey("/other");
        assertThat(snapshot.durationTopLogs())
                .extracting(ProxyRequestLogEntry::routeId)
                .containsExactly("route-a", "route-a__1");
        assertThat(snapshot.recentLogs())
                .extracting(ProxyRequestLogEntry::routeId)
                .containsExactly("route-a__1", "route-a");
    }

    @Test
    void durationTopLogsKeepSlowEntriesBeyondRecentLogLimit() {
        ProxyRequestLogService service = new ProxyRequestLogService();

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/old-slowest", "127.0.0.1", 200, 1_000));
        for (int index = 0; index < 100; index += 1) {
            service.record(new ProxyRequestLogEntry(
                    null, "route-a", "GET", "/recent-" + index, "127.0.0.1", 200, index));
        }

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.recentLogs())
                .extracting(ProxyRequestLogEntry::path)
                .doesNotContain("/old-slowest");
        assertThat(snapshot.durationTopLogs()).hasSize(100);
        assertThat(snapshot.durationTopLogs().getFirst().path()).isEqualTo("/old-slowest");
    }

    // ===== 新增：滑动窗口 / 时间序列指标 =====

    @Test
    void requestsLastMinuteDropsRequestsOlderThanSixtySeconds() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        ProxyRequestLogService service = new ProxyRequestLogService(now::get);

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/a", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/b", "127.0.0.1", 200, 20));

        assertThat(service.snapshot().requestsLastMinute()).isEqualTo(2);
        assertThat(service.snapshot("route-a").requestsLastMinute()).isEqualTo(2);

        // 推进 59 秒仍在窗口内
        now.addAndGet(59_000L);
        assertThat(service.snapshot().requestsLastMinute()).isEqualTo(2);

        // 推进到第 61 秒，旧请求滚出窗口
        now.addAndGet(2_000L);
        assertThat(service.snapshot().requestsLastMinute()).isZero();
        assertThat(service.snapshot("route-a").requestsLastMinute()).isZero();
        // 全量口径不受影响
        assertThat(service.snapshot().totalRequests()).isEqualTo(2);
    }

    @Test
    void trafficBucketsHaveThirtyPointsOldestToNewest() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        ProxyRequestLogService service = new ProxyRequestLogService(now::get);

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/first", "127.0.0.1", 200, 10));

        now.addAndGet(120_000L);
        for (int index = 0; index < 3; index += 1) {
            service.record(new ProxyRequestLogEntry(
                    null, "route-a", "GET", "/later-" + index, "127.0.0.1", 200, 5));
        }

        List<Long> buckets = service.snapshot().trafficBuckets();

        assertThat(buckets).hasSize(30);
        // 旧到新：2 分钟前的桶是 1，上一分钟为空，最新桶是刚记录的 3
        assertThat(buckets.get(27)).isEqualTo(1L);
        assertThat(buckets.get(28)).isZero();
        assertThat(buckets.get(29)).isEqualTo(3L);
        // 路由维度同样为 30 个点
        assertThat(service.snapshot("route-a").trafficBuckets()).hasSize(30);
        assertThat(service.snapshot("route-a").trafficBuckets().get(29)).isEqualTo(3L);
    }

    @Test
    void routeAndGlobalBucketsAreIndependent() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        ProxyRequestLogService service = new ProxyRequestLogService(now::get);

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/a/one", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/a/two", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-b", "GET", "/b/one", "127.0.0.1", 200, 10));

        assertThat(service.snapshot("route-a").requestsLastMinute()).isEqualTo(2);
        assertThat(service.snapshot("route-b").requestsLastMinute()).isEqualTo(1);
        assertThat(service.snapshot().requestsLastMinute()).isEqualTo(3);

        // route-a 的最新桶不含 route-b 的请求
        assertThat(service.snapshot("route-a").trafficBuckets().get(29)).isEqualTo(2L);
        assertThat(service.snapshot("route-b").trafficBuckets().get(29)).isEqualTo(1L);
        assertThat(service.snapshot().trafficBuckets().get(29)).isEqualTo(3L);
    }

    @Test
    void derivedRouteIdCountsIntoBaseRouteBuckets() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        ProxyRequestLogService service = new ProxyRequestLogService(now::get);

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/api/one", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-a__1", "GET", "/admin/two", "127.0.0.1", 200, 20));
        service.record(new ProxyRequestLogEntry(
                null, "route-b", "GET", "/other", "127.0.0.1", 200, 30));

        var snapshot = service.snapshot("route-a");

        assertThat(snapshot.requestsLastMinute()).isEqualTo(2);
        assertThat(snapshot.trafficBuckets().get(29)).isEqualTo(2L);
        assertThat(service.snapshot().requestsLastMinute()).isEqualTo(3);
    }

    @Test
    void averageDurationUsesIntegerDivisionAndUnknownRouteIsEmpty() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        ProxyRequestLogService service = new ProxyRequestLogService(now::get);

        assertThat(service.snapshot().averageDurationMs()).isZero();
        var missing = service.snapshot("route-missing");
        assertThat(missing.requestsLastMinute()).isZero();
        assertThat(missing.averageDurationMs()).isZero();
        assertThat(missing.trafficBuckets()).hasSize(30).containsOnly(0L);

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/a", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/b", "127.0.0.1", 200, 11));
        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/c", "127.0.0.1", 200, 12));

        // 33 / 3 = 11
        assertThat(service.snapshot().averageDurationMs()).isEqualTo(11);
        assertThat(service.snapshot("route-a").averageDurationMs()).isEqualTo(11);
    }

    @Test
    void routeTrafficMetricsAggregatesPerRouteWithoutLogDetails() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        ProxyRequestLogService service = new ProxyRequestLogService(now::get);

        service.record(new ProxyRequestLogEntry(
                null, "route-a", "GET", "/a/one", "127.0.0.1", 200, 10));
        service.record(new ProxyRequestLogEntry(
                null, "route-a__1", "GET", "/a/two", "127.0.0.1", 500, 20));
        service.record(new ProxyRequestLogEntry(
                null, "route-b", "POST", "/b/one", "10.0.0.2", 201, 30));

        var metrics = service.routeTrafficMetrics();

        assertThat(metrics).containsOnlyKeys("route-a", "route-b");
        var routeA = metrics.get("route-a");
        assertThat(routeA.routeId()).isEqualTo("route-a");
        assertThat(routeA.totalRequests()).isEqualTo(2);
        assertThat(routeA.failedRequests()).isEqualTo(1);
        assertThat(routeA.requestsLastMinute()).isEqualTo(2);
        assertThat(routeA.averageDurationMs()).isEqualTo(15);
        assertThat(routeA.trafficBuckets()).hasSize(30);
        assertThat(routeA.trafficBuckets().get(29)).isEqualTo(2L);

        var routeB = metrics.get("route-b");
        assertThat(routeB.totalRequests()).isEqualTo(1);
        assertThat(routeB.requestsLastMinute()).isEqualTo(1);
        assertThat(routeB.trafficBuckets()).hasSize(30);
        assertThat(routeB.trafficBuckets().get(29)).isEqualTo(1L);

        // 最近一分钟失败数只统计窗口内的失败请求
        assertThat(routeA.failedLastMinute()).isEqualTo(1);
        assertThat(routeB.failedLastMinute()).isZero();
        assertThat(service.snapshot().failedLastMinute()).isEqualTo(1);
        assertThat(service.snapshot("route-a").failedLastMinute()).isEqualTo(1);

        now.addAndGet(61_000L);
        assertThat(service.routeTrafficMetrics().get("route-a").failedLastMinute()).isZero();
        // 累计失败数不受滚动窗口影响
        assertThat(service.routeTrafficMetrics().get("route-a").failedRequests()).isEqualTo(1);
    }
}

