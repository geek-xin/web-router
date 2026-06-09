package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageHeroGuideTest {

    @Test
    void heroGuideArrowsUseOutlinedShapeConsistentWithCards() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));

        assertThat(css).contains(".guide-line::before");
        assertThat(css).contains(".guide-line::after");
        assertThat(css).contains("border-left: 12px solid var(--color-border)");
        assertThat(css).contains("--color-guide-arrow: #5b9aa8");
        assertThat(css).contains("border-left: 9px solid var(--color-guide-arrow)");
        assertThat(css).contains("background: var(--color-guide-arrow)");
        assertThat(css).contains("filter: drop-shadow(2px 2px 0 rgba(24, 38, 61, 0.12))");
    }
}
