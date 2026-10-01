# 用户指南

## 管理后台

<http://localhost:9999/admin> 是一套深色开发者控制台，主要能力：

- **KPI 行**：路由总数 / 运行中 / 已停用 / 请求数·分钟 / 平均延迟；前三项点击即筛选。
- **工具栏**：搜索（名称 / Path / Target / 监听地址 / id）、状态筛选、排序、导出、导入、新增。
- **路由卡片**：状态点、Path 标签、Target、本地端口、请求数·分钟、平均延迟与 30 分钟流量曲线。
- **详情抽屉**：路由拓扑、基本信息、运行状态、最近日志，另有配置 / 日志 / 指标标签页。
- **日志视图**：实时流、Top 路径、单耗时 Top、诊断分析。

保存、启停或删除路由后，Gateway 与本地端口代理会立即刷新。

视觉与交互规范见 [UI 设计系统](../docs/UI-DESIGN-SYSTEM.md)，文件映射见 [UI 实现映射](../docs/UI-IMPLEMENTATION.md)。

## 路由字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 系统生成 | 内部 ID，同时是配置文件名与 Gateway routeId 基础值，格式 `route-yyyyMMddHHmmss-xxxxxx`。 |
| `name` | 是 | 展示名称，≤50 字，全局唯一。 |
| `pathPrefixes` | 否 | 路径前缀列表，每项以 `/` 开头；同路由内不重复，不同路由可复用。 |
| `pathPrefix` | 否 | 旧字段，写回时始终与 `pathPrefixes[0]` 同步。 |
| `targetUrl` | 是 | 默认地址（兜底），保存前归一化为带协议 URL。 |
| `accessPageBaseUrl` | 配置前缀时必填 | 代理地址，**只被本地端口代理使用**。 |
| `accessPage` | 否 | 访问页，后台「访问」按钮优先打开它。 |
| `localIp` | 否 | 本地监听 IP，为空时默认 `127.0.0.1`。 |
| `localPort` | 管理 API 必填 | 本地监听端口，`1-65535`。 |
| `enabled` | 否 | 禁用后保留配置文件，但不注册 Gateway 路由、不启动本地代理。 |

## 校验规则

| 规则 | 违反时 |
| --- | --- |
| `id` 匹配 `^[a-zA-Z0-9_-]+$` | `BAD_REQUEST` |
| `name` 非空、≤50 字、全局唯一 | `BAD_REQUEST` / `DUPLICATE_NAME` |
| 前缀匹配 `^/[-a-zA-Z0-9_/]*$`，同路由内不重复；末尾 `/` 会被移除 | `BAD_REQUEST` / `DUPLICATE_PREFIX` |
| `targetUrl` 匹配 `^(https?://)?[-a-zA-Z0-9.]+:\d{1,5}$` | `BAD_REQUEST` |
| `accessPageBaseUrl` 同上，且配置前缀时必填 | `BAD_REQUEST` |
| `localIp` 为空 / `localhost` / 合法 IPv4 | `BAD_REQUEST` |
| `localPort` 范围 `1-65535` | `BAD_REQUEST` |
| 启用路由之间 `localIp:localPort` 不重复 | `DUPLICATE_LOCAL_BINDING` |

前缀冲突只判断「完全相同」，不做父子包含判断。

## 两条入口的转发语义差异

| 项目 | Gateway 转发 | 本地端口代理 |
| --- | --- | --- |
| 入口 | 主端口 `:9999` | 该路由的 `localIp:localPort` |
| 前缀处理 | 按层级 `StripPrefix` | **不剥离**，保留原始 URI（含 query） |
| 上游选择 | 配置 `localPort` 时交给本地代理，否则走 `targetUrl` | 命中 `pathPrefixes` 走 `accessPageBaseUrl`，否则走 `targetUrl` |
| 未命中路径 | 不命中该 Gateway 路由 | 转发到 `targetUrl` |

> 要点：**`accessPageBaseUrl` 只影响本地端口代理**。配置了 `localPort` 的路由会改写 Gateway 目标并把剥离层级降为 0，由本地代理统一决定最终上游。

## 导入与导出

- 导出 `GET /admin/api/routes/export`，下载为 `wrouter-routes-yyyyMMdd-HHmmss.json`。
- 导入 `POST /admin/api/routes/import` 总是**新增**（重新生成 ID），不覆盖同名路由；批次内冲突会整体回滚。

## 请求观测

| 指标 | 口径 |
| --- | --- |
| `totalRequests` / `failedRequests` | 累计请求数与 `status >= 400` 累计 |
| `slowRequests` | 耗时 ≥ 1000ms 累计 |
| `requestsLastMinute` / `failedLastMinute` | 最近 60 秒滚动值（1 秒桶 × 60） |
| `trafficBuckets` | 最近 30 分钟每分钟请求数，由旧到新，长度恒为 30 |
| `recentLogs` / `durationTopLogs` | 最近 **100** 条与单次耗时 Top **100** |
| `uniqueIpCount` | 去重 IP 数 |

派生 routeId `<id>__<n>` 在日志、SSE 与指标中统一归并到基础 ID。统计全部在内存中，应用重启后清空。

观测口径的完整定义见 [核心功能说明](../docs/CORE-FEATURES.md)。
