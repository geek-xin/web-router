package com.geek.webrouter.web.support;

import java.util.Locale;

/**
 * 更新脚本生成所需的平台抽象。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public enum UpdatePlatform {

    /** Linux。 */
    LINUX,

    /** macOS。 */
    MACOS,

    /** Windows。 */
    WINDOWS;

    /** 根据构建期平台标识解析平台，无法识别时回落到当前运行系统。 */
    public static UpdatePlatform resolve(String platform) {
        String normalized = platform == null ? "" : platform.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("windows")) {
            return WINDOWS;
        }
        if (normalized.startsWith("macos") || normalized.startsWith("darwin") || normalized.startsWith("mac")) {
            return MACOS;
        }
        if (normalized.startsWith("linux")) {
            return LINUX;
        }
        return current();
    }

    /** 当前运行系统对应的平台。 */
    public static UpdatePlatform current() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return WINDOWS;
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return MACOS;
        }
        return LINUX;
    }

    /** 该平台是否使用 Windows 批处理脚本。 */
    public boolean isWindows() {
        return this == WINDOWS;
    }
}
