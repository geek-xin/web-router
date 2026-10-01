# 变更说明

本文记录 `wrouter` 正式发布版本的主要能力和行为变化。

## [未发布] - 2026-09-30

### 版本定位

- 按新的 wrouter 视觉语言重写管理后台：从浅色 chunky 风格切换为深色开发者网络控制台，并沉淀为可执行的 UI 规范文档与契约测试。

### 管理后台

- 全新应用外壳：208px 侧边栏（导航 / 运行状态 / 系统设置 / 网关运行卡）、56px 顶栏（Gateway 状态胶囊 + 工具入口）、KPI 行（路由总数 / 运行中 / 已停用 / 请求数·分钟 / 平均延迟）。
- 路由列表保持卡片式：每卡包含状态、Path 前缀（超过 3 个折叠为 +N）、Target、本地端口、每分钟请求数、平均延迟与 30 分钟流量 Sparkline；按序号分配强调色，停用卡片降权。
- 详情抽屉改为 Topology-first：① 路由拓扑 ② 基本信息 ③ 运行状态 ④ 最近日志，并保留配置 / 日志 / 指标标签页与常驻操作栏。
- 路由拓扑固定表达 `Client → wrouter → 本地端口 → 目标服务`，含流动连线与未命中虚线分支。
- 新增日志视图（侧边栏进入）：按路由查看实时流、Top 路径、单耗时 Top 与诊断分析。
- 新增品牌符号（节点 + 连线）、极淡网络背景装饰、状态点呼吸与抽屉滑入动效，并遵循 `prefers-reduced-motion`。
- 工具栏改为搜索 + 状态筛选 + 排序（最近创建 / 名称 / 流量 / 延迟）+ 导出 / 导入 / 新增。

### 流量指标

- `ProxyRequestLogService` 新增滚动时间窗：最近 60 秒请求数、最近 60 秒失败数、最近 30 分钟每分钟请求数序列，以及累计平均延迟。
- `ProxyRequestLogSnapshot` 与新增 `RouteTrafficMetrics` 暴露 `requestsLastMinute`、`failedLastMinute`、`averageDurationMs`、`trafficBuckets`。
- 新增 `GET /admin/api/proxy-logs/metrics`，一次返回全部路由的紧凑指标，避免列表页逐条拉取日志明细。
- 路由状态按最近一分钟失败率派生：≤5% 运行中，>5% 异常，≥20% 错误；停用路由始终为已停用。

### 文档与测试

- 新增 `docs/CORE-FEATURES.md`：核心功能的权威说明，覆盖数据模型、路由配置管理、Gateway 动态转发、本地端口代理、请求观测与流量指标、管理 API、校验规则、关键不变量与已知边界。
- 新增 `docs/UI-DESIGN-SYSTEM.md`（色彩、排版、布局、组件、动效、拓扑、验收清单）与 `docs/UI-IMPLEMENTATION.md`（规范到文件、类名与数据契约的映射）。
- 新增 `CoreFeaturesDocContractTest`，校验核心功能文档中的阈值、接口路径、错误码与实现保持一致，并强制文档互链。
- 清理 `docs/` 下旧设计稿与旧截图，README 更新为新控制台截图与视觉语言说明。
- 新增 `AdminUiContractTest`，把设计系统与实现映射中的关键契约固化为断言，替换原 chunky 风格断言测试。
- 前端新增 `route-metrics` 模块与单测，覆盖指标格式化、Sparkline 几何与日志兜底序列。

### 多平台打包与交付

- 新增 `scripts/build-package.sh`：按类型产出单平台产物——`jar`（JAR 分发包）、`app-image`（jpackage 免安装镜像）、`installer`（macOS `dmg` / Linux `deb`+`rpm` / Windows `exe`），内置运行时无需目标机安装 JDK。
- 新增 `scripts/build-release.sh`：编排全部类型并按契约命名发布到 `target/release/`，同时产出 `SHA256SUMS.txt` 与 `release-manifest.txt`；任一资产名不符合契约即失败退出。
- 新增 `.github/workflows/release.yml`：tag `v*` 或手动触发后，在 macos-14 / macos-13 / ubuntu-latest / windows-latest 矩阵上并行构建，汇总为 GitHub Release。
- 资产命名契约由 `ReleaseAssets` 统一实现，打包脚本与 CI 必须与之一致：`wrouter-<version>-jar.zip`、`wrouter-<version>-<platform>-app.zip`（Linux 为 `-app.tar.gz`）、`wrouter-<version>-<platform>.dmg|deb|rpm|exe`。
- 应用根目录改由 `AppHomeResolver` 解析（`wrouter.home` → `WROUTER_HOME` → `user.dir`），app-image 启动参数固定 `-Dwrouter.home=$APPDIR`；传统 JAR 形态仍默认取启动工作目录，原有部署方式不变。

### 版本号与自动更新

