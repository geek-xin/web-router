package com.geek.webrouter.web.model.dto;

/**
 * GitHub Release 资产。
 *
 * @param name               文件名
 * @param browserDownloadUrl  浏览器下载地址
 * @param size               字节数
 * @param digest             GitHub 提供的摘要，形如 sha256:...，可能为空
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record ReleaseAsset(String name, String browserDownloadUrl, long size, String digest) {
}
