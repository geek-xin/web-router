package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.config.UpdateProperties;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseInfo;
import com.geek.webrouter.web.model.dto.UpdateApplyRequest;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import com.geek.webrouter.web.service.AutoUpdateService;
import com.geek.webrouter.web.service.InFlightRequestTracker;
import com.geek.webrouter.web.service.ReleaseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无感自动更新调度测试 — 覆盖「静默预下载」与「空闲才重启」两条核心行为。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
class AutoUpdateServiceTest {

    private static final String PAYLOAD = "wrouter-payload";

    @TempDir
    Path tempDir;

    @Test
    void stagesTheUpdateWithoutExitingTheProcess() {
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        AutoUpdateService auto = autoService(tracker, exits, autoProperties());

        auto.checkAndStage();

        // 关键：暂存阶段绝不退出进程，服务继续可用
        assertThat(exits.get()).isZero();
        assertThat(auto.pendingUpdate()).isNotNull();
        assertThat(auto.pendingUpdate().version()).isEqualTo("1.4.0");
        assertThat(Files.exists(tempDir.resolve("wrouter-1.4.0-jar.zip"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("apply-update.sh"))).isTrue();
    }

    @Test
    void doesNotApplyWhileARequestIsInFlight() {
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        AutoUpdateService auto = autoService(tracker, exits, autoProperties());
        auto.checkAndStage();

        // 有请求在途：绝不重启
        tracker.begin();
        auto.applyWhenIdle();
        assertThat(exits.get()).isZero();
        assertThat(auto.isIdle()).isFalse();
        assertThat(auto.inFlightCount()).isEqualTo(1);

        // 请求结束但静默窗口未到：仍不重启
        tracker.end();
        UpdateProperties quiet = autoProperties();
        quiet.setQuietSeconds(600);
        AutoUpdateService strict = autoService(tracker, exits, quiet);
        strict.checkAndStage();
        strict.applyWhenIdle();
        assertThat(exits.get()).isZero();
    }

    @Test
    void appliesOnceTheQuietWindowIsReached() throws Exception {
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        AutoUpdateService auto = autoService(tracker, exits, autoProperties());
        auto.checkAndStage();

        auto.applyWhenIdle();
        awaitExit(exits);

        assertThat(exits.get()).isEqualTo(1);
        // 只安排一次退出，重复调用不得重复触发
        auto.applyWhenIdle();
        auto.applyWhenIdle();
        Thread.sleep(50);
        assertThat(exits.get()).isEqualTo(1);
    }

    /** 退出动作在独立线程上延迟执行（见 UpdateServiceImpl#scheduleExit），测试需要等它落地。 */
    private static void awaitExit(AtomicInteger exits) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (exits.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void doesNothingWhenAutoUpdateIsDisabled() {
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        UpdateProperties disabled = autoProperties();
        disabled.setAuto(false);
        AutoUpdateService auto = autoService(tracker, exits, disabled);

        auto.checkAndStage();
        auto.applyWhenIdle();

        assertThat(auto.pendingUpdate()).isNull();
        assertThat(exits.get()).isZero();
        assertThat(Files.exists(tempDir.resolve("wrouter-1.4.0-jar.zip"))).isFalse();
    }

    @Test
    void onlyDownloadsWhenAutoApplyIsDisabled() {
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        UpdateProperties downloadOnly = autoProperties();
        downloadOnly.setAutoApply(false);
        AutoUpdateService auto = autoService(tracker, exits, downloadOnly);

        auto.checkAndStage();
        auto.applyWhenIdle();

        // 已预下载，但不自动重启，等用户确认
        assertThat(auto.pendingUpdate()).isNotNull();
        assertThat(exits.get()).isZero();
    }

    @Test
    void swallowsCheckFailuresSoTheSchedulerKeepsRunning() {
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        ReleaseClient failing = new ReleaseClient() {
            @Override
            public List<ReleaseInfo> listReleases(String repository) throws Exception {
                throw new IOException("Connection reset");
            }

            @Override
            public InputStream openAsset(String assetUrl) {
                throw new UnsupportedOperationException();
            }
        };
        UpdateServiceImpl update = new UpdateServiceImpl(new AppVersionServiceImpl(versionProperties()), failing,
                (url, target) -> target, tempDir, 0L, () -> { });
        AutoUpdateService auto = new AutoUpdateService(update, tracker, autoProperties());

        // 网络故障不得把异常抛给调度线程
        auto.checkAndStage();

        assertThat(auto.pendingUpdate()).isNull();
    }

    @Test
    void stagingTwiceReusesTheVerifiedArchiveInsteadOfDownloadingAgain() {
        AtomicInteger downloads = new AtomicInteger();
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        String digest = UpdateServiceImpl.sha256(writePayload());
        ReleaseInfo release = release("v1.4.0", asset("wrouter-1.4.0-jar.zip", "sha256:" + digest));
        UpdateServiceImpl update = new UpdateServiceImpl(new AppVersionServiceImpl(versionProperties()),
                new FakeReleaseClient(List.of(release)),
                (url, target) -> {
                    downloads.incrementAndGet();
                    Files.writeString(target, PAYLOAD, StandardCharsets.UTF_8);
                    return target;
                }, tempDir, 0L, exits::incrementAndGet);
        AutoUpdateService auto = new AutoUpdateService(update, tracker, autoProperties());

        auto.checkAndStage();
        auto.checkAndStage();

        assertThat(downloads.get()).isEqualTo(1);
        assertThat(auto.pendingUpdate()).isNotNull();
    }

    @Test
    void manualApplyStillExitsImmediatelyForOneClickUpdates() throws Exception {
        AtomicInteger exits = new AtomicInteger();
        InFlightRequestTracker tracker = new InFlightRequestTracker();
        UpdateApplyResult result = autoServiceUpdate(tracker, exits).apply(new UpdateApplyRequest("1.4.0"));

        assertThat(result.version()).isEqualTo("1.4.0");
        awaitExit(exits);
        assertThat(exits.get()).isEqualTo(1);
    }

    private AutoUpdateService autoService(InFlightRequestTracker tracker, AtomicInteger exits,
                                          UpdateProperties properties) {
        return new AutoUpdateService(autoServiceUpdate(tracker, exits), tracker, properties);
    }

    private UpdateServiceImpl autoServiceUpdate(InFlightRequestTracker tracker, AtomicInteger exits) {
        String digest = UpdateServiceImpl.sha256(writePayload());
        ReleaseInfo release = release("v1.4.0", asset("wrouter-1.4.0-jar.zip", "sha256:" + digest));
        return new UpdateServiceImpl(new AppVersionServiceImpl(versionProperties()),
                new FakeReleaseClient(List.of(release)),
                (url, target) -> {
                    Files.writeString(target, PAYLOAD, StandardCharsets.UTF_8);
                    return target;
                }, tempDir, 0L, exits::incrementAndGet);
    }

    private Path writePayload() {
        try {
            Path file = tempDir.resolve("payload.bin");
            Files.writeString(file, PAYLOAD, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UpdateProperties autoProperties() {
        UpdateProperties properties = new UpdateProperties();
        properties.setAuto(true);
        properties.setAutoApply(true);
        properties.setQuietSeconds(0);
        return properties;
    }

    private static Properties versionProperties() {
        Properties properties = new Properties();
        properties.setProperty("app.version", "1.3.0");
        properties.setProperty("app.platform", "unknown");
        properties.setProperty("app.installMode", "jar");
        properties.setProperty("app.updateChannel", "stable");
        properties.setProperty("app.repository", "geek-xin/web-router");
        return properties;
    }

    private static ReleaseInfo release(String tag, ReleaseAsset... assets) {
        return new ReleaseInfo(tag, "wrouter " + tag, "修复若干问题", "2026-09-30T15:56:37Z",
                "https://example.test/releases/" + tag, false, false, List.of(assets));
    }

    private static ReleaseAsset asset(String name, String digest) {
        return new ReleaseAsset(name, "https://example.test/" + name, 16L, digest);
    }

    private record FakeReleaseClient(List<ReleaseInfo> releases) implements ReleaseClient {
        @Override
        public List<ReleaseInfo> listReleases(String repository) {
            return releases;
        }

        @Override
        public InputStream openAsset(String assetUrl) {
            throw new UnsupportedOperationException();
        }
    }
}
