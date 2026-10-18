package com.geek.webrouter.web.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 静态资源版本串必须由**产物内容**算出。
 *
 * <p>这是「改了但看不到」的根因防线：手写版本串一旦忘记提升、
 * 或复用了已经用过的值，浏览器就会一直命中缓存里的旧响应。</p>
 */
class AssetVersionResolverTest {

    private final AssetVersionResolver resolver = new AssetVersionResolver();

    @Test
    void versionIsDerivedFromAssetContent() {
        String version = resolver.current();

        assertThat(version)
                .as("版本串应为内容哈希前 8 位")
                .isNotBlank()
                .isNotEqualTo("dev")
                .hasSize(8)
                .matches("[0-9a-f]{8}");
    }

    /** 同一份产物必须得到稳定版本串，否则长缓存会失效。 */
    @Test
    void versionIsStableForUnchangedAssets() {
        assertThat(resolver.current()).isEqualTo(resolver.current());
        assertThat(new AssetVersionResolver().current()).isEqualTo(resolver.current());
    }

    /** 版本串必须与测试资源里的真实产物内容一致，防止实现偷偷改成常量。 */
    @Test
    void versionMatchesTheActualBuiltAssets() throws Exception {
        var css = new org.springframework.core.io.ClassPathResource("static/admin/assets/app.css");
        var js = new org.springframework.core.io.ClassPathResource("static/admin/assets/app.js");
        assertThat(css.exists()).as("构建产物 app.css 必须存在").isTrue();
        assertThat(js.exists()).as("构建产物 app.js 必须存在").isTrue();

        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var resource : java.util.List.of(css, js)) {
            try (var in = resource.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        String expected = java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 8);
        assertThat(resolver.current()).isEqualTo(expected);
    }
}
