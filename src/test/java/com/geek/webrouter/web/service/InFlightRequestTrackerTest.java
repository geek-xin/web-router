package com.geek.webrouter.web.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在途请求计数器测试 — 自动更新据此判断能否安全重启。
 */
class InFlightRequestTrackerTest {

    @Test
    void countsInFlightRequestsAndReturnsToZero() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();

        assertThat(tracker.inFlightCount()).isZero();
        assertThat(tracker.begin()).isEqualTo(1);
        assertThat(tracker.begin()).isEqualTo(2);
        assertThat(tracker.inFlightCount()).isEqualTo(2);
        assertThat(tracker.end()).isEqualTo(1);
        assertThat(tracker.end()).isZero();
        assertThat(tracker.inFlightCount()).isZero();
    }

    @Test
    void neverDropsBelowZeroEvenWhenEndIsCalledTooManyTimes() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();

        // doFinally 可能重复触发，计数器不得被带成负数，否则空闲判定永远不成立
        tracker.begin();
        tracker.end();
        tracker.end();
        tracker.end();

        assertThat(tracker.inFlightCount()).isZero();
        assertThat(tracker.isIdle(0)).isTrue();
    }

    @Test
    void reportsNotIdleWhileRequestsAreStillRunning() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        tracker.begin();

        assertThat(tracker.isIdle(0)).isFalse();
        assertThat(tracker.isIdle(1000)).isFalse();
    }

    @Test
    void requiresAQuietWindowAfterTheLastRequest() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        tracker.begin();
        tracker.end();

        // 刚结束的瞬间在途为 0，但静默时长还不够
        assertThat(tracker.isIdle(60_000)).isFalse();
        assertThat(tracker.isIdle(0)).isTrue();
        assertThat(tracker.idleMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countsConcurrentRequestsIndependently() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();

        for (int i = 0; i < 50; i++) {
            tracker.begin();
        }
        assertThat(tracker.inFlightCount()).isEqualTo(50);

        for (int i = 0; i < 49; i++) {
            tracker.end();
        }
        assertThat(tracker.inFlightCount()).isEqualTo(1);
        assertThat(tracker.isIdle(0)).isFalse();

        tracker.end();
        assertThat(tracker.isIdle(0)).isTrue();
    }
}
