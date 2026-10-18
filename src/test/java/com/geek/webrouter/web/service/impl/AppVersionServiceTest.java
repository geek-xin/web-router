package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.web.model.dto.AppVersionInfo;
import com.geek.webrouter.web.support.RuntimePlatform;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionServiceTest {

    @Test
    void readsEveryFieldFromVersionProperties() {
        AppVersionInfo info = new AppVersionServiceImpl(properties("1.3.0", "jar", "macos-arm64")).currentVersion();

        assertThat(info.version()).isEqualTo("1.3.0");
        assertThat(info.buildTime()).isEqualTo("2026-09-30T15:56:37Z");
        assertThat(info.platform()).isEqualTo("macos-arm64");
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
        assertThat(info.installMode()).isEqualTo("jar");
        assertThat(info.updateSupported()).isTrue();
        assertThat(info.updateChannel()).isEqualTo("stable");
        assertThat(info.repository()).isEmpty();
    }

    /**
     * 直接 mvn package / spring-boot:run 的产物没有 -Dwrouter.platform，
     * 平台不能停在 unknown，否则后台显示「未识别平台」。
     */
    @Test
    void blankPlatformIsResolvedFromTheRunningJvm() {
        AppVersionInfo info = new AppVersionServiceImpl(properties("1.3.0", "jar", "")).currentVersion();

        assertThat(info.platform()).isEqualTo(RuntimePlatform.currentToken());
        assertThat(info.platform()).isNotEqualTo(AppVersionServiceImpl.UNKNOWN);
    }

    @Test
    void unknownPlatformIsAlsoResolvedFromTheRunningJvm() {
        AppVersionInfo info = new AppVersionServiceImpl(properties("1.3.0", "jar", "unknown")).currentVersion();

        assertThat(info.platform()).isEqualTo(RuntimePlatform.currentToken());
    }

    /** 构建元信息里的合法标识优先于运行期推导，发布契约不被改写。 */
    @Test
    void configuredPlatformWinsOverRuntimeDetection() {
        assertThat(AppVersionServiceImpl.resolvePlatform("linux-arm64")).isEqualTo("linux-arm64");
        assertThat(AppVersionServiceImpl.resolvePlatform("windows-x64")).isEqualTo("windows-x64");
        assertThat(AppVersionServiceImpl.resolvePlatform(" macos-x64 ")).isEqualTo("macos-x64");
    }

    @Test
    void unusablePlatformFallsBackToRuntimeDetection() {
        String runtime = RuntimePlatform.currentToken();

        assertThat(AppVersionServiceImpl.resolvePlatform("")).isEqualTo(runtime);
        assertThat(AppVersionServiceImpl.resolvePlatform(null)).isEqualTo(runtime);
        assertThat(AppVersionServiceImpl.resolvePlatform("unknown")).isEqualTo(runtime);
        assertThat(AppVersionServiceImpl.resolvePlatform("solaris-sparc")).isEqualTo(runtime);
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
