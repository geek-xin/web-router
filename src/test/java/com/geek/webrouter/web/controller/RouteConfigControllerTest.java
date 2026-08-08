package com.geek.webrouter.web.controller;

import com.geek.webrouter.common.exception.GlobalExceptionHandler;
import com.geek.webrouter.config.DynamicRouteService;
import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.RouteConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigControllerTest {

    @Test
    void exportRoutesReturnsVersionedPayload() {
        NoopRouteConfigService routeConfigService = new NoopRouteConfigService();
        RouteConfig route = routeConfig("orders", 19091);
        route.setId("route-a");
        routeConfigService.exportRoutes = List.of(route);
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        routeConfigService, new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.get()
                .uri("/admin/api/routes/export")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.version").isEqualTo(1)
                .jsonPath("$.data.routes.length()").isEqualTo(1)
                .jsonPath("$.data.routes[0].id").isEqualTo("route-a")
                .jsonPath("$.data.routes[0].name").isEqualTo("orders");
    }

    @Test
    void importRoutesReturnsCountAndRefreshesDynamicRoutes() {
        NoopRouteConfigService routeConfigService = new NoopRouteConfigService();
        routeConfigService.importedRoutes = List.of(routeConfig("orders", 19091));
        NoopDynamicRouteService dynamicRouteService = new NoopDynamicRouteService();
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        routeConfigService, dynamicRouteService))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes/import")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "version": 1,
                          "routes": [
                            {
                              "name": "orders",
                              "pathPrefixes": ["/orders"],
                              "targetUrl": "127.0.0.1:8080",
                              "accessPageBaseUrl": "127.0.0.1:8081",
                              "localIp": "127.0.0.1",
                              "localPort": 19091,
                              "enabled": true
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.importedCount").isEqualTo(1);

        assertThat(dynamicRouteService.refreshCount).hasValue(1);
    }

    @Test
    void createReturnsValidationMessageWhenTargetUrlHasNoPort() {
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        new NoopRouteConfigService(), new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "iotmgr",
                          "pathPrefix": "/iotmgr",
                          "targetUrl": "127.0.0.1",
                          "accessPageBaseUrl": "127.0.0.1:8081",
                          "localPort": 9191,
                          "enabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("默认地址（兜底）格式不正确，如 192.168.1.100:8080 或 api.example.com:8080");
    }

    @Test
    void createReturnsValidationMessageWhenLocalPortMissing() {
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        new NoopRouteConfigService(), new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "iotmgr",
                          "pathPrefix": "/iotmgr",
                          "targetUrl": "127.0.0.1:8080",
                          "accessPageBaseUrl": "127.0.0.1:8081",
                          "localIp": "127.0.0.1",
                          "enabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("本地端口不能为空");
    }

    @Test
    void createReturnsValidationMessageWhenProxyAddressHasNoPort() {
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        new NoopRouteConfigService(), new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "iotmgr",
                          "pathPrefix": "/iotmgr",
                          "targetUrl": "127.0.0.1:8080",
                          "accessPageBaseUrl": "127.0.0.1",
                          "localIp": "127.0.0.1",
                          "localPort": 9191,
                          "enabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("代理地址格式不正确，如 192.168.1.100:8080 或 proxy.example.com:8080");
    }

    @Test
    void createReturnsValidationMessageWhenPathPrefixHasNoProxyAddress() {
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        new NoopRouteConfigService(), new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "iotmgr",
                          "pathPrefix": "/iotmgr",
                          "targetUrl": "127.0.0.1:8080",
                          "localIp": "127.0.0.1",
                          "localPort": 9191,
                          "enabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("配置路径前缀时代理地址不能为空");
    }


    @Test
    void createAllowsMissingPathPrefixes() {
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        new NoopRouteConfigService(), new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "all-paths",
                          "targetUrl": "127.0.0.1:8080",
                          "localIp": "127.0.0.1",
                          "localPort": 9191,
                          "enabled": true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.pathPrefixes.length()").isEqualTo(0);
    }

    @Test
    void createReturnsValidationMessageWhenRouteNameTooLong() {
        WebTestClient client = WebTestClient.bindToController(new RouteConfigController(
                        new NoopRouteConfigService(), new NoopDynamicRouteService()))
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator())
                .build();

        client.post()
                .uri("/admin/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一",
                          "pathPrefix": "/iotmgr",
                          "targetUrl": "127.0.0.1:8080",
                          "accessPageBaseUrl": "127.0.0.1:8081",
                          "localIp": "127.0.0.1",
                          "localPort": 9191,
                          "enabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("路由名称不能超过 50 个字");
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private static RouteConfig routeConfig(String name, int localPort) {
        RouteConfig config = RouteConfig.builder()
                .name(name)
                .targetUrl("http://127.0.0.1:8080")
                .accessPageBaseUrl("http://127.0.0.1:8081")
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of("/" + name));
        return config;
    }

    private static class NoopRouteConfigService implements RouteConfigService {

        private List<RouteConfig> exportRoutes = List.of();
        private List<RouteConfig> importedRoutes = List.of();

        @Override
        public List<RouteConfig> listAll() {
            return List.of();
        }

        @Override
        public List<RouteConfig> exportRoutes() {
            return exportRoutes;
        }

        @Override
        public RouteConfig getByName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RouteConfig create(RouteConfig config) {
            return config;
        }

        @Override
        public List<RouteConfig> importRoutes(List<RouteConfig> configs) {
            return importedRoutes;
        }

        @Override
        public RouteConfig update(String name, RouteConfig config) {
            return config;
        }

        @Override
        public void delete(String name) {
        }

        @Override
        public void initDefaultConfigs() {
        }
    }

    private static class NoopDynamicRouteService extends DynamicRouteService {
        private final AtomicInteger refreshCount = new AtomicInteger();

        NoopDynamicRouteService() {
            super(null, null, null, null);
        }

        @Override
        public Mono<Void> refreshAll() {
            refreshCount.incrementAndGet();
            return Mono.empty();
        }
    }
}
