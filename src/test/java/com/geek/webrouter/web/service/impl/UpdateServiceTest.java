package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseHistory;
import com.geek.webrouter.web.model.dto.ReleaseHistoryItem;
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

    // ------------------------------------------------------------ 历史版本 --

    @Test
    void historyListsEveryReleaseNewestFirstAndMarksTheRunningVersion() {
        UpdateServiceImpl service = service(new FakeReleaseClient(List.of(
                release("v1.2.0", false), release("v1.10.0", false), release("v1.3.0", false))),
                new FakeDownloader("x"));

        ReleaseHistory history = service.history();

        assertThat(history.currentVersion()).isEqualTo("1.3.0");
        assertThat(history.repository()).isEqualTo("geek-xin/web-router");
        assertThat(history.message()).isEmpty();
        // 语义化版本排序：1.10.0 必须排在 1.3.0 之前
        assertThat(history.releases()).extracting(ReleaseHistoryItem::version)
                .containsExactly("1.10.0", "1.3.0", "1.2.0");
        assertThat(history.releases()).filteredOn(ReleaseHistoryItem::current)
                .extracting(ReleaseHistoryItem::version).containsExactly("1.3.0");
    }

    /** 与「检查更新」不同：历史里必须保留预发布与当前版本，并带上修订记录。 */
    @Test
    void historyKeepsPrereleasesAndCarriesReleaseNotes() {
        ReleaseInfo stable = release("v1.3.0", false);
        ReleaseInfo beta = new ReleaseInfo("v1.4.0-beta.1", "wrouter 1.4.0-beta.1", "新增历史版本视图",
                "2026-10-01T00:00:00Z", "https://example.test/releases/beta", true, false, List.of());
        UpdateServiceImpl service = service(new FakeReleaseClient(List.of(stable, beta)), new FakeDownloader("x"));

        ReleaseHistory history = service.history();

        assertThat(history.releases()).extracting(ReleaseHistoryItem::version)
                .containsExactly("1.4.0-beta.1", "1.3.0");
        assertThat(history.releases()).filteredOn(ReleaseHistoryItem::prerelease)
                .extracting(ReleaseHistoryItem::version).containsExactly("1.4.0-beta.1");
        assertThat(history.releases().get(1).notes()).isEqualTo("修复若干问题");
        assertThat(history.releases().get(1).releaseUrl()).isEqualTo("https://example.test/releases/v1.3.0");
    }

    @Test
    void historySkipsDraftsAndBlankTags() {
        ReleaseInfo draft = new ReleaseInfo("v1.9.0", "draft", "未发布", "", "", false, true, List.of());
        ReleaseInfo blank = new ReleaseInfo("", "blank", "", "", "", false, false, List.of());
        UpdateServiceImpl service = service(new FakeReleaseClient(List.of(release("v1.3.0", false), draft, blank)),
                new FakeDownloader("x"));

        assertThat(service.history().releases()).extracting(ReleaseHistoryItem::version)
                .containsExactly("1.3.0");
    }

    /** 读取失败不能让接口报错，必须降级为空列表 + 原因。 */
    @Test
    void historyReportsFailureWithoutThrowing() {
        UpdateServiceImpl service = service(new FakeReleaseClient(new IOException("Connection reset")),
                new FakeDownloader("x"));

        ReleaseHistory history = service.history();

        assertThat(history.releases()).isEmpty();
        assertThat(history.message()).contains("读取历史版本失败").contains("无法连接 GitHub");
    }

    @Test
    void historyReportsMissingRepositoryAndEmptyReleaseList() {
        UpdateServiceImpl noRepo = service(propertiesWithoutRepository(), new FakeReleaseClient(List.of()),
                new FakeDownloader("x"));
        assertThat(noRepo.history().message()).contains("未配置更新仓库地址");

        UpdateServiceImpl empty = service(new FakeReleaseClient(List.of()), new FakeDownloader("x"));
        assertThat(empty.history().releases()).isEmpty();
        assertThat(empty.history().message()).contains("暂无可用的发布版本");
    }

    /** 连续读取应命中缓存，避免把 GitHub 未认证额度耗尽。 */
    @Test
    void historyReusesTheCachedReleaseList() {
        CountingReleaseClient client = new CountingReleaseClient(List.of(release("v1.4.0", false)));
        UpdateServiceImpl service = service(client, new FakeDownloader("x"));

        service.history();
        service.history();
        service.history();

        assertThat(client.calls.get()).isEqualTo(1);
    }

    /** 失败不写入缓存：网络恢复后下一次调用应立即重试成功。 */
    @Test
    void historyRetriesAfterAFailureInsteadOfCachingIt() {
        FlakyReleaseClient client = new FlakyReleaseClient(release("v1.4.0", false));
        UpdateServiceImpl service = service(client, new FakeDownloader("x"));

        assertThat(service.history().releases()).isEmpty();
        assertThat(service.history().releases()).extracting(ReleaseHistoryItem::version)
                .containsExactly("1.4.0");
        assertThat(client.calls.get()).isEqualTo(2);
    }


    /**
     * 缓存过期后刷新失败时，必须回落到**上一次成功的数据**，而不是清空列表。
     *
     * <p>这是「读取历史版本失败」体验问题的根因：旧实现直接返回 null，
     * 于是用户明明几秒前还能看到版本列表，一次网络抖动就变成空列表 + 报错。</p>
     */
    @Test
    void historyFallsBackToStaleCacheWhenRefreshFails() {
        StaleThenFailingClient client = new StaleThenFailingClient(release("v1.4.0", false));
        MutableClock clock = new MutableClock();
        UpdateServiceImpl service = service(client, new FakeDownloader("x"), clock);

        // 第一次成功，写入缓存
        assertThat(service.history().releases()).extracting(ReleaseHistoryItem::version).containsExactly("1.4.0");

        // 推进到缓存过期，之后的刷新持续失败
        clock.advance(UpdateServiceImpl.HISTORY_CACHE_MILLIS + 1);
        ReleaseHistory stale = service.history();

        // 关键：仍然看得到版本列表，而不是空列表
        assertThat(stale.releases())
                .as("刷新失败时必须回落到上次成功的数据")
                .extracting(ReleaseHistoryItem::version).containsExactly("1.4.0");
        // 同时如实告知这是缓存数据
        assertThat(stale.message()).contains("缓存数据");
    }

    /** 刷新成功时不得出现任何缓存提示。 */
    @Test
    void historyHasNoStaleNoticeWhenRefreshSucceeds() {
        CountingReleaseClient client = new CountingReleaseClient(List.of(release("v1.4.0", false)));
        MutableClock clock = new MutableClock();
        UpdateServiceImpl service = service(client, new FakeDownloader("x"), clock);

        service.history();
        clock.advance(UpdateServiceImpl.HISTORY_CACHE_MILLIS + 1);
        ReleaseHistory refreshed = service.history();

        assertThat(refreshed.releases()).extracting(ReleaseHistoryItem::version).containsExactly("1.4.0");
        assertThat(refreshed.message()).isEmpty();
        assertThat(client.calls.get()).isEqualTo(2);
    }

    /** 从没成功过（无缓存）时，失败仍应如实报错而不是假装有数据。 */
    @Test
    void historyReportsFailureWhenNoCacheExistsAtAll() {
        UpdateServiceImpl service = service(new FakeReleaseClient(new IOException("Connection reset")),
                new FakeDownloader("x"), new MutableClock());

        ReleaseHistory history = service.history();

        assertThat(history.releases()).isEmpty();
        assertThat(history.message()).contains("读取历史版本失败").contains("无法连接 GitHub");
        assertThat(history.message()).doesNotContain("缓存数据");
    }

    /** 可手动推进的时钟，用于验证缓存过期。 */
    private static final class MutableClock implements java.util.function.LongSupplier {
        private long now = 1_700_000_000_000L;

        void advance(long millis) {
            now += millis;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }

    /** 第一次成功、之后持续失败的假客户端 — 验证缓存回落。 */
    private static final class StaleThenFailingClient implements ReleaseClient {

        private final ReleaseInfo release;
        private final AtomicInteger calls = new AtomicInteger();

        StaleThenFailingClient(ReleaseInfo release) {
            this.release = release;
        }

        @Override
        public List<ReleaseInfo> listReleases(String repository) throws Exception {
            if (calls.incrementAndGet() == 1) {
                return List.of(release);
            }
            throw new IOException("Connection reset");
        }

        @Override
        public java.io.InputStream openAsset(String assetUrl) {
            throw new UnsupportedOperationException("测试不应真实下载资产");
        }
    }

    private static Properties propertiesWithoutRepository() {
        Properties properties = properties();
        properties.remove("app.repository");
        return properties;
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

    /** 注入可推进时钟，用于验证缓存过期行为。 */
    private UpdateServiceImpl service(ReleaseClient client, AssetDownloader downloader,
                                      java.util.function.LongSupplier clock) {
        return new UpdateServiceImpl(new AppVersionServiceImpl(properties()), client, downloader,
                tempDir, 0L, () -> { }, clock);
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

    /** 统计调用次数的假客户端 — 验证历史版本缓存生效。 */
    private static final class CountingReleaseClient implements ReleaseClient {

        private final List<ReleaseInfo> releases;
        private final AtomicInteger calls = new AtomicInteger();

        CountingReleaseClient(List<ReleaseInfo> releases) {
            this.releases = releases;
        }

        @Override
        public List<ReleaseInfo> listReleases(String repository) {
            calls.incrementAndGet();
            return releases;
        }

        @Override
        public java.io.InputStream openAsset(String assetUrl) {
            throw new UnsupportedOperationException("测试不应真实下载资产");
        }
    }

    /** 第一次失败、之后成功的假客户端 — 验证失败不被缓存。 */
    private static final class FlakyReleaseClient implements ReleaseClient {

        private final ReleaseInfo release;
        private final AtomicInteger calls = new AtomicInteger();

        FlakyReleaseClient(ReleaseInfo release) {
            this.release = release;
        }

        @Override
        public List<ReleaseInfo> listReleases(String repository) throws Exception {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("Connection reset");
            }
            return List.of(release);
        }

        @Override
        public java.io.InputStream openAsset(String assetUrl) {
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
