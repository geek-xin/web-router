package com.geek.webrouter.web.model.dto;

import java.util.List;

/**
 * 单条路由的紧凑流量指标，供管理后台路由卡片使用。
 * 不包含日志明细，避免列表页拉取大量数据。
 */
public record RouteTrafficMetrics(
        String routeId,
        long totalRequests,
        long failedRequests,
        long slowRequests,
        long totalDurationMs,
        long requestsLastMinute,
        long failedLastMinute,
        long averageDurationMs,
        List<Long> trafficBuckets
) {
}
