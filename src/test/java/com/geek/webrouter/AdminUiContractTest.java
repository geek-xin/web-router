package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把 docs/UI-DESIGN-SYSTEM.md 与 docs/UI-IMPLEMENTATION.md 中的关键契约固化成断言，
 * 防止后续改动把 wrouter 的视觉语言退回通用后台模板。
 *
 * 当前视觉方向：暖纸画布 + 白色操作外壳 + 衬线标题 + 单处渐变概览带。
 */
class AdminUiContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String stylesheet() throws Exception {
        return read("frontend/src/styles.css");
    }

    /**
     * 收集样式表里定义过的所有类名（用于「文档与实现同步」检查）。
     * 先剔除 @import 与内联 data: URI —— 后者含有 www.w3.org 之类的字样，会被误当成类名。
     */
    private static Set<String> cssClasses(String css) {
        String cleaned = css
                .replaceAll("@import url\\([^)]*\\);", "")
                .replaceAll("url\\(\\s*\"data:[^\"]*\"\\s*\\)", "");
        Set<String> names = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\.(-?[_a-zA-Z][\\w-]*)").matcher(cleaned);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    @Test
    void designSystemDefinesTokensTypographyAndSignatureTraits() throws Exception {
        String design = read("docs/UI-DESIGN-SYSTEM.md");

        assertThat(design).contains("#F1F0EC");
        assertThat(design).contains("#F4F3EF");
        assertThat(design).contains("#FFFFFF");
        assertThat(design).contains("#E7E4DC");
        assertThat(design).contains("#101F33");
        assertThat(design).contains("#4E5C6E");
        assertThat(design).contains("#667080");
        assertThat(design).contains("#2F6FED");
        assertThat(design).contains("#1E9E6A");
        assertThat(design).contains("#C2760A");
        assertThat(design).contains("#C0392B");
        assertThat(design).contains("#8FC1FF");
        assertThat(design).contains("Newsreader");
        assertThat(design).contains("JetBrains Mono");
        assertThat(design).contains("PingFang SC");
        assertThat(design).contains("Paper Shell");
        assertThat(design).contains("Editorial Heading");
        assertThat(design).contains("Single Gradient Band");
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

        assertThat(implementation).contains(".app-shell");
        assertThat(implementation).contains(".app-sidebar");
        assertThat(implementation).contains(".app-topbar");
        assertThat(implementation).contains(".band");
        assertThat(implementation).contains(".route-card");
        assertThat(implementation).contains(".route-card-selected");
        assertThat(implementation).contains(".topology-node");
        assertThat(implementation).contains(".drawer");
        assertThat(implementation).contains("requestsLastMinute");
        assertThat(implementation).contains("failedLastMinute");
        assertThat(implementation).contains("averageDurationMs");
        assertThat(implementation).contains("trafficBuckets");
        assertThat(implementation).contains("/admin/api/proxy-logs/metrics");
    }

    /** 文档必须覆盖样式表里定义过的每一个类名，避免规范与实现脱节。 */
    @Test
    void implementationDocListsEveryClassDefinedInTheStylesheet() throws Exception {
        String css = stylesheet();
        String implementation = read("docs/UI-IMPLEMENTATION.md");
        Set<String> undocumented = cssClasses(css).stream()
                .filter(name -> !implementation.contains(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(undocumented)
                .as("以下 CSS 类未出现在 docs/UI-IMPLEMENTATION.md 的类名清单中，请同步文档")
                .isEmpty();
    }

    /** 设计系统文档必须覆盖样式表里的每一个 CSS 变量。 */
    @Test
    void designSystemDocListsEveryCustomProperty() throws Exception {
        String css = stylesheet();
        String design = read("docs/UI-DESIGN-SYSTEM.md");
        Set<String> undocumented = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^\\s*(--[\\w-]+)\\s*:", Pattern.MULTILINE).matcher(css);
        while (m.find()) {
            if (!design.contains(m.group(1))) {
                undocumented.add(m.group(1));
            }
        }

        assertThat(undocumented)
                .as("以下 CSS 令牌未出现在 docs/UI-DESIGN-SYSTEM.md 的令牌表中，请同步文档")
                .isEmpty();
    }

    @Test
    void stylesheetDefinesSurfaceTokensRadiusAndMotion() throws Exception {
        String css = stylesheet();

        assertThat(css).contains("--surface-canvas: #F1F0EC");
        assertThat(css).contains("--surface-sunken: #F4F3EF");
        assertThat(css).contains("--surface-shell: #FFFFFF");
        assertThat(css).contains("--surface-line: #E7E4DC");
        assertThat(css).contains("--surface-ink: #101F33");
        assertThat(css).contains("--surface-accent: #2F6FED");
        assertThat(css).contains("--surface-success: #1E9E6A");
        assertThat(css).contains("--band-grad-c: #8FC1FF");
        assertThat(css).contains("--radius-control: 10px");
        assertThat(css).contains("--radius-card: 14px");
        assertThat(css).contains("--radius-band: 20px");
        assertThat(css).contains("--sidebar-width: 236px");
        assertThat(css).contains("--font-display: Newsreader");
        assertThat(css).contains("prefers-reduced-motion");
        assertThat(css).contains("@keyframes dot-pulse");
        assertThat(css).contains("@keyframes link-flow");
        assertThat(css).contains("@keyframes drawer-in");
        // 禁止旧 chunky/clay 视觉语言残留
        assertThat(css).doesNotContain("border-clay-border");
        assertThat(css).doesNotContain("shadow-clay");
        assertThat(css).doesNotContain("#F45113");
        assertThat(css).doesNotContain("#FFF176");
        // 禁止旧深色霓虹控制台令牌与背景网络装饰
        assertThat(css).doesNotContain("--console-bg:");
        assertThat(css).doesNotContain("--console-panel:");
        assertThat(css).doesNotContain(".network-backdrop");
        // 已确认无引用的令牌不得回流
        assertThat(css).doesNotContain("--surface-canvas-deep");
        assertThat(css).doesNotContain("--surface-info");
        assertThat(css).doesNotContain("--surface-hover-border");
        assertThat(css).doesNotContain("--topbar-height");
    }

    /** 概览带必须使用专用墨色，不能复用页面墨色（否则渐变右端对比度不足）。 */
    @Test
    void gradientBandUsesDedicatedInkTokens() throws Exception {
        String css = stylesheet();

        assertThat(css).contains("--band-ink:");
        assertThat(css).contains("--band-ink-muted:");

        String bandChart = css.substring(css.indexOf(".band-chart-label {"), css.indexOf(".band-bars {"));
        assertThat(bandChart).contains("var(--band-ink-muted)");
        assertThat(bandChart).doesNotContain("var(--surface-ink-subtle)");
    }

    /** 概览带是唯一允许出现装饰性渐变的位置；仅骨架微光可以例外。 */
    @Test
    void gradientBandIsTheOnlyDecorativeGradient() throws Exception {
        String css = stylesheet();

        String bandRule = css.substring(css.indexOf(".band {"), css.indexOf(".band-lead {"));
        assertThat(bandRule).contains("linear-gradient");
        assertThat(css.split("linear-gradient", -1).length - 1).isEqualTo(2);
    }

    /** 状态点用 state-*，日志 HTTP 状态码用 status-*，两套命名空间不可混用。 */
    @Test
    void statusDotClassesStayInTheStateNamespace() throws Exception {
        String utils = read("frontend/src/features/routes/route-utils.ts");
        String css = stylesheet();

        assertThat(utils).contains("return 'state-running'");
        assertThat(utils).contains("return 'state-stopped'");
        assertThat(utils).contains("return 'state-warning'");
        assertThat(utils).contains("return 'state-error'");
        assertThat(utils).doesNotContain("return 'status-running'");

        assertThat(css).contains(".state-running");
        assertThat(css).contains(".state-stopped");
        assertThat(css).contains(".state-warning");
        assertThat(css).contains(".state-error");
        assertThat(css).contains(".status-2xx");
        assertThat(css).contains(".status-5xx");
    }

    /**
     * Tailwind 的 utilities 必须在样式表末尾输出。
     * 组件类与工具类特异性相同，若 utilities 在前，w-[min(92vw,430px)] 会被 .modal 的宽度覆盖。
     */
    @Test
    void tailwindUtilitiesAreEmittedLastSoTheyCanOverrideComponents() throws Exception {
        String css = stylesheet();

        int utilities = css.lastIndexOf("@tailwind utilities;");
        int modal = css.indexOf(".modal {");
        int routeCard = css.indexOf(".route-card {");

        assertThat(utilities).as("@tailwind utilities 必须存在").isGreaterThan(0);
        assertThat(utilities).as("@tailwind utilities 必须在组件类之后输出").isGreaterThan(modal);
        assertThat(utilities).isGreaterThan(routeCard);
        assertThat(css).contains("width: min(94vw, 468px)");
    }

    @Test
    void routeCardStaysCardBasedAndCarriesRouteTechnicalData() throws Exception {
        String card = read("frontend/src/features/routes/RouteCard.tsx");
        String css = stylesheet();

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
        String css = stylesheet();

        assertThat(drawer).contains("<RouteTopology model={topology} idle={!route.enabled} />");
        assertThat(drawer).contains("路由拓扑");
        assertThat(drawer).contains("基本信息");
        assertThat(drawer).contains("运行状态");
        assertThat(drawer).contains("最近日志");
        assertThat(drawer).contains("section-index");
        assertThat(drawer).contains("drawer-actions");
        assertThat(drawer).contains("编辑");
        assertThat(drawer).contains("访问");
        assertThat(drawer).contains("日志");
        assertThat(drawer).contains("删除");
        assertThat(drawer).contains("formatJsonForDisplay");
        assertThat(drawer).contains("编辑 JSON");
        assertThat(read("frontend/src/components/ui/textarea.tsx")).contains("json-editor");
        assertThat(drawer).contains("drawer-scrim");
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

        assertThat(css).contains(".drawer {");
        assertThat(css).contains("width: min(94vw, 468px)");
        assertThat(css).contains(".json-editor");
        assertThat(css).contains(".section-index");
        assertThat(css).contains(".topology-node");
        assertThat(css).contains(".topology-node-gateway");
        assertThat(css).contains(".topology-link-flow");
        assertThat(css).contains(".topology-fallback");
    }

    @Test
    void shellProvidesSidebarTopbarOverviewBandAndEditorialHeading() throws Exception {
        String app = read("frontend/src/App.tsx");
        String css = stylesheet();

        assertThat(app).contains("app-shell");
        assertThat(app).contains("AppSidebar");
        assertThat(app).contains("AppTopbar");
        assertThat(app).contains("OverviewBand");
        assertThat(app).contains("Gateway 运行中");
        assertThat(app).contains("GATEWAY_ENDPOINT");
        assertThat(read("frontend/src/lib/app-meta.ts")).contains("127.0.0.1:9999");
        // 版本号显示与更新入口
        assertThat(app).contains("VersionDialog");
        assertThat(app).contains("version-pill");
        assertThat(app).contains("/admin/api/version");
        assertThat(app).contains("status-panel-meta");
        assertThat(read("frontend/src/features/system/VersionDialog.tsx")).contains("/admin/api/update/check");
        assertThat(read("frontend/src/features/system/VersionDialog.tsx")).contains("/admin/api/update/apply");
        assertThat(css).contains(".version-pill");
        assertThat(css).contains(".status-panel-meta");
        assertThat(app).contains("Gateway Console");
        assertThat(app).contains("网关运行中");
        assertThat(app).contains("路由总数");
        assertThat(app).contains("运行中");
        assertThat(app).contains("已停用");
        assertThat(app).contains("本分钟请求");
        assertThat(app).contains("平均延迟");
        // 卡片式路由列表：网格渲染 RouteCard，而不是表格
        assertThat(app).contains("route-card-board");
        assertThat(app).contains("<RouteCard");
        assertThat(app).doesNotContain("<Table");
        // 背景网络装饰已删除
        assertThat(app).doesNotContain("NetworkBackdrop");

        assertThat(css).contains(".app-shell");
        assertThat(css).contains(".app-sidebar");
        assertThat(css).contains(".app-topbar");
        assertThat(css).contains(".band");
        assertThat(css).contains(".band-bar");
        assertThat(css).contains(".nav-item-active");
        assertThat(css).contains(".status-panel");
        assertThat(css).contains(".app-canvas");
    }

    @Test
    void brandMarkAndFaviconUseRouteNodesInsteadOfGenericIcons() throws Exception {
        String favicon = read("frontend/public/favicon.svg");
        String app = read("frontend/src/App.tsx");

        assertThat(favicon).contains("<circle");
        assertThat(favicon).contains("#101F33");
        assertThat(favicon).contains("#8FC1FF");
        assertThat(favicon).doesNotContain("shield");
        assertThat(favicon).doesNotContain("cloud");

        assertThat(app).contains("RouteNodeMark");
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

    /** 侧边栏必须可收起，且收起后仍可达：导航项要有 title、切换按钮要有 aria 状态。 */
    @Test
    void sidebarSupportsCollapseWithAccessibleFallbacks() throws Exception {
        String app = read("frontend/src/App.tsx");
        String css = stylesheet();
        String prefs = read("frontend/src/lib/preferences.ts");

        // 状态与持久化
        assertThat(prefs).contains("export type SidebarMode = 'expanded' | 'collapsed'");
        assertThat(prefs).contains("SIDEBAR_STORAGE_KEY = 'wrouter.sidebar'");
        assertThat(prefs).contains("export function nextSidebarMode");
        assertThat(app).contains("sidebarMode");
        assertThat(app).contains("SIDEBAR_STORAGE_KEY");

        // 收起态类名与切换按钮
        assertThat(app).contains("app-shell-collapsed");
        assertThat(app).contains("sidebar-toggle");
        assertThat(app).contains("sidebarToggleLabel(mode)");
        assertThat(app).contains("aria-expanded={!collapsed}");

        // 切换按钮固定在侧栏最下方（.sidebar-footer），而不是品牌行
        assertThat(app).contains("sidebar-footer");
        assertThat(app).contains("sidebar-toggle-label");
        assertThat(css).contains(".sidebar-footer {");
        assertThat(css).contains(".sidebar-toggle-label");
        int footerAt = app.indexOf("sidebar-footer");
        int toggleAt = app.indexOf("sidebar-toggle");
        assertThat(toggleAt).as("切换按钮必须位于 sidebar-footer 之内").isGreaterThan(footerAt);
        // 品牌行不得再包含切换按钮
        int brandAt = app.indexOf("className=\"brand\"");
        assertThat(brandAt).isGreaterThan(0);
        assertThat(app.substring(brandAt, footerAt)).doesNotContain("sidebar-toggle");

        // 收起后文案隐藏，因此每个侧栏项都要有 title 兜底
        assertThat(app).contains("const tip = (label: string) => (collapsed ? label : undefined)");
        assertThat(app).contains("title={tip('路由管理')}");
        assertThat(app).contains("title={tip('请求日志')}");
        assertThat(app).contains("title={tip('配置目录')}");
        assertThat(app).contains("title={tip('导出配置')}");
        assertThat(app).contains("title={tip('导入配置')}");
        assertThat(app).contains("title={tip('关于')}");

        assertThat(css).contains(".sidebar-toggle");
        assertThat(css).contains(".app-shell-collapsed");
        assertThat(css).contains("--sidebar-width: 76px");
        assertThat(css).contains(".app-shell-collapsed .nav-text");
    }

    /** 概览带必须可收起：收起后压成一行摘要，且功能不丢失。 */
    @Test
    void overviewBandSupportsCollapseWithoutLosingFunction() throws Exception {
        String app = read("frontend/src/App.tsx");
        String css = stylesheet();
        String prefs = read("frontend/src/lib/preferences.ts");

        // 默认展开：收起是用户主动腾空间的手段
        assertThat(prefs).contains("export type BandMode = 'expanded' | 'collapsed'");
        assertThat(prefs).contains("export const DEFAULT_BAND_MODE: BandMode = 'expanded'");
        assertThat(prefs).contains("BAND_STORAGE_KEY = 'wrouter.band'");
        assertThat(prefs).contains("export function nextBandMode");
        assertThat(prefs).contains("export function bandToggleLabel");
        assertThat(app).contains("BAND_STORAGE_KEY");
        assertThat(app).contains("resolveBandMode");

        // 切换按钮必须有无障碍状态
        assertThat(app).contains("band-toggle");
        assertThat(app).contains("aria-expanded={!collapsed}");
        assertThat(app).contains("aria-controls=\"overview-band-body\"");
        assertThat(app).contains("bandToggleLabel(mode)");

        // 收起态是一行摘要，且三个筛选按钮仍然可点（收起不隐藏功能）
        assertThat(app).contains("band-collapsed");
        assertThat(app).contains("band-summary");
        assertThat(app).contains("band-summary-stat");
        assertThat(app).contains("aria-pressed={statusFilter === 'all'}");
        assertThat(app).contains("aria-pressed={statusFilter === 'enabled'}");
        assertThat(app).contains("aria-pressed={statusFilter === 'disabled'}");

        assertThat(css).contains(".band-toggle");
        assertThat(css).contains(".band-collapsed");
        assertThat(css).contains(".band-summary {");
        assertThat(css).contains(".band-summary-stat");
        assertThat(css).contains("height: 46px");
    }

    /**
     * 日志视图默认展示「全部路由」，不需要先选一条。
     * 作用域决定数据源，且全部路由模式不得被空态拦截。
     */
    @Test
    void logViewShowsAllRoutesByDefaultWithoutRequiringASelection() throws Exception {
        String app = read("frontend/src/App.tsx");
        String panel = read("frontend/src/features/logs/RouteLogDialog.tsx");

        // 默认作用域是「全部路由」
        assertThat(app).contains("全部路由（聚合所有日志）");
        assertThat(app).doesNotContain("请选择一条路由");
        assertThat(app).doesNotContain("全部路由（选择一条查看明细）");
        // 面板始终渲染，不再按 logRoute 分支
        assertThat(app).contains("<RouteLogPanel open route={logRoute} />");

        // 面板按作用域切换数据源
        assertThat(panel).contains("const scoped = Boolean(routeId)");
        assertThat(panel).contains("'/admin/api/proxy-logs'");
        assertThat(panel).contains("'/admin/api/proxy-logs/stream'");
        assertThat(panel).contains("'/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId!)");
        // 全部路由模式不要求路由启用
        assertThat(panel).contains("const streamAllowed = scoped ? Boolean(route?.enabled) : true");
        // 标题区分两种作用域
        assertThat(panel).contains("route?.name || '全部路由'");
    }

    /** 构建脚本必须做真实类型检查；根 tsconfig.json 是 references-only，直接 tsc 不会检查任何文件。 */
    @Test
    void buildScriptTypeChecksRealProjects() throws Exception {
        String pkg = read("frontend/package.json");

        assertThat(pkg).contains("tsc -p tsconfig.app.json --noEmit");
        assertThat(pkg).contains("tsc -p tsconfig.node.json --noEmit");
        assertThat(pkg).doesNotContain("\"typecheck\": \"tsc --noEmit\"");
    }
}
