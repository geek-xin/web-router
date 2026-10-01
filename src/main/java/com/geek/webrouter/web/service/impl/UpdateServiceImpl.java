package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.common.enums.ErrorCodeEnum;
import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.dto.AppVersionInfo;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseInfo;
import com.geek.webrouter.web.model.dto.UpdateApplyRequest;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import com.geek.webrouter.web.model.dto.UpdateCheckResult;
import com.geek.webrouter.web.service.AppVersionService;
import com.geek.webrouter.web.service.AssetDownloader;
import com.geek.webrouter.web.service.ReleaseClient;
import com.geek.webrouter.web.service.UpdateService;
import com.geek.webrouter.web.support.AppHomeResolver;
import com.geek.webrouter.web.support.ReleaseAssets;
import com.geek.webrouter.web.support.ReleaseVersionComparator;
import com.geek.webrouter.web.support.UpdatePlatform;
import com.geek.webrouter.web.support.UpdateScriptGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 自动更新服务实现 — 查询 GitHub Release、下载并校验更新包、生成更新脚本。
 *
 * <p>检查接口永不抛异常；下载与校验失败才抛 {@link BusinessException}。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Service
public class UpdateServiceImpl implements UpdateService {

    /** Release 说明最大长度。 */
    private static final int MAX_RELEASE_NOTES_LENGTH = 4000;

    /** 未知版本占位值。 */
    private static final String UNKNOWN = "unknown";

    /** 更新日志文件名。 */
    private static final String UPDATE_LOG_FILE = "update.log";

    /** 默认的后台退出延迟（毫秒）。 */
    private static final long DEFAULT_EXIT_DELAY_MILLIS = 2000L;

    /** 摘要算法名。 */
    private static final String DIGEST_ALGORITHM = "sha256";

    private final AppVersionService appVersionService;
    private final ReleaseClient releaseClient;
    private final AssetDownloader assetDownloader;
    private final Path updatesDir;
    private final long exitDelayMillis;
    private final Runnable exitAction;

    @Autowired
    public UpdateServiceImpl(AppVersionService appVersionService, ReleaseClient releaseClient,
                             AssetDownloader assetDownloader) {
        this(appVersionService, releaseClient, assetDownloader, AppHomeResolver.resolveUpdatesDir(),
                DEFAULT_EXIT_DELAY_MILLIS, () -> System.exit(0));
    }

    /** 便于测试注入的完整构造函数。 */
    public UpdateServiceImpl(AppVersionService appVersionService, ReleaseClient releaseClient,
                      AssetDownloader assetDownloader, Path updatesDir, long exitDelayMillis,
                      Runnable exitAction) {
        this.appVersionService = appVersionService;
        this.releaseClient = releaseClient;
        this.assetDownloader = assetDownloader;
        this.updatesDir = updatesDir;
        this.exitDelayMillis = exitDelayMillis;
        this.exitAction = exitAction;
    }

    @Override
    public UpdateCheckResult check() {
        AppVersionInfo current = appVersionService.currentVersion();
        if (!current.updateSupported()) {
            return unavailable(current, "当前安装形态不支持自动更新（installMode=" + current.installMode()
                    + ", platform=" + current.platform() + "），请手动下载新版本");
        }
        if (current.repository() == null || current.repository().isBlank()) {
            return unavailable(current, "未配置更新仓库地址，无法检查更新");
        }
        List<ReleaseInfo> releases;
        try {
            releases = releaseClient.listReleases(current.repository());
        } catch (Exception e) {
            log.warn("检查更新失败: {}", e.getMessage());
            return unavailable(current, "检查更新失败：" + describeFailure(e));
        }
        if (releases == null || releases.isEmpty()) {
            return unavailable(current, "仓库 " + current.repository() + " 暂无可用的发布版本");
        }
        boolean acceptsPrerelease = AppVersionServiceImpl.acceptsPrerelease(current.updateChannel());
        ReleaseInfo release = selectRelease(releases, current.version(), acceptsPrerelease);
        if (release == null) {
            return unavailable(current, acceptsPrerelease
                    ? "当前已是最新版本（" + current.version() + "）"
                    : "当前已是最新正式版本（" + current.version() + "），预发布版本已跳过");
        }
        String latestVersion = ReleaseVersionComparator.normalize(release.tagName());
        Optional<ReleaseAsset> asset = selectAsset(release, current.installMode(), current.platform(), latestVersion);
        if (asset.isEmpty()) {
            return unavailable(current, "发布版本 " + latestVersion + " 没有匹配当前平台（installMode="
                    + current.installMode() + ", platform=" + current.platform() + "）的更新包");
        }
        ReleaseAsset matched = asset.get();
        return new UpdateCheckResult(current.version(), latestVersion, true, release.prerelease(),
                text(release.name(), release.tagName()), truncate(release.body()), text(release.publishedAt(), ""),
                text(release.htmlUrl(), ""), matched.name(), text(matched.browserDownloadUrl(), ""),
                matched.size(), text(matched.digest(), ""),
                "发现新版本 " + latestVersion + "（当前 " + current.version() + "），更新包 " + matched.name());
    }

