package com.geek.webrouter.web.model.dto;

/**
 * 单个历史版本条目 — 对应一次 GitHub Release。
 *
 * @param version     版本号（已去掉 v 前缀）
 * @param name        发布名称
 * @param publishedAt 发布时间（ISO-8601）
 * @param releaseUrl  Release 页面地址
 * @param prerelease  是否为预发布版本
 * @param current     是否为当前运行版本
 * @param notes       修订记录（Release 说明）
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record ReleaseHistoryItem(String version, String name, String publishedAt, String releaseUrl,
                                 boolean prerelease, boolean current, String notes) {
}
