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

class RouteConfigControllerTest {

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
    void createReturnsValidationMessageWhenAccessPageBaseUrlHasNoPort() {
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
                .jsonPath("$.message").isEqualTo("访问页地址格式不正确，如 192.168.1.100:8080 或 web.example.com:8080");
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

    private static class NoopRouteConfigService implements RouteConfigService {

        @Override
        public List<RouteConfig> listAll() {
            return List.of();
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

        NoopDynamicRouteService() {
            super(null, null, null, null);
        }

        @Override
        public Mono<Void> refreshAll() {
            return Mono.empty();
        }
    }
}
