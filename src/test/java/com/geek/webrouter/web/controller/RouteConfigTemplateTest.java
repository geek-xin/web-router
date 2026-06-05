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
}
