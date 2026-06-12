package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageConfigPathTest {

    @Test
    void adminTemplateMountsReactAppAndPassesConfigDirectoryMetadata() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));
        String app = Files.readString(Path.of("frontend/src/App.tsx"));

        assertThat(template).contains("id=\"root\"");
        assertThat(template).contains("/admin/assets/app.css");
        assertThat(template).contains("/admin/assets/app.js");
        assertThat(template).contains("name=\"routes-config-dir\"");
        assertThat(template).contains("name=\"routes-config-dir-label\"");
        assertThat(template).doesNotContain("/css/style.css");
        assertThat(template).doesNotContain("/js/app.js");

        assertThat(app).contains("routes-config-dir");
        assertThat(app).contains("routes-config-dir-label");
        assertThat(app).contains("显示完整配置目录");
        assertThat(app).contains("隐藏完整配置目录");
    }

    @Test
    void chunkyThemeDefinesConsolePaletteAndAccessibleInteractions() throws Exception {
        String css = Files.readString(Path.of("frontend/src/styles.css"));

        assertThat(css).contains("chunky-panel");
        assertThat(css).contains("chunky-card-blue");
        assertThat(css).contains("chunky-card-yellow");
        assertThat(css).contains("chunky-card-mint");
        assertThat(css).contains("glass-card-blue");
        assertThat(css).contains("glass-card-gold");
        assertThat(css).contains("glass-card-green");
        assertThat(css).contains("--color-primary: #F45113");
        assertThat(css).contains("border-[3px]");
        assertThat(css).contains("shadow-clay");
        assertThat(css).contains("prefers-reduced-motion");
    }
}
