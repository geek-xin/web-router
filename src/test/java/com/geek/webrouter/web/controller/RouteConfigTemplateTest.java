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
    void routeFormPlacesAccessPageWithListenerFieldsAndProxyAddressWithDefaultAddress() {
        String html = routeFormTemplate();

        assertThat(html).contains("<div class=\"form-row form-row-listener\">");
        assertThat(html).contains("<label for=\"localIp\">监听 IP</label>");
        assertThat(html).contains("<input type=\"text\" id=\"localIp\" value=\"127.0.0.1\" readonly aria-readonly=\"true\"");
        assertThat(html).contains("默认 127.0.0.1");
        assertThat(html).contains("<label for=\"localPort\">监听端口 <span class=\"required\">*</span></label>");
        assertThat(html).contains("<input type=\"text\" id=\"localPort\" required inputmode=\"numeric\"");
        assertThat(html).contains("<label for=\"accessPage\">访问页</label>");
        assertThat(html).contains("<input type=\"text\" id=\"accessPage\"");
        assertThat(html).contains("可选访问入口");
        assertThat(html).contains("<div class=\"form-row form-row-default\">");
        assertThat(html).contains("<label for=\"targetUrl\">默认地址（兜底） <span class=\"required\">*</span></label>");
        assertThat(html).contains("未匹配前缀时使用");
        assertThat(html).contains("<label for=\"accessPageBaseUrl\">代理地址</label>");
        assertThat(html).contains("<input type=\"text\" id=\"accessPageBaseUrl\" placeholder=\"如 127.0.0.1:9999\">");
        assertThat(html).contains("匹配路径前缀时使用");
        assertThat(html).doesNotContain("<div class=\"form-row form-row-proxy\">");
        assertThat(html).doesNotContain("<div class=\"form-row form-row-access-page\">");
        assertThat(html.indexOf("<label for=\"localPort\">监听端口 <span class=\"required\">*</span></label>"))
                .isLessThan(html.indexOf("<label for=\"accessPage\">访问页</label>"));
        assertThat(html.indexOf("<div class=\"form-row form-row-listener\">"))
                .isLessThan(html.indexOf("<div class=\"form-row form-row-default\">"));
        assertThat(html.indexOf("<label for=\"targetUrl\">默认地址（兜底） <span class=\"required\">*</span></label>"))
                .isLessThan(html.indexOf("<label for=\"accessPageBaseUrl\">代理地址</label>"));
        assertThat(html).doesNotContain("id=\"localBinding\"");
        assertThat(html).doesNotContain("id=\"proxyAddress\"");
        assertThat(html).doesNotContain("留空则不启用本地端口访问");
    }

    @Test
    void routeFormIncludesOptionalAccessPageInput() {
        String html = routeFormTemplate();

        assertThat(html).contains("<input type=\"text\" id=\"name\" required maxlength=\"50\"");
        assertThat(html).contains("<label for=\"accessPage\">访问页</label>");
        assertThat(html).contains("<input type=\"text\" id=\"accessPage\"");
        assertThat(html).contains("可选访问入口");
        assertThat(html).doesNotContain("<label for=\"accessPage\">访问页 <span class=\"required\">*</span></label>");
    }


    @Test
    void routeFormShowsPathPrefixesAsOptional() throws Exception {
        String html = routeFormTemplate();
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(html).contains("<label for=\"pathPrefixInput\">路径前缀</label>");
        assertThat(html).doesNotContain("<label for=\"pathPrefixInput\">路径前缀 <span class=\"required\">*</span></label>");
        assertThat(html).contains("匹配前缀走代理地址，未匹配走默认地址");
        assertThat(html).doesNotContain("不填写时，本地端口会代理全部路径");
        assertThat(script).contains("未配置路径前缀，请求走默认地址");
        assertThat(script).contains("匹配前缀走代理地址，未匹配走默认地址");
        assertThat(script).doesNotContain("请输入路径前缀");
    }

    @Test
    void routeCardLabelsPathPrefixesAsPathPrefixes() {
        RouteConfig config = RouteConfig.builder()
                .id("route-gateway-entry")
                .name("演示环境")
                .pathPrefixes(List.of("/realTimeMonitor"))
                .targetUrl("http://210.21.52.71:9080")
                .accessPageBaseUrl("http://127.0.0.1:9999")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of("/realTimeMonitor"));

        String html = renderIndex(List.of(config));

        assertThat(html.indexOf("<span>监听地址</span>"))
                .isLessThan(html.indexOf("<span>默认地址（兜底）</span>"));
        assertThat(html.indexOf("<span>默认地址（兜底）</span>"))
                .isLessThan(html.indexOf("<span>代理地址</span>"));
        assertThat(html).contains("<span>代理地址</span>");
        assertThat(html).contains("<strong title=\"http://127.0.0.1:9999\">127.0.0.1:9999</strong>");
        assertThat(html).contains("<span>路径前缀</span>");
        assertThat(html).contains("<code title=\"/realTimeMonitor\">/realTimeMonitor</code>");
        assertThat(html).doesNotContain("<span>Gateway 入口</span>");
    }

    @Test
    void routeCardIncludesAccessButtonForOpeningAccessPage() {
        RouteConfig config = RouteConfig.builder()
                .id("route-access")
                .name("访问页路由")
                .pathPrefixes(List.of("/portal"))
                .targetUrl("http://127.0.0.1:8081")
                .accessPageBaseUrl("http://127.0.0.1:18080")
                .accessPage("/portal/login.html")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of("/portal"));

        String html = renderIndex(List.of(config));

        assertThat(html).contains("data-access-page-base-url=\"http://127.0.0.1:18080\"");
        assertThat(html).contains("data-access-page=\"/portal/login.html\"");
        assertThat(html).contains("data-target-url=\"http://127.0.0.1:8081\"");
        assertThat(html).contains("data-local-access=\"127.0.0.1:9191\"");
        assertThat(html).contains("class=\"btn btn-sm btn-access\"");
        assertThat(html).contains(">访问</button>");
    }

    @Test
    void routeAccessScriptUsesListenerBindingForAccessPage() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(script).contains("function routeAccessPath(routeId)");
        assertThat(script).contains("const binding = (card.dataset.localBinding || '').trim();");
        assertThat(script).contains("if (binding) {\n            return 'http://' + binding + path;");
        assertThat(script).contains("请先启用路由并填写监听端口和访问页");
        assertThat(script).doesNotContain("const targetUrl = normalizedAbsoluteUrl(card.dataset.targetUrl || card.dataset.target)");
        assertThat(script).doesNotContain("return appendPathToBaseUrl(targetUrl, path)");
        assertThat(script).doesNotContain("function isConfiguredAccessPath(path, routeId)");
        assertThat(script).doesNotContain("appendPathToBaseUrl(accessPageBaseUrl, configuredAccessPage)");
    }

    @Test
    void routeFormScriptSerializesSeparateListenerFieldsIntoLocalBindingPayload() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(script).contains("localIp: document.getElementById('localIp')");
        assertThat(script).contains("localPort: document.getElementById('localPort')");
        assertThat(script).contains("accessPageBaseUrl: document.getElementById('accessPageBaseUrl')");
        assertThat(script).contains("localIp: normalizedOptionalText(elements.localIp.value)");
        assertThat(script).contains("localPort: localPort ? Number(localPort) : null");
        assertThat(script).contains("accessPageBaseUrl: normalizedOptionalText(elements.accessPageBaseUrl.value)");
        assertThat(script).contains("配置路径前缀时请输入代理地址");
        assertThat(script).contains("监听 IP 格式不正确，如 127.0.0.1 或 localhost");
        assertThat(script).contains("监听端口需为 1-65535 的整数");
        assertThat(script).doesNotContain("function parseLocalBinding(value)");
        assertThat(script).doesNotContain("document.getElementById('localBinding')");
    }

    @Test
    void routeCardAccessButtonCanUseFirstConfiguredPrefixWhenAccessPageIsBlank() {
        RouteConfig config = RouteConfig.builder()
                .id("route-prefix-entry")
                .name("前缀入口")
                .pathPrefixes(List.of("/realTimeMonitor"))
                .targetUrl("http://210.21.52.71:9080")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(true)
                .build();
        config.setEffectivePathPrefixes(List.of("/realTimeMonitor"));

        String html = renderIndex(List.of(config));

        assertThat(html).contains("data-local-access=\"127.0.0.1:9191\"");
        assertThat(html).contains("title=\"新标签页打开访问页\"");
        assertThat(html).doesNotContain("data-id=\"route-prefix-entry\" disabled=\"disabled\"");
    }

    @Test
    void routeCardAccessButtonRequiresEnabledLocalListener() {
        RouteConfig config = RouteConfig.builder()
                .id("route-disabled-entry")
                .name("停用入口")
                .pathPrefixes(List.of("/portal"))
                .targetUrl("http://127.0.0.1:8081")
                .accessPageBaseUrl("http://127.0.0.1:9999")
                .accessPage("/portal")
                .localIp("127.0.0.1")
                .localPort(9191)
                .enabled(false)
                .build();
        config.setEffectivePathPrefixes(List.of("/portal"));

        String html = renderIndex(List.of(config));

        assertThat(html).contains("data-local-access=\"127.0.0.1:9191\"");
        assertThat(html).doesNotContain("data-local-binding=");
        assertThat(html).contains("data-id=\"route-disabled-entry\" disabled=\"disabled\" title=\"请先启用路由并填写监听端口\"");
    }


    @Test
    void copiedRouteDefaultsToDisabledInEditor() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(script).contains("elements.enabled.value = 'false';");
        assertThat(script).doesNotContain("elements.enabled.value = String(cfg.enabled === true);");
    }

    @Test
    void routeLogTablesReserveFirstColumnForRowNumber() {
        String html = routeLogTablesTemplate();

        assertThat(html).contains("<th class=\"col-index\">序号</th>\n                                <th>路径</th>");
        assertThat(html).contains("<th>最长单次</th>");
        assertThat(html).contains("<td colspan=\"5\" class=\"empty-small\">暂无请求</td>");
        assertThat(html).contains("<th class=\"col-index\">序号</th>\n                                <th>时间</th>");
        assertThat(html).contains("<th>方法</th>\n                                <th>实际访问</th>\n                                <th>路径</th>");
        assertThat(html).contains("<td colspan=\"8\" class=\"empty-small\">暂无代理请求</td>");
    }


    @Test
    void routeLogTablesShowActualAccessAddressBeforePath() throws Exception {
        String html = routeLogTablesTemplate();
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(html).contains("<th>方法</th>\n                                <th>实际访问</th>\n                                <th>路径</th>");
        assertThat(html).contains("<th>方法</th>\n                                    <th>实际访问</th>\n                                    <th>路径</th>");
        assertThat(script).contains("displayLogDetailValue(entry.accessAddress)");
        assertThat(script).contains("cell(accessAddress, 'access-cell', accessAddress === '-' ? '' : accessAddress)");
        assertThat(script).contains("'实际访问: ' + displayLogDetailValue(entry.accessAddress)");
        assertThat(script).contains("routeLogDetailAccessAddress");
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

        assertThat(html).contains("停用中，不监听代理端口");
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
        assertThat(html).contains("data-enabled=\"false\" disabled=\"disabled\" title=\"请先编辑路由并填写监听端口后再启用\"");
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
