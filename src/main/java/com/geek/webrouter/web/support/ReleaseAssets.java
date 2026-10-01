package com.geek.webrouter.web.support;

import java.util.List;
import java.util.Locale;

/**
 * 发布资产命名规则 — 与打包脚本、CI 约定保持一致。
 *
 * <p>jar 形态：{@code wrouter-<version>-jar.zip}；app-image 形态：
 * {@code wrouter-<version>-<platform>-app.zip}（Linux 为 {@code .tar.gz}）。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public final class ReleaseAssets {

    /** jar 安装形态。 */
    private static final String INSTALL_MODE_JAR = "jar";

    /** app-image 安装形态。 */
    private static final String INSTALL_MODE_APP_IMAGE = "app-image";

    /** 资产文件名前缀。 */
    private static final String ASSET_PREFIX = "wrouter-";

    /** Linux app-image 资产后缀（tar.gz）。 */
    private static final String LINUX_APP_SUFFIX = "-app.tar.gz";

    /** 通用 app-image 资产后缀。 */
    private static final String APP_SUFFIX = "-app.zip";

    private ReleaseAssets() {
    }

    /** 资产文件名基础部分：{@code wrouter-<version>}。 */
    public static String assetBaseName(String version) {
        return ASSET_PREFIX + normalizeVersion(version);
    }

    /**
     * 按安装形态、版本与平台返回期望的资产文件名，顺序即优先级。
     *
     * @return 期望文件名列表；安装形态或平台无法判断时返回 {@code null}
     */
    public static List<String> expectedAssetNames(String installMode, String version, String platform) {
        String base = assetBaseName(version);
        String mode = normalizeMode(installMode);
        if (INSTALL_MODE_JAR.equals(mode)) {
            return List.of(base + "-jar.zip");
        }
        if (!INSTALL_MODE_APP_IMAGE.equals(mode)) {
            return null;
        }
        String token = platformToken(platform);
        if (token == null) {
            return null;
        }
        return List.of(base + "-" + token + appSuffix(token));
    }

    /** 当前安装形态与平台是否支持自动更新。 */
    public static boolean supportsPlatform(String installMode, String platform) {
        String mode = normalizeMode(installMode);
        if (INSTALL_MODE_JAR.equals(mode)) {
            return true;
        }
        return INSTALL_MODE_APP_IMAGE.equals(mode) && platformToken(platform) != null;
    }

    /** 平台标识（资产名中的中段），无法识别时返回 {@code null}。 */
    public static String platformToken(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "macos-arm64", "macos-x64", "linux-x64", "linux-arm64", "windows-x64" -> normalized;
            default -> null;
        };
    }

    /** app-image 资产的扩展名后缀。 */
    public static String appSuffix(String platformToken) {
        return platformToken != null && platformToken.startsWith("linux") ? LINUX_APP_SUFFIX : APP_SUFFIX;
    }

    /** 规范化版本号：去掉前缀 v/V 与空白。 */
    public static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String normalizeMode(String installMode) {
        return installMode == null ? "" : installMode.trim().toLowerCase(Locale.ROOT);
    }
}
