// 应用元信息：版本号与运行时常量。
// 版本号在构建时由 Maven 注入 version.properties；这里的常量只作为接口不可用时的兜底。
export const APP_VERSION = '1.3.0';

/** 管理后台与 Gateway 共用监听地址，见 src/main/resources/application.yml。 */
export const GATEWAY_ENDPOINT = '127.0.0.1:9999';

/** GitHub 仓库，用于版本弹窗与更新检查的兜底展示。 */
export const APP_REPOSITORY = 'geek-xin/web-router';

/** 流量 Sparkline 的时间窗，与后端 TRAFFIC_BUCKET_COUNT 保持一致。 */
export const TRAFFIC_WINDOW_MINUTES = 30;
