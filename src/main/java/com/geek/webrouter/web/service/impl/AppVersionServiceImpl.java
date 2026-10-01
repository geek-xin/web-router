package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.web.model.dto.AppVersionInfo;
import com.geek.webrouter.web.service.AppVersionService;
import com.geek.webrouter.web.support.ReleaseAssets;
import com.geek.webrouter.web.support.UpdatePlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 应用版本信息服务实现 — 读取 classpath 下的 {@code version.properties}。
 *
 * <p>资源缺失或字段为空时统一降级为安全默认值，保证接口始终可用。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Service
public class AppVersionServiceImpl implements AppVersionService {

    /** 构建元信息资源路径。 */
    public static final String VERSION_RESOURCE = "version.properties";

    /** 版本未知时的占位值。 */
    public static final String UNKNOWN = "unknown";

    /** jar 安装形态。 */
    public static final String INSTALL_MODE_JAR = "jar";

    /** app-image 安装形态。 */
    public static final String INSTALL_MODE_APP_IMAGE = "app-image";

    /** 稳定通道。 */
    public static final String CHANNEL_STABLE = "stable";

    /** 预发布通道。 */
    public static final String CHANNEL_PRERELEASE = "prerelease";

    private final Properties properties;

    public AppVersionServiceImpl() {
        this(loadProperties());
    }

    /** 便于测试注入元信息。 */
    public AppVersionServiceImpl(Properties properties) {
        this.properties = properties;
    }

    @Override
    public AppVersionInfo currentVersion() {
        String version = value("app.version", UNKNOWN);
        String platform = value("app.platform", UNKNOWN);
        String installMode = value("app.installMode", INSTALL_MODE_JAR);
        String updateChannel = value("app.updateChannel", CHANNEL_STABLE);
        String repository = value("app.repository", "");
        return new AppVersionInfo(version, value("app.buildTime", ""), platform, installMode,
                updateSupported(installMode, platform), updateChannel, repository);
    }

    /**
     * 判断当前形态是否支持自动更新。
     *
     * <p>jar 形态通过 zip 更新包升级；app-image 形态要求当前系统与架构能匹配到对应资产。</p>
     */
    public static boolean updateSupported(String installMode, String platform) {
        if (INSTALL_MODE_JAR.equalsIgnoreCase(installMode)) {
            return true;
        }
        return ReleaseAssets.supportsPlatform(installMode, platform);
    }

    /** 判断当前通道是否接受预发布版本。 */
    public static boolean acceptsPrerelease(String updateChannel) {
        return CHANNEL_PRERELEASE.equalsIgnoreCase(updateChannel);
    }

    private String value(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Properties loadProperties() {
        Properties loaded = new Properties();
        try (InputStream input = AppVersionServiceImpl.class.getClassLoader()
                .getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                log.warn("未找到构建元信息资源 {}，版本信息降级为默认值", VERSION_RESOURCE);
                return loaded;
            }
            loaded.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("读取构建元信息资源失败，版本信息降级为默认值: {}", e.getMessage());
        }
        return loaded;
    }
}