    @Override
    public UpdateApplyResult apply(UpdateApplyRequest request) {
        UpdateCheckResult check = check();
        if (!check.updateAvailable()) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, check.message());
        }
        String requestedVersion = request == null ? null : request.version();
        if (requestedVersion != null && !requestedVersion.isBlank()
                && !ReleaseVersionComparator.normalize(requestedVersion).equals(check.latestVersion())) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST,
                    "目标版本 " + requestedVersion + " 不可更新，当前可用版本为 " + check.latestVersion());
        }
        Path archive = updatesDir.resolve(safeFileName(check.assetName()));
        try {
            assetDownloader.download(check.assetUrl(), archive);
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "下载更新包失败: " + e.getMessage());
        }
        verifyDigest(archive, check.assetDigest());
        Path script = writeUpdaterScript(check, archive);
        scheduleExit();
        log.info("更新包已就绪: {} -> {}", archive, script);
        return new UpdateApplyResult(check.latestVersion(), archive.toString(), script.toString(),
                "更新包已下载并校验通过，应用将在约 2 秒后退出，由 " + script.getFileName() + " 完成替换与重启");
    }

    /** 启动时检测上一次更新日志，便于排障。 */
    @EventListener(ApplicationReadyEvent.class)
    public void reportPreviousUpdateLog() {
        Path logFile = updatesDir.resolve(UPDATE_LOG_FILE);
        if (!Files.isRegularFile(logFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return;
            }
            int from = Math.max(0, lines.size() - 20);
            log.info("检测到上次更新日志 {}（共 {} 行）：{}", logFile, lines.size(),
                    String.join(" | ", lines.subList(from, lines.size())));
        } catch (IOException e) {
            log.warn("读取更新日志失败: {}", e.getMessage());
        }
    }

    private ReleaseInfo selectRelease(List<ReleaseInfo> releases, String currentVersion,
                                      boolean acceptsPrerelease) {
        return releases.stream()
                .filter(release -> !release.draft())
                .filter(release -> acceptsPrerelease || !release.prerelease())
                .filter(release -> ReleaseVersionComparator.isNewer(release.tagName(), currentVersion))
                .max(Comparator.comparing(ReleaseInfo::tagName, ReleaseVersionComparator::compare))
                .orElse(null);
    }

    /**
     * 选择与当前安装形态/平台匹配的资产。
     *
     * <p>先按冻结的资产名精确匹配，再放宽到「同版本 + 同平台标识 + 同扩展名」，
     * 避免误选其他平台的更新包。</p>
     */
    private Optional<ReleaseAsset> selectAsset(ReleaseInfo release, String installMode, String platform,
                                               String version) {
        List<String> expectedNames = ReleaseAssets.expectedAssetNames(installMode, version, platform);
        if (expectedNames == null || release.assets() == null) {
            return Optional.empty();
        }
        for (String expectedName : expectedNames) {
            Optional<ReleaseAsset> exact = release.assets().stream()
                    .filter(asset -> asset.name() != null && asset.name().equalsIgnoreCase(expectedName))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
        }
        String token = ReleaseAssets.platformToken(platform);
        if (token == null) {
            return Optional.empty();
        }
        String tolerantPrefix = ReleaseAssets.assetBaseName(version) + "-" + token;
        String extension = ReleaseAssets.appSuffix(token);
        return release.assets().stream()
                .filter(asset -> asset.name() != null
                        && asset.name().startsWith(tolerantPrefix)
                        && asset.name().endsWith(extension))
                .findFirst();
    }

    private void verifyDigest(Path archive, String digest) {
        String expected = normalizeDigest(digest);
        if (expected == null) {
            log.info("发布方未提供 SHA-256 摘要，跳过校验: {}", archive.getFileName());
            return;
        }
        String actual = sha256(archive);
        if (!expected.equalsIgnoreCase(actual)) {
            deleteQuietly(archive);
            log.error("更新包摘要不一致: expected={}, actual={}", expected, actual);
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "更新包校验失败（SHA-256 不一致），已删除下载文件");
        }
        log.info("更新包 SHA-256 校验通过: {}", archive.getFileName());
    }

    private Path writeUpdaterScript(UpdateCheckResult check, Path archive) {
        AppVersionInfo current = appVersionService.currentVersion();
        UpdatePlatform platform = UpdatePlatform.resolve(current.platform());
        String homeDir = AppHomeResolver.resolveHome().toAbsolutePath().normalize().toString();
        String absoluteUpdatesDir = updatesDir.toAbsolutePath().normalize().toString();
        String content = UpdateScriptGenerator.generate(current.installMode(), platform, check.latestVersion(),
                current.version(), check.assetName(), archive.toAbsolutePath().normalize().toString(),
                homeDir, absoluteUpdatesDir);
        Path scriptPath = updatesDir.resolve(UpdateScriptGenerator.scriptFileName(platform));
        try {
            Files.createDirectories(updatesDir);
            Files.writeString(scriptPath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            if (!platform.isWindows()) {
                Files.setPosixFilePermissions(scriptPath, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "生成更新脚本失败: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warn("当前文件系统不支持设置脚本可执行权限: {}", e.getMessage());
        }
        return scriptPath;
    }

    private void scheduleExit() {
        Thread exitThread = new Thread(() -> {
            try {
                Thread.sleep(exitDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log.info("应用即将退出以完成自动更新");
            exitAction.run();
        }, "wrouter-update-exit");
        exitThread.setDaemon(false);
        exitThread.start();
    }

    private UpdateCheckResult unavailable(AppVersionInfo current, String message) {
        log.info("更新检查结果: updateAvailable=false, message={}", message);
        return new UpdateCheckResult(current.version(), UNKNOWN, false, false, "", "", "", "", "", "", 0L, "",
                message);
    }

    /** 解析摘要中的十六进制值；不支持或缺失时返回 null。 */
    static String normalizeDigest(String digest) {
        if (digest == null || digest.isBlank()) {
            return null;
        }
        String value = digest.trim();
        int separator = value.indexOf(':');
        if (separator >= 0) {
            String algorithm = value.substring(0, separator).trim();
            if (!DIGEST_ALGORITHM.equalsIgnoreCase(algorithm)) {
                log.warn("暂不支持的摘要算法 [{}]，跳过校验", algorithm);
                return null;
            }
            value = value.substring(separator + 1).trim();
        }
        return value.isEmpty() ? null : value;
    }

    /** 计算文件 SHA-256。 */
    static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "计算更新包摘要失败: " + e.getMessage());
        }
    }

    /** 防止资产名穿越目录。 */
    static String safeFileName(String assetName) {
        if (assetName == null || assetName.isBlank()
                || assetName.contains("/") || assetName.contains("\\") || assetName.contains("..")) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "更新包文件名不合法: " + assetName);
        }
        return assetName;
    }

    private static String truncate(String notes) {
        if (notes == null) {
            return "";
        }
        String trimmed = notes.trim();
        return trimmed.length() <= MAX_RELEASE_NOTES_LENGTH ? trimmed : trimmed.substring(0, MAX_RELEASE_NOTES_LENGTH);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String describeFailure(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "网络请求失败（" + e.getClass().getSimpleName() + "）";
        }
        if (message.contains("UnknownHost") || message.contains("Connection") || message.contains("timed out")) {
            return "无法连接 GitHub（" + message + "）";
        }
        return message;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            log.warn("删除文件失败: {}", path);
        }
    }
}
