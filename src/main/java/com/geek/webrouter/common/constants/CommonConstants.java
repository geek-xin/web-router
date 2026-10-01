package com.geek.webrouter.common.constants;

/**
 * 全局常量。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 应用根目录系统属性名。 */
    public static final String HOME_SYSTEM_PROPERTY = "wrouter.home";

    /** 应用根目录环境变量名。 */
    public static final String HOME_ENV_VARIABLE = "WROUTER_HOME";

    /** 配置目录名（相对于应用根目录）。 */
    public static final String CONFIG_DIR = "config";

    /** 路由配置子目录名（相对于配置目录）。 */
    public static final String ROUTES_DIR = "routes";

    /** 路由配置文件目录（相对于应用根目录）。 */
    public static final String ROUTES_CONFIG_DIR = CONFIG_DIR + "/" + ROUTES_DIR;

    /** 自动更新工作目录名（相对于应用根目录）。 */
    public static final String UPDATES_DIR = "updates";

    /** 路由配置文件名前缀。 */
    public static final String CONFIG_FILE_EXTENSION = ".json";
}
