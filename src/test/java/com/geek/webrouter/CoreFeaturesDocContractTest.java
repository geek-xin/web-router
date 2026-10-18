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
        assertThat(doc).contains("无感自动更新");
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
                "/admin/api/update/auto",
                "/admin/api/update/history",
                "/actuator/health",
        }) {
            assertThat(doc).as("文档必须记录接口 %s", endpoint).contains(endpoint);
        }
    }

    /**
     * 无感自动更新的关键契约：默认开启、两阶段拆分、空闲判定接入两条代理路径、
     * 文档记录默认阈值。
     */
    @Test
    void seamlessAutoUpdateContractIsDocumentedAndImplemented() throws Exception {
        String doc = read("docs/CORE-FEATURES.md");
        String properties = read("src/main/java/com/geek/webrouter/config/UpdateProperties.java");
        String auto = read("src/main/java/com/geek/webrouter/web/service/AutoUpdateService.java");
        String tracker = read("src/main/java/com/geek/webrouter/web/service/InFlightRequestTracker.java");
        String updateService = read("src/main/java/com/geek/webrouter/web/service/UpdateService.java");
        String gateway = read("src/main/java/com/geek/webrouter/config/ProxyRequestLogFilter.java");
        String localProxy = read("src/main/java/com/geek/webrouter/config/LocalPortProxyService.java");
        String application = read("src/main/java/com/geek/webrouter/Application.java");
        String yml = read("src/main/resources/application.yml");
        String controller = read("src/main/java/com/geek/webrouter/web/controller/AppVersionController.java");

        // 默认开启，且阈值与文档一致
        assertThat(properties).contains("private boolean auto = true");
        assertThat(properties).contains("private long initialDelaySeconds = 120");
        assertThat(properties).contains("private long checkIntervalSeconds = 3600");
        assertThat(properties).contains("private long quietSeconds = 30");
        assertThat(properties).contains("private boolean autoApply = true");
        assertThat(doc).contains("默认开启");
        assertThat(doc).contains("120");
        assertThat(doc).contains("3600");
        assertThat(doc).contains("30");

        // 两阶段：先暂存（不退出），空闲后再退出
        assertThat(updateService).contains("UpdateApplyResult stage()");
        assertThat(updateService).contains("void requestExit()");
        assertThat(auto).contains("updateService.stage()");
        assertThat(auto).contains("updateService.requestExit()");
        assertThat(auto).contains("@Scheduled");

        // 空闲判定必须接入 Gateway 与本地端口代理两条路径
        assertThat(tracker).contains("public int begin()");
        assertThat(tracker).contains("public int end()");
        assertThat(tracker).contains("public boolean isIdle(long quietMillis)");
        assertThat(gateway).contains("inFlightTracker.begin()");
        assertThat(gateway).contains("inFlightTracker.end()");
        assertThat(localProxy).contains("inFlightTracker.begin()");
        assertThat(localProxy).contains("inFlightTracker.end()");

        // 调度依赖 @EnableScheduling，重启依赖优雅关闭
        assertThat(application).contains("@EnableScheduling");
        assertThat(yml).contains("shutdown: graceful");
        assertThat(yml).contains("timeout-per-shutdown-phase");

        // 状态接口可观测
        assertThat(controller).contains("/update/auto");
        assertThat(doc).contains("/admin/api/update/auto");
    }

    /**
     * 阻塞式 GitHub 调用必须离开 WebFlux 事件循环。
     *
     * <p>历史缺陷：{@code AppVersionController} 直接调用阻塞的
     * {@code HttpReleaseClient.listReleases()}（内部 {@code HttpClient.send()}），
     * 而事件循环线程数 = CPU 核数。实测 12 个并发阻塞调用会让纯内存接口
     * {@code /admin/api/version} 从 0.9ms 恶化到 537ms，网络抖动时直接抛
     * {@code ConnectException}（表现为「读取历史版本失败」）。</p>
     */
    @Test
    void blockingGitHubCallsStayOffTheEventLoop() throws Exception {
        String doc = read("docs/CORE-FEATURES.md");
        String controller = read("src/main/java/com/geek/webrouter/web/controller/AppVersionController.java");
        String releaseClient = read("src/main/java/com/geek/webrouter/web/service/impl/HttpReleaseClient.java");

        // 控制器必须把阻塞调用切到 boundedElastic，且返回 Mono
        assertThat(controller).contains("Schedulers.boundedElastic()");
        assertThat(controller).contains("Mono.fromCallable");
        assertThat(controller).contains("public Mono<Result<ReleaseHistory>> history()");
        assertThat(controller).contains("public Mono<Result<UpdateCheckResult>> check()");

        // 客户端必须对瞬时连接故障做有限次重试
        assertThat(releaseClient).contains("MAX_ATTEMPTS");
        assertThat(releaseClient).contains("withRetry");
        assertThat(releaseClient).contains("IOException");

        // 文档必须记录这条约束（锚定本节标题，避免命中别处的泛泛描述）
        assertThat(doc).contains("阻塞调用必须离开事件循环");
        assertThat(doc).contains("Schedulers.boundedElastic()");
        assertThat(doc).contains("ConnectException");
    }

    /**
     * 平台兜底：构建元信息缺省时必须按运行期推导，且推导结果受发布契约约束。
     * 文档与实现必须同时记录这条不变量，避免「未识别平台」回归。
     */
    @Test
    void runtimePlatformFallbackIsDocumentedAndImplemented() throws Exception {
        String doc = read("docs/CORE-FEATURES.md");
        String service = read("src/main/java/com/geek/webrouter/web/service/impl/AppVersionServiceImpl.java");
        String runtime = read("src/main/java/com/geek/webrouter/web/support/RuntimePlatform.java");
        String updatePlatform = read("src/main/java/com/geek/webrouter/web/support/UpdatePlatform.java");

        // 版本服务在构建值不可用时按运行期推导
        assertThat(service).contains("resolvePlatform(");
        assertThat(service).contains("RuntimePlatform.currentToken()");
        assertThat(runtime).contains("os.name");
        assertThat(runtime).contains("os.arch");
        // 推导结果必须过发布契约，不能引入契约外标识
        assertThat(runtime).contains("ReleaseAssets.platformToken");

        // macOS 必须先于 Windows 判定："darwin" 含子串 "win"，
        // 顺序反了会把 macOS 认成 Windows（显示错平台 + 生成 .bat 更新脚本）
        assertThat(runtime.indexOf("name.contains(\"mac\")"))
                .isLessThan(runtime.indexOf("name.contains(\"win\")"));
        assertThat(updatePlatform.indexOf("name.contains(\"mac\")"))
                .isLessThan(updatePlatform.indexOf("name.contains(\"win\")"));

        // 文档必须记录这条兜底与顺序约束
        assertThat(doc).contains("RuntimePlatform.currentToken()");
        assertThat(doc).contains("darwin");
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
        // wiki 是独立仓库（web-router.wiki.git），页面里的相对路径 ../docs/... 在 GitHub Wiki 上会失效，
        // 因此 wiki 一律使用指向仓库的绝对链接；这里只校验「确实引用了核心功能文档」，
        // 不锁死链接形态，避免为了通过测试而把死链写回去。
        assertThat(wikiHome).contains("docs/CORE-FEATURES.md");
    }
}
