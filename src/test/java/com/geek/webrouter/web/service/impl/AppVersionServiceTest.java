package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.web.model.dto.AppVersionInfo;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionServiceTest {

    @Test
    void readsEveryFieldFromVersionProperties() {
        AppVersionInfo info = new AppVersionServiceImpl(properties("1.3.0", "jar", "unknown")).currentVersion();

        assertThat(info.version()).isEqualTo("1.3.0");
        assertThat(info.buildTime()).isEqualTo("2026-09-30T15:56:37Z");
        assertThat(info.platform()).isEqualTo("unknown");
        assertThat(info.installMode()).isEqualTo("jar");
        assertThat(info.updateSupported()).isTrue();
        assertThat(info.updateChannel()).isEqualTo("stable");
        assertThat(info.repository()).isEqualTo("geek-xin/web-router");
    }

    @Test
    void missingResourceFallsBackToSafeDefaults() {
        AppVersionInfo info = new AppVersionServiceImpl(new Properties()).currentVersion();

        assertThat(info.version()).isEqualTo("unknown");
        assertThat(info.buildTime()).isEmpty();
        assertThat(info.platform()).isEqualTo("unknown");
        assertThat(info.installMode()).isEqualTo("jar");
        assertThat(info.updateSupported()).isTrue();
        assertThat(info.updateChannel()).isEqualTo("stable");
        assertThat(info.repository()).isEmpty();
    }

    @Test
    void appImageUpdateSupportDependsOnThePlatformToken() {
        assertThat(AppVersionServiceImpl.updateSupported("app-image", "macos-arm64")).isTrue();
        assertThat(AppVersionServiceImpl.updateSupported("app-image", "linux-x64")).isTrue();
        assertThat(AppVersionServiceImpl.updateSupported("app-image", "unknown")).isFalse();
        assertThat(AppVersionServiceImpl.updateSupported("native", "macos-arm64")).isFalse();
    }

    @Test
    void onlyThePrereleaseChannelAcceptsPrereleaseBuilds() {
        assertThat(AppVersionServiceImpl.acceptsPrerelease("prerelease")).isTrue();
        assertThat(AppVersionServiceImpl.acceptsPrerelease("PRERELEASE")).isTrue();
        assertThat(AppVersionServiceImpl.acceptsPrerelease("stable")).isFalse();
        assertThat(AppVersionServiceImpl.acceptsPrerelease(null)).isFalse();
    }

    @Test
    void packagedClasspathResourceIsReadableAndCarriesAConcreteVersion() {
        AppVersionInfo info = new AppVersionServiceImpl().currentVersion();

        assertThat(info.version()).isNotBlank();
        assertThat(info.repository()).isEqualTo("geek-xin/web-router");
    }

    private static Properties properties(String version, String installMode, String platform) {
        Properties properties = new Properties();
        properties.setProperty("app.version", version);
        properties.setProperty("app.buildTime", "2026-09-30T15:56:37Z");
        properties.setProperty("app.platform", platform);
        properties.setProperty("app.installMode", installMode);
        properties.setProperty("app.updateChannel", "stable");
        properties.setProperty("app.repository", "geek-xin/web-router");
        return properties;
    }
}
