package com.geek.webrouter.web.model.dto;

/**
 * GitHub Release 数据。
 *
 * @param tagName     tag 名称，如 v1.4.0
 * @param name        Release 名称
 * @param body        Release 说明
 * @param publishedAt 发布时间
 * @param htmlUrl     Release 页面地址
 * @param prerelease  是否为预发布版本
 * @param draft       是否为草稿
 * @param assets      资产列表
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record ReleaseInfo(String tagName, String name, String body, String publishedAt, String htmlUrl,
                          boolean prerelease, boolean draft, java.util.List<ReleaseAsset> assets) {
}
