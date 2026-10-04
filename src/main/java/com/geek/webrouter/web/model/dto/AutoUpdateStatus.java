package com.geek.webrouter.web.model.dto;

/**
 * 自动更新运行状态。
 *
 * @param autoEnabled     自动更新总开关
 * @param autoApply       是否会在空闲窗口自动应用
 * @param quietSeconds    判定空闲所需的静默秒数
 * @param inFlightCount   当前在途代理请求数
 * @param idleMillis      距离最后一次代理活动的毫秒数
 * @param idle            当前是否处于可应用的空闲窗口
 * @param pendingVersion  已暂存待应用的版本，无则为空串
 * @param pendingAsset    已暂存的更新包文件名，无则为空串
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public record AutoUpdateStatus(boolean autoEnabled, boolean autoApply, long quietSeconds, int inFlightCount,
                               long idleMillis, boolean idle, String pendingVersion, String pendingAsset) {
}
