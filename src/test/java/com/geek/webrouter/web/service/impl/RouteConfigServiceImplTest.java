package com.geek.webrouter.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.entity.RouteConfig;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteConfigServiceImplTest {

    @TempDir
    Path tempDir;

    private RouteConfigServiceImpl service() {
        RouteConfigServiceImpl service = new RouteConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        return service;
    }

    @Test
    void springContextCreatesRouteConfigServiceWithObjectMapperDependency() {
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        JacksonAutoConfiguration.class))
                .withUserConfiguration(RouteConfigServiceImpl.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(RouteConfigServiceImpl.class)
                        .hasSingleBean(ObjectMapper.class));
    }

    @Test
    void createNormalizesTrailingSlashPathPrefixesBeforePersisting() {
        RouteConfig config = RouteConfig.builder()
                .name("API")
                .pathPrefixes(List.of("/api/", "/admin//"))
                .targetUrl("http://127.0.0.1:8081")
                .enabled(true)
                .build();

        RouteConfig saved = service().create(config);

        assertThat(saved.effectivePathPrefixes()).containsExactly("/api", "/admin");
        assertThat(saved.getPathPrefix()).isEqualTo("/api");
    }

    @Test
    void listAllUsesFileNameAsAuthoritativeRouteIdWhenJsonIdDiffers() throws Exception {
        Files.writeString(tempDir.resolve("route-file.json"), """
                {
                  "id": "route-json",
                  "name": "Route File",
                  "pathPrefix": "/file",
                  "pathPrefixes": ["/file"],
                  "targetUrl": "http://127.0.0.1:8081",
                  "enabled": true
                }
                """);

        List<RouteConfig> configs = service().listAll();

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().getId()).isEqualTo("route-file");
    }

    @Test
    void createRejectsInvalidLocalIpEvenWithoutLocalPort() {
        RouteConfig config = RouteConfig.builder()
                .name("Invalid IP")
                .pathPrefixes(List.of("/invalid-ip"))
                .targetUrl("http://127.0.0.1:8081")
                .localIp("999.999.999.999")
                .enabled(false)
                .build();

        assertThatThrownBy(() -> service().create(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地 IP 格式不正确");
    }
}
