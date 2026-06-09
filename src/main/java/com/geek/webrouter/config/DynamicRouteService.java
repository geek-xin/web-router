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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final Map<String, GatewayRouteSpec> currentRoutes = new LinkedHashMap<>();
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
     * 刷新全部路由：按配置快照差量删除/保存路由，避免万级路由下每次全量重建。
     */
    public Mono<Void> refreshAll() {
        return Mono.fromRunnable(refreshSemaphore::acquireUninterruptibly)
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(this::refreshAllLocked))
                .doFinally(signalType -> refreshSemaphore.release());
    }

    private Mono<Void> refreshAllLocked() {
        Map<String, GatewayRouteSpec> currentSnapshot = currentRoutesSnapshot();
        List<RouteConfig> enabledConfigs = routeConfigService.listAll().stream()
                .filter(RouteConfig::isEnabled)
                .toList();
        Map<String, GatewayRouteSpec> desiredRoutes = desiredRoutes(enabledConfigs);
        List<String> idsToRemove = currentSnapshot.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(desiredRoutes.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();
        List<GatewayRouteSpec> specsToSave = desiredRoutes.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(currentSnapshot.get(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();

        return Flux.fromIterable(idsToRemove)
                .concatMap(routeId -> routeDefinitionWriter.delete(Mono.just(routeId))
                        .doOnSuccess(unused -> log.info("已移除路由: {}", routeId))
                        .onErrorResume(err -> {
                            log.warn("移除路由失败: {} — {}", routeId, err.getMessage());
                            return Mono.empty();
                        }))
                .thenMany(Flux.fromIterable(specsToSave)
                        .concatMap(this::saveRoute))
                .then()
                .then(localPortProxyService.refreshAll(enabledConfigs))
                .doOnSuccess(unused -> {
                    replaceCurrentRoutes(desiredRoutes);
                    publisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("路由刷新完成，当前注册路由: {}", currentRouteIdsSnapshot());
                });
    }

    /**
     * 按配置中的每个路径前缀注册 Gateway 路由。
     * 如果配置了本地端口，路径前缀入口先转到本地监听地址，由本地端口代理保留原始 URI 再转发到目标地址。
     */
    private List<GatewayRouteSpec> routeSpecs(RouteConfig config) {
        List<String> prefixes = config.effectivePathPrefixes();
        if (prefixes.isEmpty()) {
            return List.of();
        }
        return IntStream.range(0, prefixes.size())
                .mapToObj(index -> new GatewayRouteSpec(
                        routeId(config.getId(), index),
                        gatewayTargetUrl(config),
                        prefixes.get(index),
                        gatewayStripPrefixSegments(config, prefixes.get(index))
                ))
                .toList();
    }

    private Mono<Void> saveRoute(GatewayRouteSpec spec) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(spec.routeId());
        definition.setUri(URI.create(spec.targetUrl()));
        definition.setOrder(0);

        definition.setPredicates(List.of(
                new PredicateDefinition("Path=" + pathPattern(spec.pathPrefix()))
        ));

        if (spec.stripPrefixSegments() > 0) {
            definition.setFilters(List.of(
                    new FilterDefinition("StripPrefix=" + spec.stripPrefixSegments())
            ));
        }

        return routeDefinitionWriter.save(Mono.just(definition))
                .doOnSuccess(unused -> log.info("已注册路由: {} {} -> {}",
                        spec.routeId(), spec.pathPrefix(), spec.targetUrl()));
    }

    private Map<String, GatewayRouteSpec> desiredRoutes(List<RouteConfig> enabledConfigs) {
        Map<String, GatewayRouteSpec> routes = new LinkedHashMap<>();
        enabledConfigs.stream()
                .flatMap(config -> routeSpecs(config).stream())
                .forEach(spec -> routes.put(spec.routeId(), spec));
        return routes;
    }

    private String routeId(String baseRouteId, int index) {
        return index == 0 ? baseRouteId : baseRouteId + "__" + index;
    }

    private String pathPattern(String pathPrefix) {
        return "/".equals(pathPrefix) ? "/**" : pathPrefix + "," + pathPrefix + "/**";
    }

    private String gatewayTargetUrl(RouteConfig config) {
        if (config.hasLocalBinding()) {
            return "http://" + config.effectiveLocalIp() + ":" + config.getLocalPort();
        }
        return config.getTargetUrl();
    }

    private int gatewayStripPrefixSegments(RouteConfig config, String pathPrefix) {
        if (config.hasLocalBinding()) {
            return 0;
        }
        return stripPrefixSegments(pathPrefix);
    }

    private int stripPrefixSegments(String pathPrefix) {
        if ("/".equals(pathPrefix)) {
            return 0;
        }
        return pathPrefix.split("/").length - 1;
    }

    private List<String> currentRouteIdsSnapshot() {
        synchronized (refreshMonitor) {
            return new ArrayList<>(currentRoutes.keySet());
        }
    }

    private Map<String, GatewayRouteSpec> currentRoutesSnapshot() {
        synchronized (refreshMonitor) {
            return new LinkedHashMap<>(currentRoutes);
        }
    }

    private void replaceCurrentRoutes(Map<String, GatewayRouteSpec> routes) {
        synchronized (refreshMonitor) {
            currentRoutes.clear();
            currentRoutes.putAll(routes);
        }
    }

    private record GatewayRouteSpec(String routeId,
                                    String targetUrl,
                                    String pathPrefix,
                                    int stripPrefixSegments) {
    }
}
