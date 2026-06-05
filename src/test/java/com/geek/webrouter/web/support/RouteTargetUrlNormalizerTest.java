package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTargetUrlNormalizerTest {

    @Test
    void addsHttpSchemeToIpAndPort() {
        assertThat(RouteTargetUrlNormalizer.normalize("192.168.1.100:8080"))
                .isEqualTo("http://192.168.1.100:8080");
    }

    @Test
    void addsHttpSchemeToDomainAndPort() {
        assertThat(RouteTargetUrlNormalizer.normalize("api.example.com:8080"))
                .isEqualTo("http://api.example.com:8080");
    }

    @Test
    void keepsExistingHttpOrHttpsScheme() {
        assertThat(RouteTargetUrlNormalizer.normalize("http://192.168.1.100:8080"))
                .isEqualTo("http://192.168.1.100:8080");
        assertThat(RouteTargetUrlNormalizer.normalize("https://api.example.com:8443"))
                .isEqualTo("https://api.example.com:8443");
    }

    @Test
    void trimsWhitespaceBeforeNormalizing() {
        assertThat(RouteTargetUrlNormalizer.normalize("  api.example.com:8080  "))
                .isEqualTo("http://api.example.com:8080");
    }
}
