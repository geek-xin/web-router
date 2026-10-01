package com.geek.webrouter.web.model.dto;

/**
 * 应用版本信息。
 *
 * @param version         当前版本号
 * @param buildTime       构建时间（ISO-8601）
 * @param platform        目标平台，如 macos-arm64
 * @param installMode     安装形态：jar | app-image
 * @param updateSupported 当前形态是否支持自动更新
 * @param updateChannel   更新通道：stable | prerelease
 * @param repository      GitHub 仓库，形如 owner/repo
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record AppVersionInfo(String version, String buildTime, String platform, String installMode,
                             boolean updateSupported, String updateChannel, String repository) {
}