- 新增 `GET /admin/api/version`：返回版本、构建时间、运行平台、安装方式、更新通道与仓库；构建元信息由 `pom.xml` 资源过滤注入 `version.properties`。
- 新增 `GET /admin/api/update/check`：查询 GitHub Release，按更新通道过滤草稿/预发布，按命名契约选包；任何异常都写进 `message`，接口本身始终 `success=true`。
- 新增 `POST /admin/api/update/apply`：下载 → 校验 SHA-256 → 生成平台更新脚本到 `updates/` → 约 2 秒后退出；不可更新时抛 `BusinessException`，摘要不一致时删除已下载文件。
- 新增 `UpdateScriptGenerator`：生成 `updates/apply-update.sh`（类 Unix）与 `apply-update.bat`（Windows），等待旧进程退出 → 备份到 `updates/backup-<旧版本>/` → 解压替换 → 重启，任一步失败自动回滚并写 `updates/update.log`。
- 管理后台新增「版本与更新」抽屉：侧边栏与顶栏常驻显示版本号，抽屉展示构建时间/平台/安装方式/更新通道，支持检查更新与一键更新。

### 文档与测试（打包）

- 新增 `docs/PACKAGING.md`：交付形态对照、资产命名契约、本地构建、应用根目录、自动更新流程与 CI 发布。
- 新增 `AppVersionServiceTest`、`UpdateServiceTest`、`ReleaseAssetsTest`、`ReleaseVersionComparatorTest`、`UpdateScriptGeneratorTest`、`AppHomeResolverTest`、`AppVersionControllerTest` 覆盖版本与更新链路。

## [1.2.0] - 2026-08-08

### 版本定位

- 新增路由配置导入/导出、管理后台整体等比缩放与卡片三列布局、卡片直达日志入口，并统一请求日志失败/慢请求统计口径。

### 管理后台

- 路由配置支持一键导出全部配置为 JSON，以及从 JSON 文件批量导入（新增 / 更新）。
- 页面 100% 时按视口等比缩放全部组件（参考 web-sim 的 `zoom` 机制），桌面常见宽度下路由卡片一行支持三列。
- 路由卡片新增「日志」按钮，直接打开该路由的实时日志弹窗；详情页移除「实时日志」标签页，统一从卡片进入。
- 修复日志弹窗中右上角关闭按钮与刷新按钮重叠的问题。
- 详情页、表单等交互保持不变，继续支持编辑、复制、访问、删除与 JSON 预览。

### 请求日志

- 失败请求数和慢请求数改为全量累计计数（含按基础 routeId 归并），与成功率口径保持一致；SSE 增量同步累加。
- 新增前后端回归测试覆盖窗口外失败/慢请求计数。

## [1.1.0] - 2026-06-27

### 版本定位

- 发布新版本号 `1.1.0`，用于承载当前 React 管理后台、访问页代理、配置目录展示、路由日志和发布文档刷新。
- 同步 Maven 项目版本、前端 package 版本、发布包文件名和公开文档版本说明。

### 管理后台

- 管理后台升级为 `frontend/` 下的 React + Vite 源码，并构建到 `src/main/resources/static/admin/` 由 Spring Boot 提供。
- 首页采用新的路由控制台布局，支持配置总数、启用/停用路由筛选和配置目录绝对路径显示/隐藏。
- 路由卡片、详情抽屉、表单和 JSON 预览整合为同一套交互，便于在列表与详情之间编辑、复制、访问和删除路由。
- 新增浏览器标签页图标和最新管理后台截图。

### 路由配置与本地代理

- 路由配置新增 `accessPageBaseUrl`（代理地址）和 `accessPage`（访问页）。
- `targetUrl` 明确为“默认地址（兜底）”；本地监听请求命中 `pathPrefixes` 时转发到 `accessPageBaseUrl`，未命中时转发到 `targetUrl`。
- 当前管理 API/后台表单要求填写 `localPort`；配置路径前缀时要求填写 `accessPageBaseUrl`。
- `targetUrl` 可在不同路由之间重复，方便复制路由后复用默认地址。
- 后台继续保持旧字段 `pathPrefix` 与 `pathPrefixes[0]` 同步，兼容旧 JSON。

### 请求日志

- 路由日志类型补充 `accessAddress`，用于展示最终实际访问的上游 `host:port`。
- 日志面板补充慢请求 Top、实时日志和诊断信息展示，继续归并多前缀派生 routeId。

### 管理 API 与文档

- 路由配置 API 对外文档统一按 routeId 说明：`/admin/api/routes/{routeId}`、`/admin/api/routes/{routeId}/raw`、`PUT` 和 `DELETE` 同理。
- 全量整理 `README.md`、`USAGE.md` 和 GitHub Wiki 文档，使字段说明、校验规则、发布包路径和前端构建方式与 `1.1.0` 对齐。

## [1.0.0] - 2026-06-07

### 核心功能

