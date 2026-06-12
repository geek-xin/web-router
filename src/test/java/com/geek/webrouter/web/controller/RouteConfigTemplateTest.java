package com.geek.webrouter.web.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigTemplateTest {

    @Test
    void routeFormKeepsListenerDefaultProxyAndPathPrefixContracts() throws Exception {
        String form = Files.readString(Path.of("frontend/src/features/routes/RouteFormDialog.tsx"));
        String utils = Files.readString(Path.of("frontend/src/features/routes/route-utils.ts"));

        assertThat(form).contains("监听 IP");
        assertThat(form).contains("readOnly aria-readonly=\"true\"");
        assertThat(form).contains("监听端口 *");
        assertThat(form).contains("访问页");
        assertThat(form).contains("默认地址（兜底） *");
        assertThat(form).contains("代理地址");
        assertThat(form).contains("访问监听地址时，命中前缀走代理地址，否则走默认地址");
        assertThat(form).contains("未配置路径前缀，请求走默认地址");
        assertThat(utils).contains("配置路径前缀时请输入代理地址");
        assertThat(utils).doesNotContain("请输入路径前缀");
    }

    @Test
    void routeCardAccessButtonUsesActiveListenerBindingForRelativeAccessPages() throws Exception {
        String card = Files.readString(Path.of("frontend/src/features/routes/RouteCard.tsx"));
        String utils = Files.readString(Path.of("frontend/src/features/routes/route-utils.ts"));

        assertThat(card).contains("activeLocalBinding(route)");
        assertThat(card).contains("请先启用路由并填写监听端口和访问页");
        assertThat(card).contains("新标签页打开访问页");
        assertThat(utils).contains("return 'http://' + binding + (path.startsWith('/') ? path : '/' + path)");
        assertThat(utils).doesNotContain("appendPathToBaseUrl(accessPageBaseUrl");
    }

    @Test
    void routeCardDisplaysListenerDefaultProxyPrefixesAndConfigFile() throws Exception {
        String card = Files.readString(Path.of("frontend/src/features/routes/RouteCard.tsx"));

        assertThat(card.indexOf("监听地址")).isLessThan(card.indexOf("默认地址（兜底）"));
        assertThat(card.indexOf("默认地址（兜底）")).isLessThan(card.indexOf("代理地址"));
        assertThat(card).contains("路径前缀");
        assertThat(card).contains("配置文件");
        assertThat(card).contains("停用中，不监听代理端口");
    }

    @Test
    void copiedRouteDefaultsToDisabledInEditor() throws Exception {
        String form = Files.readString(Path.of("frontend/src/features/routes/RouteFormDialog.tsx"));

        assertThat(form).contains("enabled: mode === 'copy' ? false : next.enabled");
        assertThat(form).contains("nextCopyName");
    }
}
