package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把 docs/CORE-FEATURES.md 中描述的核心能力与文档间引用固化为断言，
 * 防止实现变化后文档静默过期。
 */
class CoreFeaturesDocContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void coreFeaturesDocCoversEveryRuntimeCapability() throws Exception {
        String doc = read("docs/CORE-FEATURES.md");

        assertThat(doc).contains("路由配置管理");
        assertThat(doc).contains("Gateway 动态转发");
        assertThat(doc).contains("每路由本地端口代理");
        assertThat(doc).contains("请求观测与流量指标");
        assertThat(doc).contains("管理后台");
        assertThat(doc).contains("管理 API 参考");
        assertThat(doc).contains("校验规则与错误语义");
        assertThat(doc).contains("关键不变量");
        assertThat(doc).contains("已知边界");
    }

    @Test
    void coreFeaturesDocMatchesImplementedBehaviour() throws Exception {
        String doc = read("docs/CORE-FEATURES.md");
        String service = read("src/main/java/com/geek/webrouter/web/service/ProxyRequestLogService.java");
        String dynamicRoute = read("src/main/java/com/geek/webrouter/config/DynamicRouteService.java");
        String proxy = read("src/main/java/com/geek/webrouter/config/LocalPortProxyService.java");
        String errorCodes = read("src/main/java/com/geek/webrouter/common/enums/ErrorCodeEnum.java");

        // 阈值与窗口常量必须与实现一致
        assertThat(service).contains("MAX_RECENT_LOGS = 100");
        assertThat(doc).contains("最近 **100** 条");
        assertThat(service).contains("SLOW_REQUEST_THRESHOLD_MS = 1000");
        assertThat(doc).contains("耗时 ≥ 1000ms");
        assertThat(service).contains("TRAFFIC_BUCKET_COUNT = 30");
        assertThat(doc).contains("由旧到新，长度恒为 30");

        // 派生 routeId 与 Path 谓词
        assertThat(dynamicRoute).contains("baseRouteId + \"__\" + index");
        assertThat(doc).contains("<id>__<index>");
        assertThat(dynamicRoute).contains("pathPrefix + \",\" + pathPrefix + \"/**\"");
        assertThat(doc).contains("/test,/test/**");

        // 本地代理的失败语义与采样上限
        assertThat(proxy).contains("Proxy request failed");
        assertThat(doc).contains("502");
        assertThat(proxy).contains("MAX_DETAIL_CHARS = 4096");
        assertThat(doc).contains("4096");

        // 错误码表必须覆盖全部枚举项
        assertThat(errorCodes).contains("DUPLICATE_NAME(409");
        assertThat(errorCodes).contains("DUPLICATE_PREFIX(409");
        assertThat(errorCodes).contains("DUPLICATE_LOCAL_BINDING(409");
        assertThat(errorCodes).contains("CONFIG_IO_ERROR(500");
        assertThat(doc).contains("DUPLICATE_NAME");
        assertThat(doc).contains("DUPLICATE_PREFIX");
        assertThat(doc).contains("DUPLICATE_LOCAL_BINDING");
        assertThat(doc).contains("CONFIG_IO_ERROR");
    }

    @Test
    void coreFeaturesDocDocumentsEveryManagementEndpoint() throws Exception {
        String doc = read("docs/CORE-FEATURES.md");
        String routeController = read("src/main/java/com/geek/webrouter/web/controller/RouteConfigController.java");
        String logController = read("src/main/java/com/geek/webrouter/web/controller/ProxyRequestLogController.java");

        assertThat(routeController).contains("/api/routes/export");
        assertThat(routeController).contains("/api/routes/import");
        assertThat(logController).contains("/metrics");
        assertThat(logController).contains("/routes/{routeId}/stream");

        for (String endpoint : new String[]{
                "/admin/api/routes",
                "/admin/api/routes/export",
                "/admin/api/routes/import",
                "/admin/api/proxy-logs",
                "/admin/api/proxy-logs/metrics",
                "/admin/api/proxy-logs/routes/{routeId}",
                "/admin/api/proxy-logs/stream",
                "/actuator/health",
        }) {
            assertThat(doc).as("文档必须记录接口 %s", endpoint).contains(endpoint);
        }
    }

    @Test
    void documentationSetCrossReferencesEachOther() throws Exception {
        String readme = read("README.md");
        String usage = read("USAGE.md");
        String design = read("docs/UI-DESIGN-SYSTEM.md");
        String implementation = read("docs/UI-IMPLEMENTATION.md");
        String core = read("docs/CORE-FEATURES.md");
        String wikiHome = read("wiki/Home.md");

        assertThat(readme).contains("./docs/CORE-FEATURES.md");
        assertThat(readme).contains("./docs/UI-DESIGN-SYSTEM.md");
        assertThat(usage).contains("./docs/CORE-FEATURES.md");
        assertThat(design).contains("CORE-FEATURES.md");
        assertThat(implementation).contains("CORE-FEATURES.md");
        assertThat(core).contains("./UI-DESIGN-SYSTEM.md");
        assertThat(core).contains("./UI-IMPLEMENTATION.md");
        assertThat(wikiHome).contains("../docs/CORE-FEATURES.md");
    }
}
