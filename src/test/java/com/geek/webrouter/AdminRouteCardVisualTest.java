package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRouteCardVisualTest {

    @Test
    void routeCardsUseUnifiedToneAndPrefixesUseRotatingColorMarkers() throws Exception {
        String card = Files.readString(Path.of("frontend/src/features/routes/RouteCard.tsx"));
        String form = Files.readString(Path.of("frontend/src/features/routes/RouteFormDialog.tsx"));
        String utils = Files.readString(Path.of("frontend/src/features/routes/route-utils.ts"));
        String css = Files.readString(Path.of("frontend/src/styles.css"));

        assertThat(card).contains("routeCardToneClass(route, index)");
        assertThat(card).contains("prefixToneClass(index)");
        assertThat(card).contains("route-prefix-chip-interactive");
        assertThat(card).contains("route-card-title-text min-w-0 truncate text-xl");
        assertThat(form).contains("prefixToneClass(index)");
        assertThat(utils).contains("return 'route-card-tone-pink';");
        assertThat(utils).doesNotContain("ROUTE_CARD_TONE_CLASSES");

        assertThat(css).contains(".route-card-tone-pink");
        assertThat(css).contains(".route-prefix-chip-blue");
        assertThat(css).contains(".route-prefix-chip-mint");
        assertThat(css).contains(".route-prefix-chip-yellow");
        assertThat(css).contains(".route-prefix-chip-pink");
    }

    @Test
    void routeDetailFlowSitsBesideEditPanelJsonMovesToEditableTabAndHeaderHidesRouteId() throws Exception {
        String detail = Files.readString(Path.of("frontend/src/features/routes/RouteDetailDrawer.tsx"));
        String css = Files.readString(Path.of("frontend/src/styles.css"));

        assertThat(detail).contains("route-detail-tabs-bar");
        assertThat(detail).contains("route-detail-tabs mt-5");
        assertThat(detail).contains("className=\"route-detail-tab-content\"");
        assertThat(detail).contains("route-detail-tabs-list");
        assertThat(detail).contains("route-detail-tab-trigger");
        assertThat(detail).contains("route-detail-edit-layout");
        assertThat(detail).contains("<RouteTopologyGraph route={route} values={liveValues} />");
        assertThat(detail).contains("route-detail-json-layout");
        assertThat(detail).doesNotContain("<RouteTopologyGraph route={route} values={liveValues} compact />");
        assertThat(detail).contains("value=\"json\">JSON 配置");
        assertThat(detail).contains("route-detail-json-panel");
        assertThat(detail).contains("编辑 JSON");
        assertThat(detail).contains("保存 JSON");
        assertThat(detail).contains("formatJsonText");
        assertThat(detail).contains("formatJsonForDisplay");
        assertThat(detail).contains("jsonToPayload");
        assertThat(detail).contains("route-topology-compact");
        assertThat(detail).doesNotContain(">自动排版</Button>");
        assertThat(detail).doesNotContain("<Button size=\"sm\" variant=\"primary\" onClick={startJsonEdit}");
        assertThat(detail).doesNotContain("<Button size=\"sm\" variant=\"orange\" onClick={() => void saveJsonDraft()}");
        assertThat(detail).contains("<Button variant=\"outline\" onClick={() => { setJsonEditing(false); setJsonError(''); }}>取消</Button>");
        assertThat(detail).doesNotContain("<p className=\"mt-1 truncate text-sm font-bold text-clay-muted\" title={route.id}>{route.id}</p>");
        assertThat(detail.indexOf("<TabsList")).isLessThan(detail.indexOf("<TabsContent className=\"route-detail-tab-content\" value=\"edit\">"));
        assertThat(detail.indexOf("<RouteFormPanel")).isLessThan(detail.indexOf("<RouteTopologyGraph route={route} values={liveValues} />"));
        assertThat(detail.indexOf("<TabsContent className=\"route-detail-tab-content\" value=\"json\">")).isLessThan(detail.indexOf("route-detail-json-panel"));

        assertThat(css).contains(".route-detail-tabs-bar");
        assertThat(css).contains("--route-detail-tab-content-min-height");
        assertThat(css).contains("--route-detail-tab-content-min-height: 48rem;");
        assertThat(css).contains(".route-detail-tab-content");
        assertThat(css).contains("min-height: var(--route-detail-tab-content-min-height)");
        assertThat(css).contains(".route-detail-tab-content > .route-detail-edit-layout");
        assertThat(css).contains(".route-detail-tab-content > .route-detail-json-layout");
        assertThat(css).contains(".route-detail-tabs-list");
        assertThat(css).contains(".route-detail-tab-trigger");
        assertThat(css).contains(".route-detail-edit-layout .topology-map");
        assertThat(css).contains(".route-detail-json-layout");
        assertThat(detail).contains("route-detail-edit-layout route-detail-json-layout");
        assertThat(css).contains("minmax(26rem, 32rem)");
        assertThat(css).contains("align-items: stretch");
        assertThat(css).contains(".route-detail-json-layout {\n      grid-template-columns: minmax(0, 1fr) minmax(26rem, 32rem);\n      align-items: stretch;");
        assertThat(css).contains(".route-detail-edit-layout > section");
        assertThat(css).contains(".route-detail-json-panel");
        assertThat(css).contains(".route-detail-json-layout > section");
        assertThat(css).contains(".route-detail-json-panel {\n      display: flex;\n      height: 100%;");
        assertThat(css).contains(".route-detail-json-editor");
        assertThat(detail).contains("route-detail-json-editor min-h-[560px]");
        assertThat(css).contains(".topology-card:hover");
        assertThat(css).contains(".topology-router-card:hover");
        assertThat(css).contains(".topology-branch-target:hover");
        assertThat(css).contains(".route-prefix-chip:hover");
        assertThat(css).contains(".route-prefix-chip-interactive:hover");
        assertThat(css).contains("translateY(-0.3rem) scale(1.06)");
        assertThat(css).contains(".topology-branch-tag");
        assertThat(css).contains("grid-template-columns: 1.35rem minmax(0, max-content)");
        assertThat(css).contains("--topology-branch-line-left: calc(0.525rem - 1.5px)");
        assertThat(css).contains("left: var(--topology-branch-line-left)");
        assertThat(css).doesNotContain("left: 0.68rem");
        assertThat(css).contains(".route-topology-compact");
        assertThat(css).contains(".topology-icon,\n  .topology-router-icon");
        assertThat(css).contains("display: inline-flex;");
        assertThat(css).contains(".topology-card:hover .topology-icon");
        assertThat(css).contains(".topology-router-card:hover .topology-router-icon");
        assertThat(css).contains(".topology-branch-target:hover .topology-icon");
        assertThat(css).contains("rotate(-7deg) scale(1.08)");
    }
}
