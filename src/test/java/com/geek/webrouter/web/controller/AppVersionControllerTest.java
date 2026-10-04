package com.geek.webrouter.web.controller;

import com.geek.webrouter.common.exception.GlobalExceptionHandler;
import com.geek.webrouter.config.UpdateProperties;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseInfo;
import com.geek.webrouter.web.service.AssetDownloader;
import com.geek.webrouter.web.service.AutoUpdateService;
import com.geek.webrouter.web.service.InFlightRequestTracker;
import com.geek.webrouter.web.service.ReleaseClient;
import com.geek.webrouter.web.service.impl.AppVersionServiceImpl;
import com.geek.webrouter.web.service.impl.UpdateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * 版本与自动更新接口契约测试 — 使用 WebTestClient.bindToController，全程不联网。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
class AppVersionControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void versionEndpointReturnsEveryFieldOfTheFrozenContract() {
        WebTestClient client = client(properties("1.3.0", "jar", "macos-arm64"), new FakeReleaseClient(List.of()));

        client.get()
                .uri("/admin/api/version")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.version").isEqualTo("1.3.0")
                .jsonPath("$.data.buildTime").isEqualTo("2026-09-30T15:56:37Z")
                .jsonPath("$.data.platform").isEqualTo("macos-arm64")
                .jsonPath("$.data.installMode").isEqualTo("jar")
                .jsonPath("$.data.updateSupported").isEqualTo(true)
                .jsonPath("$.data.updateChannel").isEqualTo("stable")
                .jsonPath("$.data.repository").isEqualTo("geek-xin/web-router");
    }

    @Test
    void versionEndpointFallsBackToUnknownWhenResourceIsMissing() {
        WebTestClient client = client(new Properties(), new FakeReleaseClient(List.of()));

        client.get()
                .uri("/admin/api/version")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.version").isEqualTo("unknown")
                .jsonPath("$.data.platform").isEqualTo("unknown")
                .jsonPath("$.data.installMode").isEqualTo("jar")
                .jsonPath("$.data.updateSupported").isEqualTo(true);
    }

    @Test
    void checkReturnsSuccessWhenNoUpdateIsAvailable() {
        WebTestClient client = client(properties("1.3.0", "jar", "unknown"),
                new FakeReleaseClient(List.of(release("v1.3.0", false, asset("wrouter-1.3.0-jar.zip", 10, "")))));

        client.get()
                .uri("/admin/api/update/check")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.currentVersion").isEqualTo("1.3.0")
                .jsonPath("$.data.updateAvailable").isEqualTo(false)
                .jsonPath("$.data.message").value(String.class,
                        message -> org.assertj.core.api.Assertions.assertThat(message).contains("最新"));
    }

    @Test
    void checkReturnsSuccessAndAssetDetailsWhenAnUpdateIsAvailable() {
        WebTestClient client = client(properties("1.3.0", "jar", "unknown"),
                new FakeReleaseClient(List.of(release("v1.4.0", false,
                        asset("wrouter-1.4.0-jar.zip", 2048, "sha256:abc")))));

        client.get()
                .uri("/admin/api/update/check")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.updateAvailable").isEqualTo(true)
                .jsonPath("$.data.latestVersion").isEqualTo("1.4.0")
                .jsonPath("$.data.assetName").isEqualTo("wrouter-1.4.0-jar.zip")
                .jsonPath("$.data.assetSize").isEqualTo(2048)
                .jsonPath("$.data.assetDigest").isEqualTo("sha256:abc")
                .jsonPath("$.data.message").value(String.class,
                        message -> org.assertj.core.api.Assertions.assertThat(message).contains("发现新版本"));
    }

    @Test
    void checkStillReturnsSuccessWhenGitHubIsUnavailable() {
        WebTestClient client = client(properties("1.3.0", "jar", "unknown"),
                new FakeReleaseClient(new IOException("Connection reset")));

        client.get()
                .uri("/admin/api/update/check")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.updateAvailable").isEqualTo(false)
                .jsonPath("$.data.latestVersion").isEqualTo("unknown")
                .jsonPath("$.data.message").value(String.class,
                        message -> org.assertj.core.api.Assertions.assertThat(message).contains("检查更新失败"));
    }

    @Test
    void applyFailsWithBusinessErrorWhenTheTargetVersionHasNoMatchingAsset() {
        WebTestClient client = client(properties("1.3.0", "app-image", "macos-arm64"),
                new FakeReleaseClient(List.of(release("v1.4.0", false,
                        asset("wrouter-1.4.0-windows-x64-app.zip", 10, "")))));

        client.post()
                .uri("/admin/api/update/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"version\":\"1.4.0\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(400)
                .jsonPath("$.message").value(String.class,
                        message -> org.assertj.core.api.Assertions.assertThat(message).contains("没有匹配当前平台"));
    }

    @Test
    void applyDownloadsAndReturnsTheGeneratedUpdaterScript() throws Exception {
        String payload = "wrouter-payload";
        String digest = sha256(payloadFile(payload));
        WebTestClient client = client(properties("1.3.0", "jar", "unknown"),
                new FakeReleaseClient(List.of(release("v1.4.0", false,
                        asset("wrouter-1.4.0-jar.zip", payload.length(), "sha256:" + digest)))),
                new FakeDownloader(payload), () -> { });

        client.post()
                .uri("/admin/api/update/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"version\":\"1.4.0\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.version").isEqualTo("1.4.0")
                .jsonPath("$.data.archivePath").value(String.class,
                        path -> org.assertj.core.api.Assertions.assertThat(path)
                                .endsWith("wrouter-1.4.0-jar.zip"))
                .jsonPath("$.data.updaterScript").value(String.class,
                        path -> org.assertj.core.api.Assertions.assertThat(path).endsWith("apply-update.sh"));
    }

    private Path payloadFile(String payload) throws IOException {
        Path file = tempDir.resolve("payload.bin");
        Files.writeString(file, payload, StandardCharsets.UTF_8);
        return file;
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
    }

    private WebTestClient client(Properties properties, ReleaseClient releaseClient) {
        return client(properties, releaseClient, new FakeDownloader("payload"), () -> { });
    }

    private WebTestClient client(Properties properties, ReleaseClient releaseClient,
                                 AssetDownloader downloader, Runnable exitAction) {
        AppVersionServiceImpl appVersionService = new AppVersionServiceImpl(properties);
        UpdateServiceImpl updateService = new UpdateServiceImpl(appVersionService, releaseClient, downloader,
                tempDir, 0L, exitAction);
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        AutoUpdateService autoUpdateService = new AutoUpdateService(updateService, tracker, new UpdateProperties());
        return WebTestClient.bindToController(new AppVersionController(appVersionService, updateService, autoUpdateService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Properties properties(String version, String installMode, String platform) {
        Properties properties = new Properties();
        properties.setProperty("app.version", version);
        properties.setProperty("app.buildTime", "2026-09-30T15:56:37Z");
        properties.setProperty("app.platform", platform);
        properties.setProperty("app.installMode", installMode);
        properties.setProperty("app.updateChannel", "stable");
        properties.setProperty("app.repository", "geek-xin/web-router");
        return properties;
    }

    private static ReleaseInfo release(String tag, boolean prerelease, ReleaseAsset... assets) {
        return new ReleaseInfo(tag, "wrouter " + tag, "修复若干问题", "2026-09-30T15:56:37Z",
                "https://example.test/releases/" + tag, prerelease, false, List.of(assets));
    }

    private static ReleaseAsset asset(String name, long size, String digest) {
        return new ReleaseAsset(name, "https://example.test/" + name, size, digest);
    }

    /** 假 Release 客户端 — 测试不联网。 */
    private static final class FakeReleaseClient implements ReleaseClient {

        private final List<ReleaseInfo> releases;
        private final Exception failure;

        FakeReleaseClient(List<ReleaseInfo> releases) {
            this.releases = releases;
            this.failure = null;
        }

        FakeReleaseClient(Exception failure) {
            this.releases = List.of();
            this.failure = failure;
        }

        @Override
        public List<ReleaseInfo> listReleases(String repository) throws Exception {
            if (failure != null) {
                throw failure;
            }
            return releases;
        }

        @Override
        public java.io.InputStream openAsset(String assetUrl) throws Exception {
            throw new UnsupportedOperationException("测试不应真实下载资产");
        }
    }

    /** 假下载器 — 直接把内容写入目标文件。 */
    private static final class FakeDownloader implements AssetDownloader {

        private final byte[] content;

        FakeDownloader(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Path download(String assetUrl, Path target) throws IOException {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return target;
        }
    }
}
