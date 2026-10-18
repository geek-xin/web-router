package com.geek.webrouter.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理后台缓存策略：入口页不缓存，版本化资源长缓存。
 *
 * <p>这是「修好的问题刷新后依然存在」的根因防线：入口页一旦被缓存，
 * 浏览器会一直按旧的 {@code ?v=} 去取旧产物。</p>
 */
class AdminCacheControlFilterTest {

    private final AdminCacheControlFilter filter = new AdminCacheControlFilter();
    private final WebFilterChain chain = exchange -> Mono.empty();

    private HttpHeaders headersFor(String url) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(url));
        filter.filter(exchange, chain).block();
        return exchange.getResponse().getHeaders();
    }

    @Test
    void htmlEntryIsNeverCached() {
        for (String url : new String[]{"/admin", "/admin/", "/"}) {
            HttpHeaders headers = headersFor(url);
            assertThat(headers.getCacheControl())
                    .as("入口页 %s 必须禁止缓存", url)
                    .contains("no-store");
            assertThat(headers.getPragma()).contains("no-cache");
        }
    }

    @Test
    void versionedAssetsAreCachedImmutably() {
        HttpHeaders headers = headersFor("/admin/assets/app.js?v=20261011-platform");

        assertThat(headers.getCacheControl()).contains("immutable");
        assertThat(headers.getCacheControl()).contains("max-age=31536000");
    }

    /** 未带版本串的资源必须短缓存并回源校验，避免更新不及时。 */
    @Test
    void unversionedAssetsUseShortRevalidatingCache() {
        HttpHeaders headers = headersFor("/admin/assets/app.css");

        assertThat(headers.getCacheControl()).contains("max-age=300");
        assertThat(headers.getCacheControl()).contains("must-revalidate");
        assertThat(headers.getCacheControl()).doesNotContain("immutable");
    }

    /** 空白版本串不算版本化，按未版本化处理。 */
    @Test
    void blankVersionIsTreatedAsUnversioned() {
        HttpHeaders headers = headersFor("/admin/assets/app.js?v=");

        assertThat(headers.getCacheControl()).contains("max-age=300");
        assertThat(headers.getCacheControl()).doesNotContain("immutable");
    }

    /** 业务 API 不该被本过滤器改写缓存头。 */
    @Test
    void apiRoutesAreLeftAlone() {
        assertThat(headersFor("/admin/api/routes").getCacheControl()).isNull();
        assertThat(headersFor("/admin/api/update/history").getCacheControl()).isNull();
        assertThat(headersFor("/realTimeMonitor").getCacheControl()).isNull();
    }
}
