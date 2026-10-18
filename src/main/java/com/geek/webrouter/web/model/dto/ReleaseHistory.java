package com.geek.webrouter.web.model.dto;

import java.util.List;

/**
 * 历史版本与修订记录。
 *
 * <p>与「检查更新」不同：这里列出的是**全部已发布版本**（含预发布与当前版本），
 * 用于在管理后台回溯每个版本改了什么。查询失败时 {@code releases} 为空、
 * {@code message} 说明原因，接口本身仍成功。</p>
 *
 * @param currentVersion 当前运行版本
 * @param repository     查询的 GitHub 仓库，形如 owner/repo
 * @param releases       历史版本，按版本号从新到旧
 * @param message        无数据或失败时的说明，正常时为空
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record ReleaseHistory(String currentVersion, String repository, List<ReleaseHistoryItem> releases,
                             String message) {
}
