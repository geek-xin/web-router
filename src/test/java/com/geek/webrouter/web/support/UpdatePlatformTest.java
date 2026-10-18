package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台解析的边界：{@code "darwin"} 包含 {@code "win"}，判定顺序不能反。
 */
class UpdatePlatformTest {

    @Test
    void darwinResolvesToMacosNotWindows() {
        assertThat(UpdatePlatform.of("Darwin")).isEqualTo(UpdatePlatform.MACOS);
        assertThat(UpdatePlatform.of("Mac OS X")).isEqualTo(UpdatePlatform.MACOS);
        assertThat(UpdatePlatform.of("darwin")).isEqualTo(UpdatePlatform.MACOS);
    }

    @Test
    void windowsAndLinuxResolveToTheirOwnFamily() {
        assertThat(UpdatePlatform.of("Windows 11")).isEqualTo(UpdatePlatform.WINDOWS);
        assertThat(UpdatePlatform.of("Windows Server 2022")).isEqualTo(UpdatePlatform.WINDOWS);
        assertThat(UpdatePlatform.of("Linux")).isEqualTo(UpdatePlatform.LINUX);
    }

    /** 认不出的系统按 Unix 处理，生成 shell 脚本而不是 .bat。 */
    @Test
    void unrecognisedSystemsFallBackToUnix() {
        assertThat(UpdatePlatform.of("SunOS")).isEqualTo(UpdatePlatform.LINUX);
        assertThat(UpdatePlatform.of("FreeBSD")).isEqualTo(UpdatePlatform.LINUX);
        assertThat(UpdatePlatform.of(null)).isEqualTo(UpdatePlatform.LINUX);
        assertThat(UpdatePlatform.of("")).isEqualTo(UpdatePlatform.LINUX);
    }

    @Test
    void buildTimePlatformTokenWinsAndDarwinIsNotWindows() {
        assertThat(UpdatePlatform.resolve("windows-x64")).isEqualTo(UpdatePlatform.WINDOWS);
        assertThat(UpdatePlatform.resolve("darwin")).isEqualTo(UpdatePlatform.MACOS);
    }
}
