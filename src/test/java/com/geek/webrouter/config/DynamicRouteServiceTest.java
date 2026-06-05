package com.geek.webrouter.config;

import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import com.geek.webrouter.web.service.RouteConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicRouteServiceTest {

    @Test
    void staleRefreshDoesNotReRegisterRouteAfterNewerRefreshDisabledIt() {
        MutableRouteConfigService routeConfigService = new MutableRouteConfigService();
        RecordingRouteDefinitionWriter writer = new RecordingRouteDefinitionWriter();
        DynamicRouteService service = new DynamicRouteService(
                writer,
                routeConfigService,
                new NoopPublisher(),
                new NoopLocalPortProxyService()
        );
        routeConfigService.configs = List.of(routeConfig("route-a", "/a"));

        Mono<Void> staleEnableRefresh = service.refreshAll();

        routeConfigService.configs = List.of();
        service.refreshAll().block(Duration.ofSeconds(3));
        staleEnableRefresh.block(Duration.ofSeconds(3));

        assertThat(writer.savedRouteIds).doesNotContain("route-a");
    }

    @Test
    void refreshOnlyUpdatesChangedGatewayRoutes() {
        MutableRouteConfigService routeConfigService = new MutableRouteConfigService();
        RecordingRouteDefinitionWriter writer = new RecordingRouteDefinitionWriter();
        DynamicRouteService service = new DynamicRouteService(
                writer,
                routeConfigService,
                new NoopPublisher(),
                new NoopLocalPortProxyService()
        );
        routeConfigService.configs = List.of(
                routeConfig("route-a", "/a"),
                routeConfig("route-b", "/b")
        );
        service.refreshAll().block(Duration.ofSeconds(3));
        writer.clear();

        routeConfigService.configs = List.of(
                routeConfig("route-a", "/a"),
                routeConfig("route-b", "/changed")
        );
        service.refreshAll().block(Duration.ofSeconds(3));

        assertThat(writer.savedRouteIds).containsExactly("route-b");
        assertThat(writer.deletedRouteIds).containsExactly("route-b");
    }

    private RouteConfig routeConfig(String id, String pathPrefix) {
        RouteConfig config = RouteConfig.builder()
                .id(id)
                .name(id)
                .pathPrefixes(List.of(pathPrefix))
                .targetUrl("http://127.0.0.1:8081")
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of(pathPrefix));
        return config;
    }

    private static class MutableRouteConfigService implements RouteConfigService {
        private List<RouteConfig> configs = List.of();

        @Override
        public List<RouteConfig> listAll() {
            return configs;
        }

        @Override
        public RouteConfig getByName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RouteConfig create(RouteConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RouteConfig update(String name, RouteConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void initDefaultConfigs() {
        }
    }

    private static class RecordingRouteDefinitionWriter implements RouteDefinitionWriter {
        private final List<String> savedRouteIds = new CopyOnWriteArrayList<>();
        private final List<String> deletedRouteIds = new CopyOnWriteArrayList<>();
        private final Map<String, RouteDefinition> routeDefinitions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Mono<Void> save(Mono<RouteDefinition> route) {
            return route.doOnNext(definition -> {
                savedRouteIds.add(definition.getId());
                routeDefinitions.put(definition.getId(), definition);
            }).then();
        }

        @Override
        public Mono<Void> delete(Mono<String> routeId) {
            return routeId.doOnNext(id -> {
                deletedRouteIds.add(id);
                routeDefinitions.remove(id);
            }).then();
        }

        private void clear() {
            savedRouteIds.clear();
            deletedRouteIds.clear();
        }
    }

    private static class NoopPublisher implements ApplicationEventPublisher {
        @Override
        public void publishEvent(ApplicationEvent event) {
        }

        @Override
        public void publishEvent(Object event) {
        }
    }

    private static class NoopLocalPortProxyService extends LocalPortProxyService {
        NoopLocalPortProxyService() {
            super(new ProxyRequestLogService(), (config, handler) -> { throw new UnsupportedOperationException(); });
        }

        @Override
        public Mono<Void> refreshAll(List<RouteConfig> enabledConfigs) {
            return Mono.empty();
        }
    }
}
