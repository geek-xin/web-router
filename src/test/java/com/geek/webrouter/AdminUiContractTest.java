package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把 docs/UI-DESIGN-SYSTEM.md 与 docs/UI-IMPLEMENTATION.md 中的关键契约固化成断言，
 * 防止后续改动把 wrouter 的视觉语言退回通用后台模板。
 */
class AdminUiContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void designSystemDefinesTokensTypographyAndRadius() throws Exception {
        String design = read("docs/UI-DESIGN-SYSTEM.md");

        assertThat(design).contains("#07111F");
        assertThat(design).contains("#091525");
        assertThat(design).contains("#0F1D30");
        assertThat(design).contains("#1683FF");
        assertThat(design).contains("#10D9A0");
        assertThat(design).contains("#8B5CF6");
        assertThat(design).contains("#16C7E8");
        assertThat(design).contains("#F59E0B");
        assertThat(design).contains("#F3F7FF");
        assertThat(design).contains("#A8B8CE");
        assertThat(design).contains("#71839B");
        assertThat(design).contains("JetBrains Mono");
        assertThat(design).contains("PingFang SC");
        assertThat(design).contains("Network Line Decoration");
        assertThat(design).contains("Route Accent");
        assertThat(design).contains("Topology-first Detail");
        assertThat(design).contains("Client → wrouter → Local Port → Target");
    }

    @Test
    void designSystemKeepsCardBasedRouteManagementAndBansTemplatePatterns() throws Exception {
        String design = read("docs/UI-DESIGN-SYSTEM.md");

        assertThat(design).contains("Card-based Route List");
        assertThat(design).contains("没有被改成 Table");
        assertThat(design).contains("没有过度玻璃拟态");
        assertThat(design).contains("没有过多渐变");
        assertThat(design).contains("没有过多圆角");
        assertThat(design).contains("没有过度模板化");
        assertThat(design).contains("Route Card 包含 Path");
        assertThat(design).contains("Route Card 包含 Target");
        assertThat(design).contains("Route Card 包含 Local Port");
        assertThat(design).contains("Route Card 包含 Status");
        assertThat(design).contains("Route Card 包含 Traffic / Latency");
        assertThat(design).contains("存在 Sparkline");
        assertThat(design).contains("存在 Route Detail Drawer");
        assertThat(design).contains("Drawer 首屏是 Route Topology");
        assertThat(design).contains("存在 Gateway Runtime 信息");
    }

    @Test
    void implementationDocMapsDesignSystemToRealClassNames() throws Exception {
        String implementation = read("docs/UI-IMPLEMENTATION.md");

        assertThat(implementation).contains(".console-shell");
        assertThat(implementation).contains(".console-sidebar");
        assertThat(implementation).contains(".console-topbar");
        assertThat(implementation).contains(".kpi-card");
        assertThat(implementation).contains(".route-card");
        assertThat(implementation).contains(".route-card-selected");
        assertThat(implementation).contains(".topology-flow");
        assertThat(implementation).contains(".topology-node");
        assertThat(implementation).contains(".route-drawer");
        assertThat(implementation).contains(".network-backdrop");
        assertThat(implementation).contains("requestsLastMinute");
        assertThat(implementation).contains("failedLastMinute");
        assertThat(implementation).contains("averageDurationMs");
        assertThat(implementation).contains("trafficBuckets");
        assertThat(implementation).contains("/admin/api/proxy-logs/metrics");
    }

    @Test
    void stylesheetDefinesConsoleTokensRadiusAndMotion() throws Exception {
        String css = read("frontend/src/styles.css");

        assertThat(css).contains("--console-bg: #07111F");
        assertThat(css).contains("--console-panel: #0F1D30");
        assertThat(css).contains("--console-primary: #1683FF");
        assertThat(css).contains("--console-success: #10D9A0");
        assertThat(css).contains("--console-line: rgba(120, 170, 230, 0.14)");
        assertThat(css).contains("--radius-control: 7px");
        assertThat(css).contains("--radius-card: 10px");
        assertThat(css).contains("--radius-panel: 12px");
        assertThat(css).contains("--sidebar-width: 208px");
        assertThat(css).contains("--topbar-height: 56px");
        assertThat(css).contains("prefers-reduced-motion");
        assertThat(css).contains("@keyframes status-pulse");
        assertThat(css).contains("@keyframes link-flow");
        assertThat(css).contains("@keyframes drawer-in");
        // 禁止旧 chunky/clay 视觉语言残留
        assertThat(css).doesNotContain("border-clay-border");
        assertThat(css).doesNotContain("shadow-clay");
        assertThat(css).doesNotContain("#F45113");
        assertThat(css).doesNotContain("#FFF176");
    }

    @Test
    void routeCardStaysCardBasedAndCarriesRouteTechnicalData() throws Exception {
        String card = read("frontend/src/features/routes/RouteCard.tsx");
        String css = read("frontend/src/styles.css");

        assertThat(card).contains("routeAccentClass(index)");
        assertThat(card).contains("route-card-selected");
        assertThat(card).contains("route-card-muted");
        assertThat(card).contains("route-card-path");
        assertThat(card).contains("route-field-label");
        assertThat(card).contains("Target");
        assertThat(card).contains("本地端口");
        assertThat(card).contains("RouteSparkline");
        assertThat(card).contains("deriveRouteStatus");
        assertThat(card).contains("routeStatusText");
        assertThat(card).contains("formatCompactNumber");
        assertThat(card).contains("formatLatency");

        assertThat(css).contains(".route-card {");
        assertThat(css).contains(".route-card-selected");
        assertThat(css).contains(".route-card-muted");
        assertThat(css).contains(".route-card-spark");
        assertThat(css).contains("max-height: 260px");
        assertThat(css).contains(".accent-blue");
        assertThat(css).contains(".accent-cyan");
        assertThat(css).contains(".accent-purple");
        assertThat(css).contains(".accent-orange");
        assertThat(css).contains(".accent-green");
        assertThat(css).contains(".accent-slate");
        assertThat(css).contains(".route-card-board");
    }

    @Test
    void detailDrawerLeadsWithTopologyAndKeepsFourSectionsAndActions() throws Exception {
        String drawer = read("frontend/src/features/routes/RouteDetailDrawer.tsx");
        String topology = read("frontend/src/features/routes/RouteTopology.tsx");
        String css = read("frontend/src/styles.css");

        assertThat(drawer).contains("<RouteTopology model={topology} idle={!route.enabled} />");
        assertThat(drawer).contains("路由拓扑");
        assertThat(drawer).contains("基本信息");
        assertThat(drawer).contains("运行状态");
        assertThat(drawer).contains("最近日志");
        assertThat(drawer).contains("drawer-section-index");
        assertThat(drawer).contains("route-drawer-actions");
        assertThat(drawer).contains("编辑");
        assertThat(drawer).contains("访问");
        assertThat(drawer).contains("日志");
        assertThat(drawer).contains("删除");
        assertThat(drawer).contains("formatJsonForDisplay");
        assertThat(drawer).contains("编辑 JSON");
        assertThat(read("frontend/src/components/ui/textarea.tsx")).contains("drawer-json-editor");
        assertThat(drawer).contains("route-drawer-scrim");
        assertThat(drawer).contains("aria-modal");
        // 概览首屏必须是拓扑：拓扑在概览标签页内先于基本信息出现
        assertThat(drawer.indexOf("路由拓扑")).isLessThan(drawer.indexOf("基本信息"));
        assertThat(drawer.indexOf("基本信息")).isLessThan(drawer.indexOf("运行状态"));
        assertThat(drawer.indexOf("运行状态")).isLessThan(drawer.indexOf("最近日志"));

        assertThat(topology).contains("Client");
        assertThat(topology).contains("wrouter");
        assertThat(topology).contains("本地端口");
        assertThat(topology).contains("目标服务");
        assertThat(topology).contains("topology-node");
        assertThat(topology).contains("topology-link");
        assertThat(topology).contains("topology-fallback");

        assertThat(css).contains(".route-drawer {");
        assertThat(css).contains("width: min(92vw, 420px)");
        assertThat(css).contains(".drawer-json-editor");
        assertThat(css).contains(".drawer-section-index");
        assertThat(css).contains(".topology-node");
        assertThat(css).contains(".topology-node-gateway");
        assertThat(css).contains(".topology-link-flow");
        assertThat(css).contains(".topology-fallback");
    }

    @Test
    void shellProvidesSidebarTopbarKpiAndNetworkBackdrop() throws Exception {
        String app = read("frontend/src/App.tsx");
        String css = read("frontend/src/styles.css");

        assertThat(app).contains("console-shell");
        assertThat(app).contains("ConsoleSidebar");
        assertThat(app).contains("ConsoleTopbar");
        assertThat(app).contains("NetworkBackdrop");
        assertThat(app).contains("KpiCard");
        assertThat(app).contains("Gateway Running");
        assertThat(app).contains("GATEWAY_ENDPOINT");
        assertThat(read("frontend/src/lib/app-meta.ts")).contains("127.0.0.1:9999");
        // 版本号显示与更新入口
        assertThat(app).contains("VersionDialog");
        assertThat(app).contains("version-pill");
        assertThat(app).contains("/admin/api/version");
        assertThat(app).contains("runtime-card-version-button");
        assertThat(read("frontend/src/features/system/VersionDialog.tsx")).contains("/admin/api/update/check");
        assertThat(read("frontend/src/features/system/VersionDialog.tsx")).contains("/admin/api/update/apply");
        assertThat(css).contains(".version-pill");
        assertThat(css).contains(".runtime-card-version-button");
        assertThat(app).contains("Local Gateway Console");
        assertThat(app).contains("网关运行中");
        assertThat(app).contains("路由总数");
        assertThat(app).contains("运行中");
        assertThat(app).contains("已停用");
        assertThat(app).contains("请求数 / 分钟");
        assertThat(app).contains("平均延迟");
        assertThat(app).contains("ROUTE");
        // 卡片式路由列表：网格渲染 RouteCard，而不是表格
        assertThat(app).contains("route-card-board");
        assertThat(app).contains("<RouteCard");
        assertThat(app).doesNotContain("<Table");

        assertThat(css).contains(".console-shell");
        assertThat(css).contains(".console-sidebar");
        assertThat(css).contains(".console-topbar");
        assertThat(css).contains(".kpi-row");
        assertThat(css).contains(".kpi-card");
        assertThat(css).contains(".side-nav-item-active");
        assertThat(css).contains(".runtime-card");
        assertThat(css).contains(".network-backdrop");
    }

    @Test
    void brandMarkAndFaviconUseRouteNodesInsteadOfGenericIcons() throws Exception {
        String favicon = read("frontend/public/favicon.svg");
        String app = read("frontend/src/App.tsx");

        assertThat(favicon).contains("<circle");
        assertThat(favicon).contains("#1683FF");
        assertThat(favicon).contains("#10D9A0");
        assertThat(favicon).doesNotContain("shield");
        assertThat(favicon).doesNotContain("cloud");

        assertThat(app).contains("RouteNodeMark");
        assertThat(app).contains("NetworkBackdrop");
    }

    @Test
    void frontendConsumesCompactTrafficMetricsEndpointWithGracefulFallback() throws Exception {
        String app = read("frontend/src/App.tsx");
        String metrics = read("frontend/src/features/routes/route-metrics.ts");

        assertThat(app).contains("/admin/api/proxy-logs/metrics");
        assertThat(app).contains("normalizeTrafficMetrics");
        assertThat(metrics).contains("TRAFFIC_BUCKET_COUNT = TRAFFIC_WINDOW_MINUTES");
        assertThat(read("frontend/src/lib/app-meta.ts")).contains("TRAFFIC_WINDOW_MINUTES = 30");
        assertThat(metrics).contains("requestsLastMinute");
        assertThat(metrics).contains("averageDurationMs");
        assertThat(metrics).contains("trafficBuckets");
        assertThat(metrics).contains("sparklineGeometry");
    }
}
