package com.geek.webrouter.web.support;

import java.util.Locale;

/**
 * 运行期平台识别 — 构建元信息缺省时的兜底。
 *
 * <p>打包脚本通过 {@code -Dwrouter.platform=...} 注入构建平台，但
 * {@code mvn package}、{@code spring-boot:run} 等直接构建的产物没有该值，
 * {@code version.properties} 中会留空。此时按 JVM 的 {@code os.name} /
 * {@code os.arch} 推导，避免管理后台把运行平台显示成「未识别平台」。</p>
 *
 * <p>jar 分发包本身与平台无关，运行时推导比把构建机平台写死更准确；
 * 返回的标识始终受 {@link ReleaseAssets#platformToken(String)} 约束，
 * 不会引入发布契约之外的新平台。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public final class RuntimePlatform {

    /** 无法识别时的占位值，与 {@code AppVersionServiceImpl.UNKNOWN} 语义一致。 */
    public static final String UNKNOWN = "unknown";

    private RuntimePlatform() {
    }

    /**
     * 当前运行平台的资产标识，形如 {@code macos-arm64}。
     *
     * @return {@link ReleaseAssets#platformToken(String)} 认可的标识；无法识别时为 {@code unknown}
     */
    public static String currentToken() {
        return token(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /** 由 OS 名与架构拼装平台标识。 */
    public static String token(String osName, String osArch) {
        String family = family(osName);
        String architecture = architecture(osArch);
        if (family == null || architecture == null) {
            return UNKNOWN;
        }
        String candidate = family + "-" + architecture;
        return ReleaseAssets.platformToken(candidate) == null ? UNKNOWN : candidate;
    }

    /**
     * 归一化操作系统族，未知返回 {@code null}。
     *
     * <p>macOS/Darwin 必须先于 Windows 判断：{@code "darwin"} 本身包含
     * {@code "win"}，顺序反了会把 Darwin 误判成 Windows。</p>
     */
    private static String family(String osName) {
        String name = normalize(osName);
        if (name.contains("mac") || name.contains("darwin")) {
            return "macos";
        }
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("linux")) {
            return "linux";
        }
        return null;
    }

    /** 归一化 CPU 架构，未知返回 {@code null}。 */
    private static String architecture(String osArch) {
        return switch (normalize(osArch)) {
            case "aarch64", "arm64" -> "arm64";
            case "x86_64", "amd64", "x64" -> "x64";
            default -> null;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
