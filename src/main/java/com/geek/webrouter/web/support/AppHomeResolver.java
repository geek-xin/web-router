package com.geek.webrouter.web.support;

import com.geek.webrouter.common.constants.CommonConstants;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用根目录解析器。
 *
 * <p>解析顺序：系统属性 {@code wrouter.home} → 环境变量 {@code WROUTER_HOME} → 当前工作目录
 * {@code user.dir}。三者都不可用时退化为相对路径，保证「相对启动目录」的默认行为不变。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public final class AppHomeResolver {

    /** 未显式配置根目录时使用的相对兜底路径。 */
    private static final String DEFAULT_HOME = ".";

    private AppHomeResolver() {
    }

    /** 解析应用根目录。 */
    public static Path resolveHome() {
        return resolveHome(System.getProperty(CommonConstants.HOME_SYSTEM_PROPERTY),
                System.getenv(CommonConstants.HOME_ENV_VARIABLE),
                System.getProperty("user.dir"));
    }

    /** 解析应用根目录，候选值按优先级依次尝试。 */
    public static Path resolveHome(String systemPropertyValue, String environmentValue, String workingDirectory) {
        Path home = firstUsable(systemPropertyValue, environmentValue, workingDirectory);
        return home == null ? Paths.get(DEFAULT_HOME) : home;
    }

    /** 解析路由配置目录：{@code <home>/config/routes}。 */
    public static Path resolveRoutesConfigDir() {
        return resolveHome().resolve(CommonConstants.CONFIG_DIR).resolve(CommonConstants.ROUTES_DIR);
    }

    /** 解析自动更新工作目录：{@code <home>/updates}。 */
    public static Path resolveUpdatesDir() {
        return resolveHome().resolve(CommonConstants.UPDATES_DIR);
    }

    private static Path firstUsable(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return Paths.get(candidate.trim());
            }
        }
        return null;
    }
}
