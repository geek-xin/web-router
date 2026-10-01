package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseInfo;
import com.geek.webrouter.web.model.dto.UpdateApplyRequest;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import com.geek.webrouter.web.model.dto.UpdateCheckResult;
import com.geek.webrouter.web.service.AssetDownloader;
import com.geek.webrouter.web.service.ReleaseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void checkReturnsNoUpdateWhenTheLatestReleaseIsNotNewer() {
        UpdateServiceImpl service = service(new FakeReleaseClient(List.of(release("v1.3.0", false))),
                new FakeDownloader("payload"));

        UpdateCheckResult result = service.check();

        assertThat(result.currentVersion()).isEqualTo("1.3.0");
        assertThat(result.updateAvailable()).isFalse();
        assertThat(result.message()).contains("最新");
    }

    @Test
    void checkPicksTheHighestVersionAndMatchingJarAsset() {
        ReleaseInfo older = release("v1.3.1", false, asset("wrouter-1.3.1-jar.zip", 10, ""));
        ReleaseInfo newest = release("v1.10.0", false,
                asset("wrouter-1.10.0-macos-arm64-app.zip", 20, ""),
                asset("wrouter-1.10.0-jar.zip", 30, "sha256:abc"));
        UpdateServiceImpl service = service(new FakeReleaseClient(List.of(newest, older)),
                new FakeDownloader("payload"));

        UpdateCheckResult result = service.check();

        assertThat(result.updateAvailable()).isTrue();
        assertThat(result.latestVersion()).isEqualTo("1.10.0");
        assertThat(result.assetName()).isEqualTo("wrouter-1.10.0-jar.zip");
        assertThat(result.assetUrl()).isEqualTo("https://example.test/wrouter-1.10.0-jar.zip");
        assertThat(result.assetSize()).isEqualTo(30L);
        assertThat(result.assetDigest()).isEqualTo("sha256:abc");
        assertThat(result.releaseNotes()).isEqualTo("修复若干问题");
        assertThat(result.message()).contains("发现新版本 1.10.0");
    }

    @Test
    void checkSkipsPrereleaseOnStableChannelButAcceptsItOnPrereleaseChannel() {
        ReleaseInfo prerelease = release("v1.4.0-rc.1", true, asset("wrouter-1.4.0-rc.1-jar.zip", 10, ""));
        UpdateServiceImpl stable = service(new FakeReleaseClient(List.of(prerelease)), new FakeDownloader("x"));

        assertThat(stable.check().updateAvailable()).isFalse();
        assertThat(stable.check().message()).contains("预发布");

        UpdateServiceImpl beta = service(properties("prerelease"),
                new FakeReleaseClient(List.of(prerelease)), new FakeDownloader("x"));
        UpdateCheckResult result = beta.check();

        assertThat(result.updateAvailable()).isTrue();
        assertThat(result.prerelease()).isTrue();
        assertThat(result.latestVersion()).isEqualTo("1.4.0-rc.1");
    }

    @Test
    void checkReportsNetworkFailureInMessageInsteadOfThrowing() {
        UpdateServiceImpl service = service(new FakeReleaseClient(new IOException("Connection reset")),
                new FakeDownloader("x"));

        UpdateCheckResult result = service.check();

        assertThat(result.updateAvailable()).isFalse();
        assertThat(result.message()).contains("检查更新失败");
        assertThat(result.latestVersion()).isEqualTo("unknown");
    }

    @Test
    void checkReportsMissingPlatformAssetInMessage() {
        UpdateServiceImpl service = service(properties("jar", "app-image", "windows-x64"),
                new FakeReleaseClient(List.of(release("v1.4.0", false, asset("wrouter-1.4.0-linux-x64-app.tar.gz", 1, "")))),
                new FakeDownloader("x"));

        UpdateCheckResult result = service.check();

        assertThat(result.updateAvailable()).isFalse();
        assertThat(result.message()).contains("没有匹配当前平台");
    }

    @Test
    void checkReportsUnsupportedInstallModeInMessage() {
        UpdateServiceImpl service = service(properties("unknown", "native", "unknown"),
                new FakeReleaseClient(List.of(release("v1.4.0", false))), new FakeDownloader("x"));

        assertThat(service.check().message()).contains("不支持自动更新");
    }

    @Test
    void applyDownloadsVerifiesDigestAndWritesTheUpdaterScript() throws Exception {
        String payload = "wrouter-payload";
        String digest = UpdateServiceImpl.sha256(writePayload(payload));
        ReleaseInfo release = release("v1.4.0", false,
                asset("wrouter-1.4.0-jar.zip", payload.length(), "sha256:" + digest));
        FakeDownloader downloader = new FakeDownloader(payload);
        AtomicInteger exits = new AtomicInteger();
        UpdateServiceImpl service = service(properties(), new FakeReleaseClient(List.of(release)), downloader,
                exits::incrementAndGet);

        UpdateApplyResult result = service.apply(new UpdateApplyRequest("1.4.0"));

        Path archive = tempDir.resolve("wrouter-1.4.0-jar.zip");
        Path script = tempDir.resolve("apply-update.sh");
        assertThat(result.version()).isEqualTo("1.4.0");
        assertThat(result.archivePath()).isEqualTo(archive.toString());
        assertThat(result.updaterScript()).isEqualTo(script.toString());
        assertThat(Files.readString(archive)).isEqualTo(payload);
        assertThat(Files.readString(script)).contains("#!/bin/sh").contains("backup-1.3.0");
        assertThat(Files.isExecutable(script)).isTrue();
        assertThat(downloader.downloadCount.get()).isEqualTo(1);
    }

    @Test
    void applyDeletesTheArchiveAndFailsWhenDigestDoesNotMatch() {
        ReleaseInfo release = release("v1.4.0", false,
                asset("wrouter-1.4.0-jar.zip", 12, "sha256:" + "0".repeat(64)));
        UpdateServiceImpl service = service(properties(), new FakeReleaseClient(List.of(release)),
                new FakeDownloader("wrouter-payload"), () -> { });

        assertThatThrownBy(() -> service.apply(new UpdateApplyRequest("1.4.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SHA-256 不一致");
        assertThat(Files.exists(tempDir.resolve("wrouter-1.4.0-jar.zip"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("apply-update.sh"))).isFalse();
    }

    @Test
    void applyRejectsARequestedVersionThatIsNotTheLatestOne() {
        ReleaseInfo release = release("v1.4.0", false, asset("wrouter-1.4.0-jar.zip", 1, ""));
        UpdateServiceImpl service = service(properties(), new FakeReleaseClient(List.of(release)),
                new FakeDownloader("x"), () -> { });

        assertThatThrownBy(() -> service.apply(new UpdateApplyRequest("1.2.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1.2.0");
    }

    @Test
    void applyFailsWhenNothingIsUpdatable() {
        UpdateServiceImpl service = service(new FakeReleaseClient(List.of(release("v1.3.0", false))),
                new FakeDownloader("x"));

        assertThatThrownBy(() -> service.apply(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最新");
    }

    @Test
    void assetNamesThatCouldEscapeTheUpdatesDirectoryAreRejected() {
        assertThatThrownBy(() -> UpdateServiceImpl.safeFileName("../../evil.zip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件名不合法");
        assertThatThrownBy(() -> UpdateServiceImpl.safeFileName("sub/dir.zip"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> UpdateServiceImpl.safeFileName(" "))
                .isInstanceOf(BusinessException.class);
        assertThat(UpdateServiceImpl.safeFileName("wrouter-1.4.0-jar.zip"))
                .isEqualTo("wrouter-1.4.0-jar.zip");
    }

    @Test
    void digestParsingSupportsPrefixedAndBareHexValues() {
        assertThat(UpdateServiceImpl.normalizeDigest("sha256:ABC")).isEqualTo("ABC");
        assertThat(UpdateServiceImpl.normalizeDigest("ABC")).isEqualTo("ABC");
        assertThat(UpdateServiceImpl.normalizeDigest("md5:ABC")).isNull();
        assertThat(UpdateServiceImpl.normalizeDigest("  ")).isNull();
        assertThat(UpdateServiceImpl.normalizeDigest(null)).isNull();
    }

    private Path writePayload(String payload) throws IOException {
        Path file = tempDir.resolve("payload.bin");
        Files.writeString(file, payload, StandardCharsets.UTF_8);
        return file;
    }

    private UpdateServiceImpl service(ReleaseClient client, AssetDownloader downloader) {
        return service(properties(), client, downloader, () -> { });
    }

    private UpdateServiceImpl service(Properties properties, ReleaseClient client, AssetDownloader downloader) {
        return service(properties, client, downloader, () -> { });
    }

    private UpdateServiceImpl service(Properties properties, ReleaseClient client, AssetDownloader downloader,
                                      Runnable exitAction) {
        return new UpdateServiceImpl(new AppVersionServiceImpl(properties), client, downloader,
                tempDir, 0L, exitAction);
    }

    private static Properties properties() {
        return properties("1.3.0", "jar", "unknown");
    }

    private static Properties properties(String channel) {
        Properties properties = properties();
        properties.setProperty("app.updateChannel", channel);
        return properties;
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
        private final AtomicInteger downloadCount = new AtomicInteger();

        FakeDownloader(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Path download(String assetUrl, Path target) throws IOException {
            downloadCount.incrementAndGet();
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return target;
        }
    }
}
