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

    /** 路由配置文件目录（相对于工作目录）。 */
    public static final String ROUTES_CONFIG_DIR = "config/routes";

    /** 默认代理端口。 */
    public static final int DEFAULT_PORT = 8080;

    /** 路由配置文件名前缀。 */
    public static final String CONFIG_FILE_EXTENSION = ".json";
}
