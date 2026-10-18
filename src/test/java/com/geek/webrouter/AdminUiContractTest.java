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
        // 拓扑流动：虚线沿链路方向平移（stroke-dashoffset），表达方向且不违反动效禁令
        assertThat(css).contains("@keyframes topology-flow");
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

    /** 版本抽屉必须提供历史版本与修订记录，且折叠/当前版本标记有明确实现。 */
    @Test
    void versionDrawerExposesReleaseHistoryWithRevisionNotes() throws Exception {
        String dialog = read("frontend/src/features/system/VersionDialog.tsx");
        String utils = read("frontend/src/features/system/version-utils.ts");
        String css = stylesheet();

        assertThat(dialog).contains("历史版本与修订记录");
        assertThat(dialog).contains("/admin/api/update/history");
        assertThat(dialog).contains("normalizeReleaseHistory");
        assertThat(dialog).contains("release-history");
        assertThat(dialog).contains("aria-expanded");
        // 当前版本与预发布都必须有文字标记，不能只靠颜色
        assertThat(dialog).contains("当前");
        assertThat(dialog).contains("预览");
        // 折叠态用首行摘要，展开态给全文
        assertThat(utils).contains("releaseNotesPreview");
        assertThat(utils).contains("formatReleaseDate");
        assertThat(utils).contains("historySummary");

        assertThat(css).contains(".release-history {");
        assertThat(css).contains(".release-history-row-current");
        assertThat(css).contains(".release-history-chevron-open");
        assertThat(css).contains(".release-history-notes");
    }

    /**
     * 顶栏铃铛必须是「更新日志」入口，而不是纯装饰按钮。
     *
     * <p>历史缺陷：顶栏只有一个带红点的静态 `<button aria-label="通知">`，
     * 点了没有任何反应，红点也无法解释——用户无法知道「最新一版改了什么」。</p>
     */
    @Test
    void topbarBellShowsLatestChangelogWithUnreadMarker() throws Exception {
        String app = read("frontend/src/App.tsx");
        String bell = read("frontend/src/features/system/ChangelogBell.tsx");
        String utils = read("frontend/src/features/system/changelog-utils.ts");
        String css = stylesheet();

        // 顶栏接入的是真实组件，不再有静态通知按钮
        assertThat(app).contains("<ChangelogBell");
        assertThat(app).doesNotContain("aria-label=\"通知\"");

        assertThat(bell).contains("更新日志");
        assertThat(bell).contains("/admin/api/update/history");
        assertThat(bell).contains("changelogEntries");
        assertThat(bell).contains("hasUnreadChangelog");
        assertThat(bell).contains("markChangelogSeen");
        // 未读点复用 icon-button-dot，未读时才加，不能常亮
        assertThat(bell).contains("icon-button-dot");
        // 只读入口：不得触发下载或重启
        assertThat(bell).doesNotContain("/admin/api/update/apply");

        assertThat(utils).contains("CHANGELOG_SEEN_STORAGE_KEY");
        assertThat(utils).contains("compareChangelogVersions");
        assertThat(utils).contains("latestChangelogEntry");

        assertThat(css).contains(".changelog-bell {");
        assertThat(css).contains(".changelog-pop {");
        assertThat(css).contains(".changelog-item-current");
        assertThat(css).contains(".changelog-chevron-open");
        assertThat(css).contains(".changelog-notes");
    }

    /**
     * 操作列必须容得下全部按钮，否则会溢出并压到相邻列/卡片外。
     *
     * <p>历史缺陷：列表视图操作列写死 250px（6 个按钮约 325px），
     * 配合 justify-content:flex-end + nowrap 向左溢出，压住曲线列；
     * 卡片视图 4 列布局（≥1720px）同理溢出到卡片外。</p>
     */
    @Test
    void actionColumnsFitTheirButtonsWithoutOverflow() throws Exception {
        String css = stylesheet();

        // 列表操作列用共用变量给出确定宽度，而不是内容相关的 max-content：
        // 表头与数据行是独立 grid 容器，max-content 会各算各的导致整表错位。
        assertThat(css).contains("--list-actions-column:");
        assertThat(css).contains("var(--list-actions-column)");
        assertThat(css).doesNotContain("minmax(250px, max-content)");
        // 卡片操作行允许换行兜底
        assertThat(css).contains(".route-card-actions {");
        assertThat(css).contains("flex-wrap: wrap");
        // 4 列布局下按钮退化为纯图标，避免换行撑高卡片被裁
        assertThat(css).contains("@media (min-width: 1720px)");
    }

    /**
     * 列表视图不展示 Path 列：路由名下方的行为摘要已表达前缀语义，
     * 完整前缀由卡片视图与详情承担。
     */
    @Test
    void routeListViewOmitsThePathColumn() throws Exception {
        String list = read("frontend/src/features/routes/RouteList.tsx");
        String css = stylesheet();

        // 表头与行里都不再有 Path 单元格
        assertThat(list).doesNotContain("route-list-paths");
        assertThat(list).doesNotContain(">Path<");
        // 样式表里也不应残留只服务于该列的规则
        assertThat(css).doesNotContain(".route-list-paths");

        // 列表仍然保留路由名与行为摘要（Path 语义的替代）
        assertThat(list).contains("route-list-name");
        assertThat(list).contains("routeBehaviorSummary");

        // 列数必须与 6 个单元格匹配：路由 / Target·端口 / 请求 / 延迟 / 曲线 / 操作
        assertThat(css).contains("minmax(0, 1.6fr)");
        assertThat(css).contains("minmax(0, 1.1fr)");
        // ≤1100px 的网格区域不再声明 paths
        assertThat(css).doesNotContain("'paths targets'");
    }

    /**
     * 拓扑必须是实时画面：流动速度由真实流量驱动，且不违反 §7 的动效禁令。
     */
    @Test
    void topologyFlowReflectsLiveTrafficWithoutBannedEffects() throws Exception {
        String topology = read("frontend/src/features/routes/RouteTopology.tsx");
        String css = stylesheet();
        String design = read("docs/UI-DESIGN-SYSTEM.md");

        // 组件接收实时指标，并把它折算成流动周期 + 色调
        assertThat(topology).contains("traffic?: RouteTrafficMetrics | null");
        assertThat(topology).contains("describeFlow");
        assertThat(topology).contains("requestsLastMinute");
        assertThat(topology).contains("failedLastMinute");
        assertThat(topology).contains("--flow-duration");

        // 流动用 SVG 虚线平移实现（天然表达方向），不是靠移动一个色块
        assertThat(topology).contains("stroke-dashoffset");
        assertThat(css).contains("@keyframes topology-flow");
        assertThat(css).contains("stroke-dasharray");

        // 三种实时状态都要有对应样式
        assertThat(css).contains(".topology-flow-live");
        assertThat(css).contains(".topology-flow-error");
        assertThat(css).contains(".topology-flow-idle");
        // 失败请求转告警色
        assertThat(css).contains("--surface-warning");

        // §7 禁令：不得用渐变 / 缩放 / 旋转做流动效果
        assertThat(topology).doesNotContain("linear-gradient");
        String linkRules = css.substring(css.indexOf(".topology-link {"), css.indexOf(".topology-fallback {"));
        assertThat(linkRules).doesNotContain("scale(");
        assertThat(linkRules).doesNotContain("rotate(");
        assertThat(linkRules).doesNotContain("linear-gradient");

        // 宽屏必须保留**可见**的横向连线，而不是整段 display:none。
        // 只断言类名存在是不够的——把 display 改回 none 也能通过，
        // 所以这里取 .detail-overview 段落，校验横线/右箭头是 block、竖线/下箭头是 none。
        // 定位「宽屏横线可见」这条规则块：从选择器到其后第一个 } 为止
        int horizontalRuleStart = css.indexOf(".detail-overview .topology-link-svg-h,");
        assertThat(horizontalRuleStart)
                .as("必须存在宽屏横向连线的显示规则")
                .isGreaterThanOrEqualTo(0);
        int horizontalRuleEnd = css.indexOf("}", horizontalRuleStart);
        String horizontalRule = css.substring(horizontalRuleStart, horizontalRuleEnd);
        assertThat(horizontalRule)
                .as("宽屏的横向连线必须是可见的（早先 display:none 导致宽屏完全看不到流动方向）")
                .contains(".topology-link-arrow-right")
                .contains("display: block")
                .doesNotContain("display: none");
        assertThat(css).contains("minmax(0, 1fr) 26px");

        // 文档记录实时语义与分档
        assertThat(design).contains("拓扑是实时画面");
        assertThat(design).contains("最近一分钟请求");
    }
    /**
     * 所有下拉必须走统一的 Radix Select，不得使用原生 select 元素。
     *
     * <p>原生下拉的展开列表由操作系统绘制，CSS 无法作用于它——触发框是设计系统的
     * 白底圆角、展开列表却是系统灰底直角，同一界面出现两套视觉语言，深浅色也不跟随。
     * 这条断言防止原生 select 回流。</p>
     */
    @Test
    void everyDropdownUsesTheUnifiedRadixSelect() throws Exception {
        String selectComponent = read("frontend/src/components/ui/select.tsx");
        String css = stylesheet();
        String implDoc = read("docs/UI-IMPLEMENTATION.md");
        String designDoc = read("docs/UI-DESIGN-SYSTEM.md");

        // 统一组件基于 Radix，并导出选项模型
        assertThat(selectComponent).contains("@radix-ui/react-select");
        assertThat(selectComponent).contains("export interface SelectOption");
        assertThat(selectComponent).contains("onValueChange");

        // 三个调用点都必须改用统一组件
        for (String file : new String[]{
                "frontend/src/features/routes/RouteToolbar.tsx",
                "frontend/src/features/logs/RouteLogDialog.tsx",
                "frontend/src/App.tsx"}) {
            String source = read(file);
            assertThat(source)
                    .as("%s 不得再使用原生 select 元素", file)
                    .doesNotContain("<select");
            assertThat(source)
                    .as("%s 应从统一组件导入 Select", file)
                    .contains("from '@/components/ui/select'");
        }

        // input.tsx 不再导出原生 Select
        assertThat(read("frontend/src/components/ui/input.tsx")).doesNotContain("export const Select");

        // 弹层样式与文档都在位
        assertThat(css).contains(".select-trigger");
        assertThat(css).contains(".select-content");
        assertThat(css).contains(".select-item");
        // 图标与文案重叠的修复：search-field 内的下拉必须让开左侧图标
        assertThat(css).contains(".search-field .select-trigger");
        assertThat(implDoc).contains(".select-trigger");
        assertThat(designDoc).contains("展开列表由操作系统绘制");
    }
    /**
     * 运行平台只展示系统名（macOS / Linux / Windows），不带 CPU 架构。
     * 「macOS · Apple Silicon」这类文案过长会挤占抽屉与侧栏，
     * 而架构对使用者没有决策价值（更新包由后端按 platform 标识自动选）。
     */
    @Test
    void platformLabelShowsOnlyTheOsName() throws Exception {
        String utils = read("frontend/src/features/system/version-utils.ts");
        String design = read("docs/UI-DESIGN-SYSTEM.md");

        // 契约标识映射到纯系统名
        assertThat(utils).contains("'macos-arm64': 'macOS'");
        assertThat(utils).contains("'macos-x64': 'macOS'");
        assertThat(utils).contains("'linux-x64': 'Linux'");
        assertThat(utils).contains("'windows-x64': 'Windows'");
        // 不再拼接架构后缀：断言标签映射本身，而不是全文件搜字符串
        // （' · ' 在历史版本/表格等其它助手里有合法用途）
        String labelMap = utils.substring(utils.indexOf("const PLATFORM_LABELS"),
                utils.indexOf("const PLATFORM_FAMILIES"));
        // 值必须是纯系统名：既没有架构词，也没有 "·" 拼接
        // （键里出现 x64 是契约标识本身，属正常）
        assertThat(labelMap).doesNotContain("Apple Silicon");
        assertThat(labelMap).doesNotContain("Intel");
        assertThat(labelMap).doesNotContain("ARM64");
        assertThat(labelMap).doesNotContain("' · '");
        // 架构映射表已删除
        assertThat(utils).doesNotContain("PLATFORM_ARCHITECTURES");
        // 文档同步记录该约定
        assertThat(design).contains("不展示 CPU 架构");
    }

    @Test
    void routeDetailRendersInPageAndLeadsWithTopology() throws Exception {
        String detail = read("frontend/src/features/routes/RouteDetailView.tsx");
        String app = read("frontend/src/App.tsx");
        String topology = read("frontend/src/features/routes/RouteTopology.tsx");
        String css = stylesheet();

        // 详情是页面内视图：渲染为 surface-panel 分节，不再有遮罩与 aria-modal 抽屉语义
        assertThat(detail).contains("route-detail-view");
        assertThat(detail).contains("surface-panel");
        assertThat(detail).doesNotContain("drawer-scrim");
        assertThat(detail).doesNotContain("aria-modal");
        // App 必须直接渲染 RouteDetailView，且不再引用抽屉组件
        assertThat(app).contains("<RouteDetailView");
        assertThat(app).doesNotContain("RouteDetailDrawer");
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("frontend/src/features/routes/RouteDetailDrawer.tsx")))
                .as("抽屉组件应已被页内详情取代")
                .isFalse();

        // 拓扑现在是实时画面：除 idle 外还要接实时流量，用于驱动流动速度与告警色
        assertThat(detail).contains("<RouteTopology model={topology} idle={!route.enabled} traffic={traffic} />");
        assertThat(detail).contains("路由拓扑");
        assertThat(detail).contains("基本信息");
        assertThat(detail).contains("运行状态");
        assertThat(detail).contains("最近日志");
        assertThat(detail).contains("section-index");
        assertThat(detail).contains("detail-actions");
        assertThat(detail).contains("编辑");
        assertThat(detail).contains("访问");
        assertThat(detail).contains("日志");
        assertThat(detail).contains("删除");
        assertThat(detail).contains("formatJsonForDisplay");
        assertThat(detail).contains("编辑 JSON");
        // 底部操作栏：默认「编辑」，进入配置页后原位换成「保存」，文案不带「配置」。
        assertThat(detail).contains("const editing = tab === 'config'");
        assertThat(detail).contains("showActions={false}");
        assertThat(detail).contains("form={DETAIL_FORM_ID}");
        assertThat(detail).doesNotContain("保存配置");
        // 保存按钮排在「访问」之前，且只在配置态渲染（默认进入详情看不到保存）。
        assertThat(detail.indexOf("{saving ? '保存中…' : '保存'}")).isLessThan(detail.indexOf("onAccess(route)"));
        assertThat(read("frontend/src/components/ui/textarea.tsx")).contains("json-editor");
        // 概览首屏必须是拓扑：拓扑在概览标签页内先于基本信息出现
        assertThat(detail.indexOf("路由拓扑")).isLessThan(detail.indexOf("基本信息"));
        assertThat(detail.indexOf("基本信息")).isLessThan(detail.indexOf("运行状态"));
        assertThat(detail.indexOf("运行状态")).isLessThan(detail.indexOf("最近日志"));
        // 可视化：概览两列栅格 + 指标卡 + 曲线
        assertThat(css).contains(".route-detail-view");
        assertThat(css).contains(".detail-overview");
        assertThat(css).contains(".detail-metric-row");
        assertThat(css).contains(".detail-spark");

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
        // 顶栏的「Gateway 运行中」胶囊已移除，状态文案改由侧栏运行卡表达
        assertThat(app).doesNotContain("Gateway 运行中");
        assertThat(app).contains("网关运行中");
        assertThat(app).contains("GATEWAY_ENDPOINT");
        assertThat(read("frontend/src/lib/app-meta.ts")).contains("127.0.0.1:9999");
        // 版本号显示与更新入口
        assertThat(app).contains("VersionDialog");
        assertThat(app).contains("/admin/api/version");
        assertThat(read("frontend/src/features/system/VersionDialog.tsx")).contains("/admin/api/update/check");
        assertThat(read("frontend/src/features/system/VersionDialog.tsx")).contains("/admin/api/update/apply");
        // 版本号与 Gateway 状态统一放在左侧菜单栏的运行卡：
        // 顶栏只保留全局操作（搜索/主题/通知），不再有状态胶囊与版本胶囊
        assertThat(app).contains("status-panel-meta");
        assertThat(app).doesNotContain("version-pill");
        assertThat(app).doesNotContain("state-pill");
        assertThat(css).contains(".status-panel-meta");
        assertThat(css).doesNotContain(".version-pill");
        assertThat(css).doesNotContain(".state-pill");
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

        // 默认作用域是「全部路由」：现在由 Select 的 options 表达（label + hint 两段）
        assertThat(app).contains("{ value: '', label: '全部路由', hint: '聚合所有日志' }");
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
