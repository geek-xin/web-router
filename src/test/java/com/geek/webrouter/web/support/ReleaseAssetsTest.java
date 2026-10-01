package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseAssetsTest {

    @Test
    void jarInstallModeAlwaysUsesTheJarZipAsset() {
        assertThat(ReleaseAssets.expectedAssetNames("jar", "1.4.0", "unknown"))
                .containsExactly("wrouter-1.4.0-jar.zip");
        assertThat(ReleaseAssets.expectedAssetNames("jar", "v1.4.0", "macos-arm64"))
                .containsExactly("wrouter-1.4.0-jar.zip");
        assertThat(ReleaseAssets.supportsPlatform("jar", "unknown")).isTrue();
    }

    @Test
    void appImageAssetsFollowTheFrozenPlatformMatrix() {
        assertThat(ReleaseAssets.expectedAssetNames("app-image", "1.4.0", "macos-arm64"))
                .containsExactly("wrouter-1.4.0-macos-arm64-app.zip");
        assertThat(ReleaseAssets.expectedAssetNames("app-image", "1.4.0", "macos-x64"))
                .containsExactly("wrouter-1.4.0-macos-x64-app.zip");
        assertThat(ReleaseAssets.expectedAssetNames("app-image", "1.4.0", "linux-x64"))
                .containsExactly("wrouter-1.4.0-linux-x64-app.tar.gz");
        assertThat(ReleaseAssets.expectedAssetNames("app-image", "1.4.0", "windows-x64"))
                .containsExactly("wrouter-1.4.0-windows-x64-app.zip");
    }

    @Test
    void unknownPlatformOrInstallModeCannotBeUpdated() {
        assertThat(ReleaseAssets.expectedAssetNames("app-image", "1.4.0", "unknown")).isNull();
        assertThat(ReleaseAssets.expectedAssetNames("app-image", "1.4.0", null)).isNull();
        assertThat(ReleaseAssets.expectedAssetNames("native", "1.4.0", "linux-x64")).isNull();
        assertThat(ReleaseAssets.supportsPlatform("app-image", "unknown")).isFalse();
        assertThat(ReleaseAssets.supportsPlatform("unknown", "linux-x64")).isFalse();
        assertThat(ReleaseAssets.supportsPlatform("app-image", "linux-x64")).isTrue();
    }
}
