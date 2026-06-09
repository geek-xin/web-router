package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageConfigPathTest {

    @Test
    void configDirectoryFullPathIsHiddenUntilUserTogglesIt() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/index.html"));
        String script = Files.readString(Path.of("src/main/resources/static/js/app.js"));

        assertThat(template).contains("id=\"btnToggleConfigPath\"");
        assertThat(template).contains("aria-controls=\"configPathFull\"");
        assertThat(template).contains("aria-expanded=\"false\"");
        assertThat(template).contains("id=\"configPathFull\"");
        assertThat(template).contains("hidden");
        assertThat(template).contains("class=\"status-path-toggle-icon status-path-toggle-icon-eye\"");
        assertThat(template).contains("class=\"status-path-toggle-icon status-path-toggle-icon-eye-off\"");

        assertThat(script).contains("btnToggleConfigPath: document.getElementById('btnToggleConfigPath')");
        assertThat(script).contains("configPathFull: document.getElementById('configPathFull')");
        assertThat(script).contains("elements.configPathFull.hidden = !willShow");
        assertThat(script).contains("elements.btnToggleConfigPath.setAttribute('aria-expanded', String(willShow))");
        assertThat(script).contains("elements.btnToggleConfigPath.setAttribute('aria-label', willShow ? '隐藏完整配置目录' : '显示完整配置目录')");
        assertThat(script).doesNotContain("elements.btnToggleConfigPath.textContent");

        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));
        String statusGridCss = css.substring(css.indexOf(".status-grid {"),
                css.indexOf(".status-card,", css.indexOf(".status-grid {")));
        assertThat(css).contains(".status-grid {\n    display: grid;\n    grid-template-columns: repeat(3, minmax(0, 1fr));\n    gap: 14px;");
        assertThat(statusGridCss).doesNotContain("align-items: start;");
        assertThat(css).contains(".status-card-file .status-path-full[hidden]");
        assertThat(css).contains("display: block;\n    visibility: hidden;\n    opacity: 0;\n    pointer-events: none;");
        assertThat(css).contains(".status-path-toggle-icon");
        assertThat(css).contains(".status-path-toggle[aria-expanded=\"true\"] .status-path-toggle-icon-eye");
        assertThat(css).doesNotContain(".status-path-toggle-pupil");
    }
}
