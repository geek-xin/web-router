package com.geek.webrouter.web.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void effectivePathPrefixesUsesLegacyPathPrefixWhenPathPrefixesIsMissing() throws Exception {
        RouteConfig config = objectMapper.readValue("""
                {
                  "id": "route-a",
                  "name": "Route A",
                  "pathPrefix": "/legacy",
                  "targetUrl": "http://127.0.0.1:8080",
                  "enabled": true
                }
                """, RouteConfig.class);

        assertThat(config.effectivePathPrefixes()).containsExactly("/legacy");
    }

    @Test
    void effectivePathPrefixesUsesPathPrefixesWhenPresent() throws Exception {
        RouteConfig config = objectMapper.readValue("""
                {
                  "id": "route-a",
                  "name": "Route A",
                  "pathPrefix": "/legacy",
                  "pathPrefixes": ["/api", "/admin"],
                  "targetUrl": "http://127.0.0.1:8080",
                  "enabled": true
                }
                """, RouteConfig.class);

        assertThat(config.effectivePathPrefixes()).containsExactly("/api", "/admin");
    }

    @Test
    void setEffectivePathPrefixesKeepsFirstPrefixAsLegacyPathPrefix() {
        RouteConfig config = new RouteConfig();

        config.setEffectivePathPrefixes(List.of("/api", "/admin"));

        assertThat(config.getPathPrefix()).isEqualTo("/api");
        assertThat(config.getPathPrefixes()).containsExactly("/api", "/admin");
    }
}
