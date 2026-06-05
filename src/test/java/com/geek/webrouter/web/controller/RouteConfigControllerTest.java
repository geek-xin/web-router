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
                          "enabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("目标地址格式不正确，如 192.168.1.100:8080 或 api.example.com:8080");
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
