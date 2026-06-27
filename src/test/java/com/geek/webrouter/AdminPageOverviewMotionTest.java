package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageOverviewMotionTest {

    @Test
    void overviewStatusCardsExposeMouseMotionStyles() throws Exception {
        String app = Files.readString(Path.of("frontend/src/App.tsx"));
        String css = Files.readString(Path.of("frontend/src/styles.css"));

        assertThat(app).contains("overview-stat-card");
        assertThat(app).contains("overview-stat-value");
        assertThat(app).contains("overview-stat-icon");

        assertThat(css).contains(".overview-stat-card::after");
        assertThat(css).contains(".overview-stat-card:hover .overview-stat-value");
        assertThat(css).contains(".overview-stat-card:hover .overview-stat-icon");
        assertThat(css).contains(".overview-stat-card:active");
    }

    @Test
    void configDirectoryCardHasMotionToggleAndStableHeight() throws Exception {
        String app = Files.readString(Path.of("frontend/src/App.tsx"));
        String css = Files.readString(Path.of("frontend/src/styles.css"));

        assertThat(app).contains("overview-config-card");
        assertThat(app).contains("overview-stat-icon overview-config-toggle");
        assertThat(app).contains("config-path-value-slot");
        assertThat(app).contains("data-config-path-visible");
        assertThat(app).contains("showConfigPath ? <EyeOff");
        assertThat(app).contains("隐藏配置目录绝对路径");
        assertThat(app).contains("显示配置目录绝对路径");

        assertThat(css).contains(".overview-config-card");
        assertThat(css).contains(".overview-config-toggle");
        assertThat(css).contains("width: 3.375rem");
        assertThat(css).contains("height: 3.375rem");
        assertThat(css).contains(".overview-stat-card:hover .overview-stat-icon");
        assertThat(css).contains(".overview-config-card:hover .config-path-value");
        assertThat(css).contains(".config-path-value-slot");
        assertThat(css).contains("height: 1.65rem");
        assertThat(css).contains("min-height: 1.65rem");
        assertThat(css).contains("height: 7.25rem");
        assertThat(css).contains("min-height: 7.25rem");
    }

    @Test
    void overviewCardsFilterRoutesByStatusWithoutToolbarFilterButtons() throws Exception {
        String app = Files.readString(Path.of("frontend/src/App.tsx"));
        String css = Files.readString(Path.of("frontend/src/styles.css"));
        String toolbar = Files.readString(Path.of("frontend/src/features/routes/RouteToolbar.tsx"));

        assertThat(app).contains("type RouteFilter = 'enabled' | 'disabled' | 'all'");
        assertThat(app).contains("React.useState<RouteFilter>('enabled')");
        assertThat(app).contains("routeFilter === 'enabled'");
        assertThat(app).contains("routeFilter === 'disabled'");
        assertThat(app).contains("onClick={() => setRouteFilter('all')}");
        assertThat(app).contains("onClick={() => setRouteFilter('enabled')}");
        assertThat(app).contains("onClick={() => setRouteFilter('disabled')}");
        assertThat(app).contains("当前筛选条件");
        assertThat(app).contains("overview-stat-label-row");
        assertThat(app).contains("overview-filter-badge");
        assertThat(app).contains("当前筛选");
        assertThat(css).contains(".overview-stat-label-row");
        assertThat(css).contains("flex-wrap: nowrap");
        assertThat(css).contains("white-space: nowrap");
        assertThat(css).contains(".overview-filter-badge");

        assertThat(toolbar).doesNotContain("routeFilter");
        assertThat(toolbar).doesNotContain("route-filter-group");
        assertThat(toolbar).doesNotContain("全部路由");
        assertThat(toolbar).doesNotContain("启用路由");
        assertThat(toolbar).doesNotContain("停用路由");
    }
}
