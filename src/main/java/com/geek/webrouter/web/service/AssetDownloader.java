package com.geek.webrouter.web.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 更新包下载器抽象 — 便于测试替换，避免真实联网。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public interface AssetDownloader {

    /** 下载资产到目标路径，返回目标路径。 */
    Path download(String assetUrl, Path target) throws IOException;
}
