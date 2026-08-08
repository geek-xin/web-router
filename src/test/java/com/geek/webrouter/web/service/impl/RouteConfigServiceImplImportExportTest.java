package com.geek.webrouter.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.entity.RouteConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteConfigServiceImplImportExportTest {

    @TempDir
    Path configDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void importRoutesCreatesFreshIdsAndExportsNormalizedRoutes() throws Exception {
        RouteConfigServiceImpl service = service();
        RouteConfig imported = route("source-id", "orders", 19091);
        imported.setPathPrefix("/legacy");
        imported.setPathPrefixes(null);

        List<RouteConfig> saved = service.importRoutes(List.of(imported));

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getId()).startsWith("route-");
        assertThat(saved.getFirst().getId()).isNotEqualTo("source-id");
        assertThat(saved.getFirst().getPathPrefix()).isEqualTo("/legacy");
        assertThat(saved.getFirst().getPathPrefixes()).containsExactly("/legacy");
        assertThat(service.exportRoutes()).extracting(RouteConfig::getName).containsExactly("orders");
        assertThat(countConfigFiles()).isEqualTo(1);
    }

    @Test
    void importRoutesRejectsNameConflictWithoutWritingAnyImportedFiles() throws Exception {
        RouteConfigServiceImpl service = service();
        service.create(route(null, "orders", 19091));
        long existingFileCount = countConfigFiles();

        assertThatThrownBy(() -> service.importRoutes(List.of(route("source-id", "orders", 19092))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路由名称已存在: orders");

        assertThat(countConfigFiles()).isEqualTo(existingFileCount);
        assertThat(service.exportRoutes()).extracting(RouteConfig::getName).containsExactly("orders");
    }

    @Test
    void importRoutesRejectsEnabledLocalBindingConflictWithoutWritingAnyImportedFiles() throws Exception {
        RouteConfigServiceImpl service = service();
        service.create(route(null, "orders", 19091));
        long existingFileCount = countConfigFiles();

        RouteConfig firstImport = route("source-a", "billing", 19092);
        RouteConfig conflictingImport = route("source-b", "reporting", 19091);

        assertThatThrownBy(() -> service.importRoutes(List.of(firstImport, conflictingImport)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地监听地址已被 [orders] 使用: 127.0.0.1:19091");

        assertThat(countConfigFiles()).isEqualTo(existingFileCount);
        assertThat(service.exportRoutes()).extracting(RouteConfig::getName).containsExactly("orders");
    }

    @Test
    void importRoutesRejectsWithinFileDuplicatesWithoutWritingAnyImportedFiles() throws Exception {
        RouteConfigServiceImpl service = service();

        assertThatThrownBy(() -> service.importRoutes(List.of(
                route("source-a", "orders", 19091),
                route("source-b", "orders", 19092)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件内路由名称重复: orders");

        assertThat(countConfigFiles()).isZero();
    }

    private RouteConfigServiceImpl service() {
        RouteConfigServiceImpl service = new RouteConfigServiceImpl(objectMapper, configDir);
        service.initDefaultConfigs();
        return service;
    }

    private long countConfigFiles() throws Exception {
        try (var files = Files.list(configDir)) {
            return files.count();
        }
    }

    private RouteConfig route(String id, String name, int localPort) {
        RouteConfig config = RouteConfig.builder()
                .id(id)
                .name(name)
                .targetUrl("http://127.0.0.1:8080")
                .accessPageBaseUrl("http://127.0.0.1:8081")
                .accessPage("/admin")
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of("/" + name));
        return config;
    }
}
