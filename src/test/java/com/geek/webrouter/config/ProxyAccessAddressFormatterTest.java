package com.geek.webrouter.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyAccessAddressFormatterTest {

    @Test
    void formatsHostAndPortWithDefaults() {
        assertThat(ProxyAccessAddressFormatter.hostPort(URI.create("http://127.0.0.1:9191/path")))
                .isEqualTo("127.0.0.1:9191");
        assertThat(ProxyAccessAddressFormatter.hostPort(URI.create("http://example.com/path")))
                .isEqualTo("example.com:80");
        assertThat(ProxyAccessAddressFormatter.hostPort(URI.create("https://example.com/path")))
                .isEqualTo("example.com:443");
    }

    @Test
    void formatsAccessAddressAsHostAndPortOnly() {
        assertThat(ProxyAccessAddressFormatter.accessAddress(
                URI.create("http://127.0.0.1:9191"),
                "/portal/notice/heartbeat?show=1"
        )).isEqualTo("127.0.0.1:9191");
        assertThat(ProxyAccessAddressFormatter.accessAddress("127.0.0.1:9191", "portal/messageHandler"))
                .isEqualTo("127.0.0.1:9191");
        assertThat(ProxyAccessAddressFormatter.accessAddress("127.0.0.1:9191", "?show=1"))
                .isEqualTo("127.0.0.1:9191");
        assertThat(ProxyAccessAddressFormatter.accessAddress("127.0.0.1:9191", ""))
                .isEqualTo("127.0.0.1:9191");
    }
}
