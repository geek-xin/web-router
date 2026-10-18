package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行期平台识别的契约：结果必须落在发布资产认可的平台矩阵内。
 */
class RuntimePlatformTest {

    @Test
    void mapsEverySupportedOsAndArchitectureCombination() {
        assertThat(RuntimePlatform.token("Mac OS X", "aarch64")).isEqualTo("macos-arm64");
        assertThat(RuntimePlatform.token("Mac OS X", "x86_64")).isEqualTo("macos-x64");
        assertThat(RuntimePlatform.token("Darwin", "arm64")).isEqualTo("macos-arm64");
        assertThat(RuntimePlatform.token("Linux", "amd64")).isEqualTo("linux-x64");
        assertThat(RuntimePlatform.token("Linux", "aarch64")).isEqualTo("linux-arm64");
        assertThat(RuntimePlatform.token("Windows 11", "amd64")).isEqualTo("windows-x64");
    }

    /** "darwin" 含子串 "win"，判定顺序反了会把 macOS 认成 Windows。 */
    @Test
    void darwinIsNotMistakenForWindows() {
        assertThat(RuntimePlatform.token("Darwin", "arm64")).isEqualTo("macos-arm64");
        assertThat(RuntimePlatform.token("darwin", "x86_64")).isEqualTo("macos-x64");
    }

    @Test
    void toleratesCaseAndPaddingFromJvmProperties() {
        assertThat(RuntimePlatform.token("  MAC OS X  ", " ARM64 ")).isEqualTo("macos-arm64");
        assertThat(RuntimePlatform.token("windows server 2022", "x86_64")).isEqualTo("windows-x64");
    }

    /** 认不出系统或架构时必须回落到 unknown，而不是编造一个契约外的标识。 */
    @Test
    void unrecognisedOsOrArchitectureDegradesToUnknown() {
        assertThat(RuntimePlatform.token("SunOS", "sparc")).isEqualTo(RuntimePlatform.UNKNOWN);
        assertThat(RuntimePlatform.token("Linux", "ppc64le")).isEqualTo(RuntimePlatform.UNKNOWN);
        assertThat(RuntimePlatform.token(null, null)).isEqualTo(RuntimePlatform.UNKNOWN);
        assertThat(RuntimePlatform.token("", "")).isEqualTo(RuntimePlatform.UNKNOWN);
    }

    /** 推导结果必须始终是 ReleaseAssets 认可的标识，否则更新会选不到资产。 */
    @Test
    void detectedTokenIsAlwaysAcceptedByTheReleaseContract() {
        String[][] combinations = {
                {"Mac OS X", "aarch64"}, {"Mac OS X", "x86_64"}, {"Darwin", "arm64"},
                {"Linux", "amd64"}, {"Linux", "aarch64"}, {"Windows 11", "amd64"},
                {"SunOS", "sparc"}, {"Linux", "ppc64le"},
        };

        for (String[] combination : combinations) {
            String token = RuntimePlatform.token(combination[0], combination[1]);
            if (!RuntimePlatform.UNKNOWN.equals(token)) {
                assertThat(ReleaseAssets.platformToken(token))
                        .as("推导出的 %s 必须被发布契约接受", token)
                        .isEqualTo(token);
            }
        }
    }

    /** 当前 JVM 上必须能识别出一个具体平台，不能是 unknown。 */
    @Test
    void currentJvmResolvesToAConcretePlatform() {
        String token = RuntimePlatform.currentToken();

        assertThat(token).isNotEqualTo(RuntimePlatform.UNKNOWN);
        assertThat(ReleaseAssets.platformToken(token)).isEqualTo(token);
    }
}
