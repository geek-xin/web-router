package com.geek.webrouter.web.model.dto;

/**
 * 应用更新请求。
 *
 * @param version 目标版本，缺省表示使用检查到的最新版本
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record UpdateApplyRequest(String version) {
}
