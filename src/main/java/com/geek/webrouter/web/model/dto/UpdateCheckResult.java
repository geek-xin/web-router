package com.geek.webrouter.web.model.dto;

/**
 * 更新检查结果。
 *
 * @param currentVersion  当前版本
 * @param latestVersion   远端最新版本（未找到时为 unknown）
 * @param updateAvailable 是否存在可用更新
 * @param prerelease      命中的版本是否为预发布版本
 * @param releaseName     Release 名称
 * @param releaseNotes    Release 说明（最长 4000 字符）
 * @param publishedAt     Release 发布时间
 * @param releaseUrl      Release 页面地址
 * @param assetName       匹配到的资产文件名
 * @param assetUrl        资产下载地址
 * @param assetSize       资产字节数，未知为 0
 * @param assetDigest     资产摘要，形如 sha256:...，未知为空串
 * @param message         人类可读的说明信息
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record UpdateCheckResult(String currentVersion, String latestVersion, boolean updateAvailable,
                                boolean prerelease, String releaseName, String releaseNotes, String publishedAt,
                                String releaseUrl, String assetName, String assetUrl, long assetSize,
                                String assetDigest, String message) {
}
