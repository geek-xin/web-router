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
                .accessPageBaseUrl("http://127.0.0.1:8082")
                .enabled(true)
                .build();

        RouteConfig saved = service().create(config);

        assertThat(saved.effectivePathPrefixes()).containsExactly("/api", "/admin");
        assertThat(saved.getPathPrefix()).isEqualTo("/api");
    }


    @Test
    void createAllowsRouteWithoutPathPrefixes() {
        RouteConfig config = RouteConfig.builder()
                .name("All Paths")
                .targetUrl("http://127.0.0.1:8081")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(true)
                .build();

        RouteConfig saved = service().create(config);

        assertThat(saved.effectivePathPrefixes()).isEmpty();
        assertThat(saved.getPathPrefix()).isNull();
    }

    @Test
    void createTrimsOptionalAccessPageBeforePersisting() {
        RouteConfig config = RouteConfig.builder()
                .name("Portal")
                .pathPrefixes(List.of("/portal"))
                .targetUrl("http://127.0.0.1:8081")
                .accessPageBaseUrl("  http://127.0.0.1:18080  ")
                .accessPage("  /portal/login.html  ")
                .enabled(false)
                .build();

        RouteConfig saved = service().create(config);
        RouteConfig loaded = service().getByName(saved.getId());

        assertThat(saved.getAccessPageBaseUrl()).isEqualTo("http://127.0.0.1:18080");
        assertThat(loaded.getAccessPageBaseUrl()).isEqualTo("http://127.0.0.1:18080");
        assertThat(saved.getAccessPage()).isEqualTo("/portal/login.html");
        assertThat(loaded.getAccessPage()).isEqualTo("/portal/login.html");
    }

    @Test
    void createStoresBlankAccessPageAsNull() {
        RouteConfig config = RouteConfig.builder()
                .name("Blank Portal")
                .targetUrl("http://127.0.0.1:8082")
                .accessPageBaseUrl("   ")
                .accessPage("   ")
                .enabled(false)
                .build();

        RouteConfig saved = service().create(config);

        assertThat(saved.getAccessPageBaseUrl()).isNull();
        assertThat(saved.getAccessPage()).isNull();
    }

    @Test
    void createRejectsPathPrefixesWithoutProxyAddress() {
        RouteConfig config = RouteConfig.builder()
                .name("Missing Proxy")
                .pathPrefixes(List.of("/portal"))
                .targetUrl("http://127.0.0.1:8081")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(false)
                .build();

        assertThatThrownBy(() -> service().create(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置路径前缀时代理地址不能为空");
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
                  "accessPageBaseUrl": "http://127.0.0.1:8082",
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
                .accessPageBaseUrl("http://127.0.0.1:8082")
                .localIp("999.999.999.999")
                .enabled(false)
                .build();

        assertThatThrownBy(() -> service().create(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地 IP 格式不正确");
    }

    @Test
    void createAllowsSamePathPrefixesAcrossDifferentRoutes() {
        RouteConfigServiceImpl service = service();
        RouteConfig source = RouteConfig.builder()
                .name("演示环境")
                .pathPrefixes(List.of("/iotmgr", "/sysmgr", "/portal"))
                .targetUrl("http://127.0.0.1:9080")
                .accessPageBaseUrl("http://127.0.0.1:9082")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        RouteConfig copy = RouteConfig.builder()
                .name("演示环境-copy")
                .pathPrefixes(List.of("/iotmgr", "/sysmgr", "/portal"))
                .targetUrl("http://127.0.0.1:9081")
                .accessPageBaseUrl("http://127.0.0.1:9083")
                .localIp("127.0.0.1")
                .localPort(8081)
                .enabled(true)
                .build();

        service.create(source);
        RouteConfig savedCopy = service.create(copy);

        assertThat(savedCopy.effectivePathPrefixes()).containsExactly("/iotmgr", "/sysmgr", "/portal");
    }

    @Test
    void createAllowsSameDefaultTargetUrlAcrossDifferentRoutes() {
        RouteConfigServiceImpl service = service();
        RouteConfig source = RouteConfig.builder()
                .name("默认地址源路由")
                .targetUrl("http://127.0.0.1:9080")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(false)
                .build();
        RouteConfig copy = RouteConfig.builder()
                .name("默认地址复制路由")
                .targetUrl("http://127.0.0.1:9080")
                .localIp("127.0.0.1")
                .localPort(9192)
                .enabled(false)
                .build();

        service.create(source);
        RouteConfig savedCopy = service.create(copy);

        assertThat(savedCopy.getTargetUrl()).isEqualTo("http://127.0.0.1:9080");
    }

    @Test
    void createAllowsDisabledCopyToKeepBindingUsedByDisabledRoute() {
        RouteConfigServiceImpl service = service();
        RouteConfig existing = RouteConfig.builder()
                .name("停用演示环境")
                .targetUrl("http://127.0.0.1:9080")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(false)
                .build();
        RouteConfig copied = RouteConfig.builder()
                .name("停用演示环境-copy")
                .targetUrl("http://127.0.0.1:9081")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(false)
                .build();

        service.create(existing);
        RouteConfig savedCopy = service.create(copied);

        assertThat(savedCopy.isEnabled()).isFalse();
        assertThat(savedCopy.getLocalPort()).isEqualTo(9191);
    }

    @Test
    void createRejectsLocalBindingConflictWhenNewRouteIsEnabled() {
        RouteConfigServiceImpl service = service();
        RouteConfig existing = RouteConfig.builder()
                .name("启用演示环境")
                .targetUrl("http://127.0.0.1:9080")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        RouteConfig copy = RouteConfig.builder()
                .name("启用演示环境-copy")
                .targetUrl("http://127.0.0.1:9081")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();

        service.create(existing);

        assertThatThrownBy(() -> service.create(copy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地监听地址已被 [启用演示环境] 使用: 127.0.0.1:9191");
    }

    @Test
    void updateRejectsLocalBindingConflictWhenEnablingRoute() {
        RouteConfigServiceImpl service = service();
        RouteConfig enabled = RouteConfig.builder()
                .name("启用演示环境")
                .targetUrl("http://127.0.0.1:9080")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        RouteConfig disabled = RouteConfig.builder()
                .name("待启用演示环境")
                .targetUrl("http://127.0.0.1:9081")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(false)
                .build();

        service.create(enabled);
        RouteConfig savedDisabled = service.create(disabled);
        disabled.setEnabled(true);

        assertThatThrownBy(() -> service.update(savedDisabled.getId(), disabled))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地监听地址已被 [启用演示环境] 使用: 127.0.0.1:9191");
    }
}
