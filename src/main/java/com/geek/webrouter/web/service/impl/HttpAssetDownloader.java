package com.geek.webrouter.web.service.impl;

import com.geek.webrouter.web.service.AssetDownloader;
import com.geek.webrouter.web.service.ReleaseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 更新包下载器 — 通过 {@link ReleaseClient} 拉取资产并落盘。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Component
@RequiredArgsConstructor
public class HttpAssetDownloader implements AssetDownloader {

    private final ReleaseClient releaseClient;

    @Override
    public Path download(String assetUrl, Path target) throws IOException {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new IOException("创建更新目录失败: " + target.getParent(), e);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream input = releaseClient.openAsset(assetUrl)) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw e;
        } catch (Exception e) {
            deleteQuietly(temporary);
            throw new IOException("下载更新包失败: " + e.getMessage(), e);
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理临时文件失败不影响主流程
        }
    }
}
