package com.geek.webrouter.config;

import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.RouteConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

/**
 * 动态路由管理服务 — 运行时增删改路由，即时生效无需重启。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteService {

    private final RouteDefinitionWriter routeDefinitionWriter;
    private final RouteConfigService routeConfigService;
    private final ApplicationEventPublisher publisher;
    private final LocalPortProxyService localPortProxyService;

    private final List<String> currentRouteIds = new ArrayList<>();
    private final Object refreshMonitor = new Object();
    private final Semaphore refreshSemaphore = new Semaphore(1);

    /**
     * 启动时从本地文件加载全部路由。
     */
    @PostConstruct
    public void init() {
        refreshAll().subscribe(
                unused -> {
                },
                err -> log.error("启动时刷新路由失败", err)
        );
    }

    /**
     * 刷新全部路由：先清除已注册路由，再从文件重新加载。
     */
    public Mono<Void> refreshAll() {
        return Mono.fromRunnable(refreshSemaphore::acquireUninterruptibly)
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(this::refreshAllLocked))
                .doFinally(signalType -> refreshSemaphore.release());
    }

    private Mono<Void> refreshAllLocked() {
        List<String> idsToRemove = currentRouteIdsSnapshot();
        List<RouteConfig> enabledConfigs = routeConfigService.listAll().stream()
                .filter(RouteConfig::isEnabled)
                .toList();
        List<String> enabledRouteIds = enabledConfigs.stream()
                .flatMap(config -> routeIds(config).stream())
                .toList();

        return Flux.fromIterable(idsToRemove)
                .concatMap(routeId -> routeDefinitionWriter.delete(Mono.just(routeId))
                        .doOnSuccess(unused -> log.info("已移除路由: {}", routeId))
                        .onErrorResume(err -> {
                            log.warn("移除路由失败: {} — {}", routeId, err.getMessage());
                            return Mono.empty();
                        }))
                .thenMany(Flux.fromIterable(enabledConfigs)
                        .concatMap(this::saveRoutes))
                .then()
                .then(localPortProxyService.refreshAll(enabledConfigs))
                .doOnSuccess(unused -> {
                    replaceCurrentRouteIds(enabledRouteIds);
                    publisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("路由刷新完成，当前注册路由: {}", currentRouteIdsSnapshot());
                });
    }

    /**
     * 按配置中的每个路径前缀注册 Gateway 路由。
     */
    private Flux<Void> saveRoutes(RouteConfig config) {
        List<String> prefixes = config.effectivePathPrefixes();
        return Flux.range(0, prefixes.size())
                .concatMap(index -> saveRoute(config, prefixes.get(index), index));
    }

    private Mono<Void> saveRoute(RouteConfig config, String pathPrefix, int index) {
        String routeId = routeId(config.getId(), index);
        String targetUrl = config.getTargetUrl();

        RouteDefinition definition = new RouteDefinition();
        definition.setId(routeId);
        definition.setUri(URI.create(targetUrl));
        definition.setOrder(0);

        definition.setPredicates(List.of(
                new PredicateDefinition("Path=" + pathPattern(pathPrefix))
        ));

        int segments = stripPrefixSegments(pathPrefix);
        if (segments > 0) {
            definition.setFilters(List.of(
                    new FilterDefinition("StripPrefix=" + segments)
            ));
        }

        return routeDefinitionWriter.save(Mono.just(definition))
                .doOnSuccess(unused -> log.info("已注册路由: {} {} -> {}", routeId, pathPrefix, targetUrl));
    }

    private List<String> routeIds(RouteConfig config) {
        return IntStream.range(0, config.effectivePathPrefixes().size())
                .mapToObj(index -> routeId(config.getId(), index))
                .toList();
    }

    private String routeId(String baseRouteId, int index) {
        return index == 0 ? baseRouteId : baseRouteId + "__" + index;
    }

    private String pathPattern(String pathPrefix) {
        return "/".equals(pathPrefix) ? "/**" : pathPrefix + "/**";
    }

    private int stripPrefixSegments(String pathPrefix) {
        if ("/".equals(pathPrefix)) {
            return 0;
        }
        return pathPrefix.split("/").length - 1;
    }

    private List<String> currentRouteIdsSnapshot() {
        synchronized (refreshMonitor) {
            return new ArrayList<>(currentRouteIds);
        }
    }

    private void replaceCurrentRouteIds(List<String> routeIds) {
        synchronized (refreshMonitor) {
            currentRouteIds.clear();
            currentRouteIds.addAll(routeIds);
        }
    }
}
