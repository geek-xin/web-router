package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import com.geek.webrouter.web.model.dto.ProxyRequestLogSnapshot;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ProxyRequestLogService {

    private static final int MAX_RECENT_LOGS = 100;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final Map<String, AtomicLong> requestsByIp = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestsByPath = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> durationByPath = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalRequestsByRoute = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalDurationMsByRoute = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicLong>> requestsByRouteAndIp = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicLong>> requestsByRouteAndPath = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicLong>> durationByRouteAndPath = new ConcurrentHashMap<>();
    private final ArrayDeque<ProxyRequestLogEntry> recentLogs = new ArrayDeque<>();
    private final Sinks.Many<ProxyRequestLogEntry> sink = Sinks.many().multicast().directBestEffort();

    public void record(ProxyRequestLogEntry entry) {
        ProxyRequestLogEntry timestamped = entry.timestamp() == null
                ? entry.withTimestamp(Instant.now())
                : entry;
        String routeId = baseRouteId(timestamped.routeId());
        String clientIp = timestamped.clientIp() == null || timestamped.clientIp().isBlank()
                ? "-"
                : timestamped.clientIp();
        String path = timestamped.path() == null || timestamped.path().isBlank()
                ? "/"
                : timestamped.path();

        totalRequests.incrementAndGet();
        long durationMs = Math.max(0, timestamped.durationMs());
        totalDurationMs.addAndGet(durationMs);
        requestsByIp.computeIfAbsent(clientIp, ignored -> new AtomicLong()).incrementAndGet();
        requestsByPath.computeIfAbsent(path, ignored -> new AtomicLong()).incrementAndGet();
        durationByPath.computeIfAbsent(path, ignored -> new AtomicLong()).addAndGet(durationMs);
        totalRequestsByRoute.computeIfAbsent(routeId, ignored -> new AtomicLong()).incrementAndGet();
        totalDurationMsByRoute.computeIfAbsent(routeId, ignored -> new AtomicLong())
                .addAndGet(durationMs);
        requestsByRouteAndIp
                .computeIfAbsent(routeId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(clientIp, ignored -> new AtomicLong())
                .incrementAndGet();
        requestsByRouteAndPath
                .computeIfAbsent(routeId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(path, ignored -> new AtomicLong())
                .incrementAndGet();
        durationByRouteAndPath
                .computeIfAbsent(routeId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(path, ignored -> new AtomicLong())
                .addAndGet(durationMs);

        synchronized (recentLogs) {
            recentLogs.addFirst(timestamped);
            while (recentLogs.size() > MAX_RECENT_LOGS) {
                recentLogs.removeLast();
            }
        }

        sink.tryEmitNext(timestamped);
    }

    public ProxyRequestLogSnapshot snapshot() {
        Map<String, Long> ipStats = toSortedStats(requestsByIp);
        Map<String, Long> pathStats = toSortedStats(requestsByPath);
        Map<String, Long> pathDurationStats = toStats(durationByPath);
        ArrayList<ProxyRequestLogEntry> logs;
        synchronized (recentLogs) {
            logs = new ArrayList<>(recentLogs);
        }
        return new ProxyRequestLogSnapshot(
                totalRequests.get(),
                totalDurationMs.get(),
                ipStats.size(),
                ipStats,
                pathStats,
                pathDurationStats,
                logs
        );
    }

    public ProxyRequestLogSnapshot snapshot(String routeId) {
        Map<String, Long> ipStats = toSortedStats(requestsByRouteAndIp.getOrDefault(routeId, Map.of()));
        Map<String, Long> pathStats = toSortedStats(requestsByRouteAndPath.getOrDefault(routeId, Map.of()));
        Map<String, Long> pathDurationStats = toStats(durationByRouteAndPath.getOrDefault(routeId, Map.of()));
        ArrayList<ProxyRequestLogEntry> logs;
        synchronized (recentLogs) {
            logs = recentLogs.stream()
                    .filter(entry -> routeId.equals(baseRouteId(entry.routeId())))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        long routeTotal = totalRequestsByRoute.getOrDefault(routeId, new AtomicLong()).get();
        long routeDurationMs = totalDurationMsByRoute.getOrDefault(routeId, new AtomicLong()).get();
        return new ProxyRequestLogSnapshot(
                routeTotal,
                routeDurationMs,
                ipStats.size(),
                ipStats,
                pathStats,
                pathDurationStats,
                logs
        );
    }

    public Flux<ProxyRequestLogEntry> stream() {
        return sink.asFlux();
    }

    public Flux<ProxyRequestLogEntry> stream(String routeId) {
        return sink.asFlux()
                .filter(entry -> routeId.equals(baseRouteId(entry.routeId())));
    }

    private String baseRouteId(String routeId) {
        if (routeId == null) {
            return "";
        }
        int derivedSeparator = routeId.lastIndexOf("__");
        if (derivedSeparator < 0) {
            return routeId;
        }
        String suffix = routeId.substring(derivedSeparator + 2);
        return suffix.matches("\\d+") ? routeId.substring(0, derivedSeparator) : routeId;
    }

    private Map<String, Long> toSortedStats(Map<String, AtomicLong> stats) {
        return stats.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(AtomicLong::get).reversed()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Long> toStats(Map<String, AtomicLong> stats) {
        return stats.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }
}
