<div align="center">

# 用户指南

**日常操作、字段含义与两条入口的语义差异**

</div>

---

## 控制台导览

<http://localhost:9999/admin> 是一套开发者网络控制台，一屏之内回答「哪条路由、去哪、通不通、多少流量」。

| 区域 | 能做什么 |
| --- | --- |
| 概览带 | 本分钟请求数、最近 30 分钟曲线、路由总数 / 运行中 / 已停用（前三项点击即筛选） |
| 工具栏 | 搜索（名称 / Path / Target / 监听地址 / id）、状态筛选、排序、卡片·列表切换、导出、导入、新增 |
| 路由卡片 | 状态点、Path 标签、Target、本地端口、请求数·分钟、平均延迟、30 分钟 Sparkline |
| 路由详情 | 页内视图（非抽屉）：① 路由拓扑 ② 基本信息 ③ 运行状态 ④ 最近日志，另有配置 / 日志 / 指标标签页 |
| 日志视图 | 实时流、Top 路径、单耗时 Top、诊断分析；默认聚合全部路由 |
| 版本与更新 | 侧边栏运行卡的版本号 → 抽屉：当前安装、自动更新、手动更新、历史版本 |
| 更新日志铃铛 | 顶栏铃铛展示最新版本的修订内容；未读（有新版本）才亮红点 |

保存、启停或删除路由后，Gateway 与本地端口代理立即刷新，不需要重启。
视觉与交互规范见 [UI 设计系统](https://github.com/geek-xin/web-router/blob/main/docs/UI-DESIGN-SYSTEM.md)。

## 路由字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 系统生成 | 内部 ID，同时是配置文件名与 Gateway routeId 基础值，格式 `route-yyyyMMddHHmmss-xxxxxx` |
| `name` | ✅ | 展示名称，≤50 字，全局唯一 |
| `pathPrefixes` | — | 路径前缀列表，每项以 `/` 开头；同路由内不重复，不同路由可复用 |
| `pathPrefix` | — | 旧字段，写回时始终与 `pathPrefixes[0]` 同步 |
| `targetUrl` | ✅ | 默认地址（兜底），保存前归一化为带协议 URL；允许与其他路由重复 |
| `accessPageBaseUrl` | 配前缀时必填 | 代理地址，**只被本地端口代理使用** |
| `accessPage` | — | 访问页，后台「访问」按钮优先打开它 |
| `localIp` | — | 本地监听 IP，为空时默认 `127.0.0.1` |
| `localPort` | 管理 API 必填 | 本地监听端口，`1-65535` |
| `enabled` | — | 禁用后保留配置文件，但不注册 Gateway 路由、不启动本地代理 |

## 两条入口的转发语义差异

这是使用 wrouter 时最需要记住的一点：

| 项目 | Gateway 转发 | 本地端口代理 |
| --- | --- | --- |
| 入口 | 主端口 `:9999` | 该路由的 `localIp:localPort` |
| 匹配 | 按 `pathPrefixes` 注册 Path 谓词 | 端口只属于这条路由，全部请求都进 |
| 前缀处理 | 按层级 `StripPrefix` | **不剥离**，保留原始 URI（含 query） |
| 上游选择 | 配置 `localPort` 时交给本地代理，否则走 `targetUrl` | 命中 `pathPrefixes` 走 `accessPageBaseUrl`，否则走 `targetUrl` |
| 未命中路径 | 不命中该 Gateway 路由 | 转发到 `targetUrl` |
| 失败响应 | Gateway 默认错误页 | HTTP 502 + 文本 `Proxy request failed` |

> **要点**：`accessPageBaseUrl` 只影响本地端口代理。配置了 `localPort` 的路由会改写 Gateway 目标并把剥离层级降为 0，
> 由本地代理统一决定最终上游。

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

## 导入与导出

- 导出：`GET /admin/api/routes/export`，下载为 `wrouter-routes-yyyyMMdd-HHmmss.json`。
- 导入：`POST /admin/api/routes/import` 总是**新增**（重新生成 ID），不覆盖同名路由；批次内冲突整体回滚。

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

> 同时配置 `localPort` 的路由，一次客户端请求可能产生**两条**日志（Gateway 一条 + 本地代理一条）。

## 版本与更新

| 位置 | 内容 |
| --- | --- |
| 侧边栏运行卡 | 状态点 + 版本号，点击打开「版本与更新」抽屉 |
| 顶栏铃铛 | 只读更新日志：最新版本修订内容、未读点、跳转完整历史 |
| 版本抽屉 | 当前安装、自动更新状态（在途请求 / 空闲判定 / 待应用版本 / 静默阈值）、手动检查与一键更新、历史版本 |

自动更新是「无感」三段式：静默检查 → 预下载（不退出）→ 仅在**无在途请求且静默达标**时退出重启。
失败会自动回滚，日志写在应用根目录的 `updates/update.log`。

观测口径的完整定义见
[核心功能说明](https://github.com/geek-xin/web-router/blob/main/docs/CORE-FEATURES.md)。
