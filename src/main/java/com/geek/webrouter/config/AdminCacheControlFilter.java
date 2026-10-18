package com.geek.webrouter.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 管理后台静态资源的缓存策略。
 *
 * <p>背景：后台入口页 {@code /admin} 引用带 {@code ?v=<构建标记>} 的
 * {@code app.js} / {@code app.css}。入口页本身**绝不能**被浏览器缓存——
 * 一旦缓存，即使服务端已更新产物，用户仍会按旧的 {@code ?v=} 去取旧文件，
 * 表现为「修好的问题刷新后依然存在」。</p>
 *
 * <p>策略：</p>
 * <ul>
 *   <li>HTML 入口（{@code /admin}、{@code /}）→ {@code no-store}，每次都回源。</li>
 *   <li>带版本号的静态资源（{@code ?v=}）→ 一年 {@code immutable}，
 *       因为文件名固定、靠查询串失效，可安全长缓存。</li>
 *   <li>未带版本号的静态资源 → 短缓存 + 必须回源校验，兼顾更新及时性。</li>
 * </ul>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCacheControlFilter implements WebFilter {

    /** 版本化静态资源的缓存时长（秒）：一年。 */
    private static final long IMMUTABLE_MAX_AGE_SECONDS = 31_536_000L;

    /** 未版本化静态资源的缓存时长（秒）：5 分钟。 */
    private static final long SHORT_MAX_AGE_SECONDS = 300L;

    /** 静态资源前缀。 */
    private static final String ASSETS_PREFIX = "/admin/assets/";

    /** 版本化查询参数名，与 index.html 的 {@code ?v=} 一致。 */
    private static final String VERSION_PARAM = "v";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        String path = exchange.getRequest().getPath().value();

        if (isHtmlEntry(path)) {
            // 入口页不缓存：它决定用哪个版本的产物
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate");
            headers.set(HttpHeaders.PRAGMA, "no-cache");
            headers.set(HttpHeaders.EXPIRES, "0");
        } else if (path.startsWith(ASSETS_PREFIX)) {
            if (isVersioned(exchange)) {
                headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=" + IMMUTABLE_MAX_AGE_SECONDS + ", immutable");
            } else {
                headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=" + SHORT_MAX_AGE_SECONDS + ", must-revalidate");
            }
        }
        return chain.filter(exchange);
    }

    /** 入口页：管理后台首页与根路径重定向。 */
    private static boolean isHtmlEntry(String path) {
        return "/admin".equals(path) || "/admin/".equals(path) || "/".equals(path);
    }

    /** 是否带版本查询串（{@code ?v=...}）。 */
    private static boolean isVersioned(ServerWebExchange exchange) {
        String version = exchange.getRequest().getQueryParams().getFirst(VERSION_PARAM);
        return version != null && !version.isBlank();
    }
}