- 基于 Spring Boot 3.5.2、Spring Cloud Gateway 2024.0.1、Reactor Netty 和 Thymeleaf 构建轻量 Web 路由代理。
- 默认服务绑定 `127.0.0.1:9999`，`GET /` 重定向到 `/admin`。
- 提供 Thymeleaf 管理后台和统一 `Result<T>` 管理 API。
- 路由配置以本地 JSON 文件持久化到 `config/routes/<id>.json`，无需数据库。
- 创建路由时自动生成 `route-yyyyMMddHHmmss-xxxxxx` 格式 ID。
- `listAll()` 按配置文件最后修改时间倒序展示。

### 路由配置能力

- 支持单条路由配置多个路径前缀 `pathPrefixes`。
- 保留旧字段 `pathPrefix`，并与 `pathPrefixes[0]` 同步，兼容旧 JSON。
- 路径前缀保存前会规范化，非根路径末尾 `/` 会被移除。
- 支持目标地址归一化：`host:port` 自动补为 `http://host:port`。
- 支持展示名称、路径前缀、目标地址和启用本地监听绑定冲突校验。
- 支持 `localIp` 独立格式校验，即使未配置 `localPort`。

### Gateway 动态代理

- 启用路由会按每个 `pathPrefixes` 项注册 Gateway 路由。
- 多路径前缀派生 routeId：第一条使用基础 `id`，后续使用 `<id>__<index>`。
- Path 谓词规则：`/` -> `/**`，`/test` -> `/test/**`。
- 按前缀路径层级生成 `StripPrefix`，例如 `/test/api` -> `StripPrefix=2`。
- 新增、更新、删除配置后动态刷新 Gateway，无需重启应用。
- 动态刷新按快照差异处理路由，减少未变化路由的删除/重建。

### 本地端口代理

- 支持为单条启用路由配置独立 `localIp:localPort` 本地监听。
- `localIp` 为空时默认使用 `127.0.0.1`。
- 仅 `enabled=true` 且设置 `localPort` 的路由会启动本地代理。
- 本地端口代理按当前路由 `pathPrefixes` 做入口隔离；未命中前缀的请求返回 `404`。
- 本地端口代理保留原始请求 URI，不执行 `StripPrefix`。
- 转发时透传请求方法、请求体和大部分 Header，并将 `Host` 改为目标地址 Host。
- 代理响应设置 `Connection: close` 和 `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`，降低旧连接和浏览器缓存影响。
- 代理失败时返回 HTTP 502，响应文本为 `Proxy request failed`。
- 刷新本地代理时，同一监听绑定会更新内存路由快照，避免无谓重绑定；禁用或移除绑定时停止对应监听。

### 请求日志与管理后台

- Gateway 代理和本地端口代理都会记录请求日志。
- 支持总请求数、去重 IP 数、按 IP 聚合、Top 路径、最近 100 条日志统计。
- 多路径派生 routeId 会归并到基础路由 ID 展示和统计。
- 支持全部路由和单路由 SSE 实时日志流。
- 管理后台支持查看路由日志弹窗，包含摘要、Top 路径、最近请求和实时刷新开关。
- 优化路由日志弹窗布局：固定统计区域、滚动路径统计表、底部关闭按钮和打开/关闭过渡动画。

### 管理 API

- `GET /admin/api/routes`：查询全部路由。
- `GET /admin/api/routes/{routeId}`：查询单条路由。
- `GET /admin/api/routes/{routeId}/raw`：读取原始 JSON 文件内容。
- `POST /admin/api/routes`：创建路由并刷新代理。
- `PUT /admin/api/routes/{routeId}`：更新路由并刷新代理。
- `DELETE /admin/api/routes/{routeId}`：删除路由并刷新代理。
- `GET /admin/api/proxy-logs`：查询全部路由日志快照。
- `GET /admin/api/proxy-logs/routes/{routeId}`：查询指定路由日志快照。
- `GET /admin/api/proxy-logs/stream`：订阅全部路由实时日志。
- `GET /admin/api/proxy-logs/routes/{routeId}/stream`：订阅指定路由实时日志。

### 发布与文档

- 新增 `scripts/build-dist.sh`，用于编译、打包 Spring Boot JAR，并生成包含 Linux/macOS 与 Windows 一键后台启动/停止脚本、后台配置文件和路由配置目录的 `target/*.tar.gz` 发布包，同时复制到 `target/dist/`。
- 新增 GitHub Wiki 文档结构：首页、快速开始、用户指南、架构、API、排障、开发指南和侧边栏。
- 重新整理 `README.md`、`USAGE.md` 和 `CHANGELOG.md`，使功能说明与当前实现保持一致。

### 行为约定

- 业务异常由 `GlobalExceptionHandler` 转为 HTTP 200 + `success=false`。
- 参数校验异常返回 HTTP 200 + `success=false` + `code=400`。
- 未捕获异常返回 HTTP 500 + `success=false`。
- 前端 `fetchJson()` 依赖上述响应语义。
- 当前项目无 npm 构建流程，前端资源直接位于 `src/main/resources/templates` 与 `src/main/resources/static`。
