package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.ReleaseInfo;

import java.io.InputStream;
import java.util.List;

/**
 * GitHub Release 访问抽象 — 生产实现走 HTTP，测试注入假实现，避免真实联网。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public interface ReleaseClient {

    /** 拉取仓库的发布列表（按 GitHub 返回顺序，通常为创建时间倒序）。 */
    List<ReleaseInfo> listReleases(String repository) throws Exception;

    /** 下载资产内容。 */
    InputStream openAsset(String assetUrl) throws Exception;
}
