package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppHomeResolverTest {

    @Test
    void systemPropertyWinsOverEnvironmentAndWorkingDirectory() {
        Path home = AppHomeResolver.resolveHome("/opt/wrouter", "/env/wrouter", "/work");

        assertThat(home).isEqualTo(Path.of("/opt/wrouter"));
    }

    @Test
    void environmentVariableIsUsedWhenSystemPropertyIsBlank() {
        assertThat(AppHomeResolver.resolveHome("  ", "/env/wrouter", "/work"))
                .isEqualTo(Path.of("/env/wrouter"));
        assertThat(AppHomeResolver.resolveHome(null, "/env/wrouter", "/work"))
                .isEqualTo(Path.of("/env/wrouter"));
    }

    @Test
    void workingDirectoryIsTheFallbackAndRelativeBehaviourIsPreserved() {
        assertThat(AppHomeResolver.resolveHome(null, null, "/work")).isEqualTo(Path.of("/work"));
        assertThat(AppHomeResolver.resolveHome(null, null, null)).isEqualTo(Path.of("."));
        assertThat(AppHomeResolver.resolveHome(null, null, null)
                .resolve("config").resolve("routes").normalize()).isEqualTo(Path.of("config/routes"));
    }

    @Test
    void resolvesConfigAndUpdatesDirectoriesUnderTheHome() {
        assertThat(Path.of("/opt/wrouter").resolve("config").resolve("routes").toString())
                .isEqualTo("/opt/wrouter/config/routes");
        assertThat(Path.of("/opt/wrouter").resolve("updates").toString())
                .isEqualTo("/opt/wrouter/updates");
    }
}
