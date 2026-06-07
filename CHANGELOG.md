# 变更说明

本文记录 `web-router` 当前开发版本的主要能力和行为变化。

## [1.0.0-SNAPSHOT] - 当前开发版本

### 核心功能

- 基于 Spring Boot 3.5.2、Spring Cloud Gateway 2024.0.1、Reactor Netty 和 Thymeleaf 构建轻量 Web 路由代理。
- 默认服务绑定 `127.0.0.1:8090`，`GET /` 重定向到 `/admin`。
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
- `GET /admin/api/routes/{id}`：查询单条路由。
- `GET /admin/api/routes/{id}/raw`：读取原始 JSON 文件内容。
- `POST /admin/api/routes`：创建路由并刷新代理。
- `PUT /admin/api/routes/{id}`：更新路由并刷新代理。
- `DELETE /admin/api/routes/{id}`：删除路由并刷新代理。
- `GET /admin/api/proxy-logs`：查询全部路由日志快照。
- `GET /admin/api/proxy-logs/routes/{routeId}`：查询指定路由日志快照。
- `GET /admin/api/proxy-logs/stream`：订阅全部路由实时日志。
- `GET /admin/api/proxy-logs/routes/{routeId}/stream`：订阅指定路由实时日志。

### 发布与文档

- 新增 `scripts/build-dist.sh`，用于编译、打包 Spring Boot JAR，并生成包含一键启动脚本、后台配置文件和路由配置目录的 `target/dist/*.tar.gz` 发布包。
- 新增 GitHub Wiki 文档结构：首页、快速开始、用户指南、架构、API、排障、开发指南和侧边栏。
- 重新整理 `README.md`、`USAGE.md` 和 `CHANGELOG.md`，使功能说明与当前实现保持一致。

### 行为约定

- 业务异常由 `GlobalExceptionHandler` 转为 HTTP 200 + `success=false`。
- 参数校验异常返回 HTTP 200 + `success=false` + `code=400`。
- 未捕获异常返回 HTTP 500 + `success=false`。
- 前端 `fetchJson()` 依赖上述响应语义。
- 当前项目无 npm 构建流程，前端资源直接位于 `src/main/resources/templates` 与 `src/main/resources/static`。
