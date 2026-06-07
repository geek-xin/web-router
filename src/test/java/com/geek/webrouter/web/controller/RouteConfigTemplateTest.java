package com.geek.webrouter.web.controller;

import com.geek.webrouter.web.model.entity.RouteConfig;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigTemplateTest {

    @Test
    void routeFormLocksDefaultLocalIpAndRequiresLocalPort() {
        String html = routeFormTemplate();

        assertThat(html).contains("<label for=\"localIp\">本地 IP</label>");
        assertThat(html).contains("<input type=\"text\" id=\"localIp\" value=\"127.0.0.1\" readonly");
        assertThat(html).contains("<label for=\"localPort\">本地端口 <span class=\"required\">*</span></label>");
        assertThat(html).contains("<input type=\"number\" id=\"localPort\" min=\"1\" max=\"65535\" required");
        assertThat(html).doesNotContain("留空则不启用本地端口访问");
    }

    @Test
    void routeFormIncludesOptionalAccessPageInput() {
        String html = routeFormTemplate();

        assertThat(html).contains("<input type=\"text\" id=\"name\" required maxlength=\"50\"");
        assertThat(html).contains("<label for=\"accessPage\">访问页</label>");
        assertThat(html).contains("<input type=\"text\" id=\"accessPage\"");
        assertThat(html).doesNotContain("<label for=\"accessPage\">访问页 <span class=\"required\">*</span></label>");
    }

    @Test
    void routeCardIncludesAccessButtonForOpeningAccessPage() {
        RouteConfig config = RouteConfig.builder()
                .id("route-access")
                .name("访问页路由")
                .pathPrefixes(List.of("/portal"))
                .targetUrl("http://127.0.0.1:8081")
                .accessPage("/portal/login.html")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of("/portal"));

        String html = renderIndex(List.of(config));

        assertThat(html).contains("data-access-page=\"/portal/login.html\"");
        assertThat(html).contains("data-local-access=\"127.0.0.1:9191\"");
        assertThat(html).contains("class=\"btn btn-sm btn-access\"");
        assertThat(html).contains(">访问</button>");
    }

    @Test
    void routeLogTablesReserveFirstColumnForRowNumber() {
        String html = routeLogTablesTemplate();

        assertThat(html).contains("<th class=\"col-index\">序号</th>\n                                <th>路径</th>");
        assertThat(html).contains("<th>最长单次</th>");
        assertThat(html).contains("<td colspan=\"5\" class=\"empty-small\">暂无请求</td>");
        assertThat(html).contains("<th class=\"col-index\">序号</th>\n                                <th>时间</th>");
        assertThat(html).contains("<td colspan=\"7\" class=\"empty-small\">暂无代理请求</td>");
    }

    @Test
    void routeLogSearchControlUsesPlaceholderOnlyAndCompactCloseButton() {
        String html = routeLogTablesTemplate();

        assertThat(html).contains("<label class=\"log-search-control log-search-control-compact\" for=\"routeLogPathSearch\">");
        assertThat(html).doesNotContain(">\n                            路径\n                            <input id=\"routeLogPathSearch\"");
        assertThat(html).contains("class=\"btn btn-sm modal-close-btn\"");
    }

    @Test
    void routeLogModalIncludesDiagnosticTabForSelectedRequestAnalysis() {
        String html = routeLogTablesTemplate();

        assertThat(html).contains("data-route-log-tab=\"diagnostics\">诊断分析</button>");
        assertThat(html).contains("id=\"routeLogDiagnosticsPanel\"");
        assertThat(html).contains("id=\"routeLogDiagnosticEmpty\"");
        assertThat(html).contains("id=\"routeLogDiagnosticRows\"");
        assertThat(html).contains("<th>详情</th>");
        assertThat(html).doesNotContain("REQUEST DIAGNOSTICS");
        assertThat(html).doesNotContain("已复制请求列表");
    }

    @Test
    void routeLogSummaryDoesNotRenderUniqueIpMetric() {
        String html = routeLogSummaryTemplate();

        assertThat(html).doesNotContain("独立 IP");
        assertThat(html).doesNotContain("routeLogUniqueIps");
        assertThat(html).contains("请求数");
        assertThat(html).contains("失败请求");
        assertThat(html).contains("慢请求");
        assertThat(html).contains("成功率");
    }

    @Test
    void disabledRouteWithLocalPortShowsThatLocalProxyIsNotListening() {
        RouteConfig config = RouteConfig.builder()
                .id("route-disabled")
                .name("演示环境")
                .pathPrefixes(List.of("/iotmgr"))
                .targetUrl("http://210.21.52.71:9080")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(false)
                .build();
        config.setEffectivePathPrefixes(List.of("/iotmgr"));

        String html = renderIndex(List.of(config));

        assertThat(html).contains("停用中，不监听本地端口");
        assertThat(html).doesNotContain("<strong title=\"127.0.0.1:9191\">127.0.0.1:9191</strong>");
        assertThat(html).doesNotContain("data-local-binding=\"127.0.0.1:9191\"");
    }

    @Test
    void disabledRouteWithoutLocalPortCannotBeEnabledFromRouteCard() {
        RouteConfig config = RouteConfig.builder()
                .id("route-without-local-port")
                .name("未配置端口")
                .pathPrefixes(List.of("/missing-port"))
                .targetUrl("http://127.0.0.1:8081")
                .enabled(false)
                .build();
        config.setEffectivePathPrefixes(List.of("/missing-port"));

        String html = renderIndex(List.of(config));

        assertThat(html).contains("data-has-local-port=\"false\"");
        assertThat(html).contains("data-enabled=\"false\" disabled=\"disabled\" title=\"请先编辑路由并填写本地端口后再启用\"");
    }

    private String renderIndex(List<RouteConfig> configs) {
        Context context = new Context();
        context.setVariable("configs", configs);
        context.setVariable("assetVersion", 1L);
        return templateEngine().process(routeCardsTemplate(), context);
    }

    private SpringTemplateEngine templateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private String routeCardsTemplate() {
        try {
            String html = Files.readString(Path.of("src/main/resources/templates/index.html"));
            int start = html.indexOf("<div id=\"routeCards\"");
            int end = html.indexOf("        </section>", start);
            return html.substring(start, end);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取路由卡片模板片段", e);
        }
    }

    private String routeFormTemplate() {
        try {
            String html = Files.readString(Path.of("src/main/resources/templates/index.html"));
            int start = html.indexOf("<form id=\"routeForm\">");
            int end = html.indexOf("        </form>", start);
            return html.substring(start, end);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取路由表单模板片段", e);
        }
    }

    private String routeLogSummaryTemplate() {
        try {
            String html = Files.readString(Path.of("src/main/resources/templates/index.html"));
            int start = html.indexOf("<div class=\"log-summary\">");
            int end = html.indexOf("            <div class=\"log-card route-log-tabs-card\">", start);
            return html.substring(start, end);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取路由日志统计模板片段", e);
        }
    }

    private String routeLogTablesTemplate() {
        try {
            String html = Files.readString(Path.of("src/main/resources/templates/index.html"));
            int start = html.indexOf("<div class=\"log-card route-log-tabs-card\">");
            int end = html.indexOf("        <div id=\"routeLogDetailPanel\"", start);
            return html.substring(start, end);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取路由日志表格模板片段", e);
        }
    }
}
