package com.geek.webrouter.web.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 管理后台静态资源的版本标记（{@code ?v=} 查询串）。
 *
 * <p>背景：入口页 {@code /admin} 引用 {@code /admin/assets/app.js} 与 {@code app.css}。
 * 这两个文件名是固定的，靠查询串失效。此前版本串是**手写在 index.html 里的常量**，
 * 带来两个真实故障：</p>
 * <ul>
 *   <li>改完产物忘了提升版本串 → 浏览器继续用旧文件；</li>
 *   <li>更隐蔽的是**复用了已经用过的版本串** → 浏览器缓存里已有该 URL 的旧响应，
 *       新产物永远取不到，表现为「改了但看不到」。</li>
 * </ul>
 *
 * <p>因此改为**由产物内容算出**：内容一变，版本串必变；内容不变则版本串稳定，
 * 长缓存依然有效。人工步骤被彻底消除。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Component
public class AssetVersionResolver {

    /** 需要参与版本计算的资源；顺序固定，保证结果稳定。 */
    private static final String[] TRACKED_ASSETS = {
            "static/admin/assets/app.css",
            "static/admin/assets/app.js"
    };

    /** 取哈希前 8 位：足够避免碰撞，又不至于让 URL 太长。 */
    private static final int HASH_PREFIX_LENGTH = 8;

    /** 资源缺失时的兜底值：不缓存错误，只是让页面仍能渲染。 */
    private static final String FALLBACK = "dev";

    private volatile String cached;

    /**
     * 当前产物版本串，形如 {@code d6cbdad3}。
     *
     * @return 版本串；资源不可读时返回 {@code dev}
     */
    public String current() {
        String value = cached;
        if (value == null) {
            synchronized (this) {
                value = cached;
                if (value == null) {
                    value = compute();
                    cached = value;
                }
            }
        }
        return value;
    }

    private String compute() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String asset : TRACKED_ASSETS) {
                ClassPathResource resource = new ClassPathResource(asset);
                if (!resource.exists()) {
                    log.warn("静态资源缺失，版本串回退为 {}：{}", FALLBACK, asset);
                    return FALLBACK;
                }
                try (InputStream in = resource.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, HASH_PREFIX_LENGTH);
        } catch (Exception e) {
            log.warn("计算静态资源版本串失败，回退为 {}：{}", FALLBACK, e.getMessage());
            return FALLBACK;
        }
    }
}
