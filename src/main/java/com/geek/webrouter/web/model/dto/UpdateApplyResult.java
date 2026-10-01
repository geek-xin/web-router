package com.geek.webrouter.web.model.dto;

/**
 * 应用更新结果。
 *
 * @param version       目标版本
 * @param archivePath   已下载的更新包绝对路径
 * @param updaterScript 生成的更新脚本绝对路径
 * @param message       人类可读的说明信息
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record UpdateApplyResult(String version, String archivePath, String updaterScript, String message) {
}
