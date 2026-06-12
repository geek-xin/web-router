package com.geek.webrouter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPageHeroGuideTest {

    @Test
    void heroUsesChunkyRouteConsoleLanguageAndNoEmojiIcons() throws Exception {
        String app = Files.readString(Path.of("frontend/src/App.tsx"));

        assertThat(app).contains("ChunkyHero");
        assertThat(app).contains("WEB ROUTER CONTROL");
        assertThat(app).contains("路径转发、监听端口、实时日志，一处管理。");
        assertThat(app).contains("JSON 配置");
        assertThat(app).contains("chunky-panel");
        assertThat(app).contains("FlowStep");
        assertThat(app).contains("lucide-react").doesNotContain("🚀").doesNotContain("🎨").doesNotContain("⚙️");
    }
}
