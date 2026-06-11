package com.geek.webrouter.web.model.dto;

import java.util.List;
import java.util.Map;

public record ProxyRequestLogSnapshot(
        long totalRequests,
        long totalDurationMs,
        int uniqueIpCount,
        Map<String, Long> requestsByIp,
        Map<String, Long> pathStats,
        Map<String, Long> pathDurationStats,
        Map<String, Long> pathMaxDurationStats,
        List<ProxyRequestLogEntry> durationTopLogs,
        List<ProxyRequestLogEntry> recentLogs
) {
}
