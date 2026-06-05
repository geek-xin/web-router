# 变更说明

本文档记录 `web-router` 的主要能力变化和当前版本功能。

## [1.0.0-SNAPSHOT] - 当前开发版本

### 新增

- 基于 Spring Boot 3.5 和 Spring Cloud Gateway 的轻量路由代理服务。
- 提供 Thymeleaf 管理后台，默认入口为 `/admin`。
- 支持通过本地 JSON 文件持久化路由配置：`config/routes/<id>.json`。
- 支持管理 API 对路由执行新增、查询、更新、删除。
- 支持配置变更后动态刷新 Gateway 路由，无需重启应用。
- 支持单条路由配置多个路径前缀 `pathPrefixes`。
- 保留旧字段 `pathPrefix`，与 `pathPrefixes[0]` 同步以兼容旧配置。
- 支持按路径前缀自动生成 Gateway `Path` 谓词。
- 支持按路径层级自动生成 `StripPrefix` 过滤器。
- 支持目标地址归一化：API 入参省略协议时默认补 `http://`。
- 支持展示名称、路径前缀、目标地址和本地监听地址冲突校验。
- 支持为单条启用路由配置独立本地端口代理：`localIp:localPort`。
- 支持本地端口代理失败时返回 HTTP 502。
- 支持 Gateway 代理请求日志记录。
- 支持本地端口代理请求日志记录。
- 支持代理请求统计：总请求数、去重 IP 数、按 IP 聚合、最近 100 条日志。
- 支持通过 SSE 推送实时代理请求日志。
- 支持 `/actuator/health` 和 `/actuator/info` 管理端点。

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

### 行为说明

- Gateway 转发会根据路径前缀执行 `StripPrefix`。
- 本地端口代理会保留原始请求 URI，不执行 `StripPrefix`。
- 路由配置刷新只影响后续 HTTP 请求；已加载的目标系统页面不会自动重新请求前端配置/菜单，必要时需要刷新浏览器页面。
- 禁用路由会保留配置文件，但不会注册 Gateway 路由或启动本地端口代理。
- 业务异常和参数校验异常统一返回 `Result` 响应体；多数业务错误 HTTP 状态仍为 200，错误信息在响应体中体现。

### 注意事项

- 配置目录 `config/routes` 受应用启动工作目录影响。
- 默认服务端口为 `8090`。
- 当前项目没有 npm 构建流程，前端资源直接位于 `src/main/resources/static`。
