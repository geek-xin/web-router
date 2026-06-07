# AGENTS.md

## 项目定位

`web-router` 是一个轻量 Web 路由代理，基于 Spring Boot 3.5、Spring Cloud Gateway、Reactor Netty 和 Thymeleaf。它通过本地 JSON 文件维护路由配置，支持：

- 按一个或多个路径前缀转发到目标服务。
- 为单条路由可选启动独立本地 IP/端口代理。
- 在管理后台增删改查配置并即时刷新 Gateway，无需重启。
- 记录代理请求统计、最近请求日志，并通过 SSE 推送路由请求日志。

## 核心机制

### 路由配置模型

核心实体：`src/main/java/com/geek/webrouter/web/model/entity/RouteConfig.java`

每条路由配置包含：

- `id`：内部路由 ID，用作 Gateway 路由 ID 基础值和配置文件名；创建时自动生成，格式类似 `route-20260603234846-4d2deb`。
- `name`：展示名称，可包含中文和符号；不同路由之间不能重复。
- `pathPrefixes`：路径前缀列表，如 `/api/users`、`/admin`；同一路由内不能重复，不同路由之间允许使用相同前缀（常用于复制一套路由到不同目标/本地端口）。
- `pathPrefix`：兼容旧配置的单路径字段；读写时与 `pathPrefixes` 第一项同步。
- `targetUrl`：目标服务地址；API 入参可省略协议，保存前由 `RouteTargetUrlNormalizer` 补为 `http://`。
- `localIp` / `localPort`：可选本地监听地址；`localPort` 为空表示不启动独立本地端口代理，`localIp` 默认 `127.0.0.1`。
- `enabled`：是否启用；禁用配置仍保留文件，但不会注册 Gateway 路由，也不会启动本地端口代理。

示例配置：

```json
{
  "id": "route-20260603234846-4d2deb",
  "name": "测试服务",
  "pathPrefix": "/test",
  "pathPrefixes": ["/test", "/api/test"],
  "targetUrl": "http://localhost:8081",
  "localIp": "127.0.0.1",
  "localPort": 18081,
  "enabled": true
}
```

### 本地持久化

- 配置目录：`config/routes`，相对于应用启动工作目录。
- 配置文件：`config/routes/<id>.json`。
- 读写入口：`RouteConfigServiceImpl`。
- 不要绕过 `resolveFilePath()` 直接拼接任意文件路径。
- `listAll()` 会按文件最后修改时间倒序返回。

### Gateway 动态转发

核心服务：`src/main/java/com/geek/webrouter/config/DynamicRouteService.java`

- 启动和写操作后调用 `refreshAll()`。
- 刷新流程：先删除当前已注册路由，再读取全部 `enabled=true` 配置并重新注册，最后发布 `RefreshRoutesEvent`。
- 每个 `pathPrefixes` 项会注册一条 Gateway 路由：
  - 第 1 条使用基础 `id`。
  - 后续前缀使用 `<id>__<index>`，如 `route-xxx__1`。
- Path 谓词：
  - `/` -> `/**`
  - `/test` -> `/test/**`
- `StripPrefix` 按路径层级生成：`/test/api` 会剥离 2 段。

### 本地端口代理

核心服务：`src/main/java/com/geek/webrouter/config/LocalPortProxyService.java`

- 仅对 `enabled=true` 且设置了 `localPort` 的路由启动。
- 监听地址为 `effectiveLocalIp():localPort`，`localIp` 为空时默认 `127.0.0.1`。
- 刷新动态路由时会停止旧本地代理并按当前启用配置重启。
- 本地端口代理使用 Reactor Netty `HttpServer` + `HttpClient`，会透传请求方法、请求体和大部分 Header，并把 `Host` 改为目标地址 Host。
- 本地端口代理转发时会先按当前路由的 `pathPrefixes` 做入口隔离；命中后不会按 `pathPrefixes` 剥离前缀，而是把原始请求 URI 追加到 `targetUrl` 后。
- 本地端口代理响应会设置 `Connection: close` 和 `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`，避免浏览器在增删 `pathPrefixes` 后复用刷新前的旧连接或缓存旧页面/接口结果。
- 增删 `pathPrefixes` 后，后台会刷新本地端口代理；刷新只影响后续 HTTP 请求，已经加载的目标系统页面（如 `/portal/login/loginPortal.html`）如果没有重新发起网络请求，web-router 无法主动改变页面内已有状态。可通过 `本地端口代理转发请求` / `本地端口代理拒绝未配置前缀请求` 日志判断请求是否真正到达 9191。
- 代理失败时返回 HTTP 502 和文本 `Proxy request failed`。

### 请求日志

相关文件：

- `ProxyRequestLogFilter`：记录 Spring Cloud Gateway 代理请求。
- `LocalPortProxyService`：记录本地端口代理请求。
- `ProxyRequestLogService`：维护内存统计、最近 100 条日志和 SSE 事件流。
- `ProxyRequestLogController`：暴露管理端日志 API。

统计口径：

- 总请求数。
- 去重 IP 数。
- 按 IP 请求次数排序。
- 最近请求日志。
- 多前缀派生路由 ID（如 `<id>__1`）会归并到基础路由 ID。

## 管理端与 API

### 页面入口

- `GET /`：重定向到 `/admin`。
- `GET /admin`：渲染 Thymeleaf 管理后台。

### 路由配置 API

所有 API 统一返回 `Result<T>`。

- `GET /admin/api/routes`：查询全部路由。
- `GET /admin/api/routes/{id}`：查询单条路由。
- `GET /admin/api/routes/{id}/raw`：读取原始 JSON 文件内容。
- `POST /admin/api/routes`：创建路由，成功后刷新动态路由。
- `PUT /admin/api/routes/{id}`：更新路由，成功后刷新动态路由。
- `DELETE /admin/api/routes/{id}`：删除路由，成功后刷新动态路由。

### 请求日志 API

- `GET /admin/api/proxy-logs`：全部路由日志快照。
- `GET /admin/api/proxy-logs/routes/{routeId}`：指定路由日志快照。
- `GET /admin/api/proxy-logs/stream`：全部路由请求日志 SSE 流。
- `GET /admin/api/proxy-logs/routes/{routeId}/stream`：指定路由请求日志 SSE 流。

## 校验与约束

- 路由 `id` 只允许英文、数字、下划线和连字符；外部路径变量 `{id}` 最终会进入 `resolveFilePath()` 校验。
- `name` 不能为空，作为展示名称使用，不能与其他路由重复。
- `pathPrefixes` 至少一项；每项必须以 `/` 开头，只允许英文、数字、下划线、连字符和 `/`。
- 路径前缀冲突检查是“完全相同前缀”冲突，不做父子前缀包含判断。
- `targetUrl` DTO 入参格式为 `host:port` 或 `http(s)://host:port`；保存前会归一化为带协议 URL。
- `targetUrl` 不能与其他路由重复。
- `localPort` 范围为 `1-65535`。
- `localIp` 允许空值、`localhost` 或 IPv4；设置本地端口后最终会规范为有效监听 IP。
- `localIp:localPort` 不能与其他启用本地绑定的路由重复。

## 统一响应与异常

- 成功响应：`Result.success(data)`，`success=true`，`code=200`。
- 业务异常：抛 `BusinessException`，由 `GlobalExceptionHandler` 转为 HTTP 200 响应，响应体 `success=false`。
- 参数校验异常：由 `GlobalExceptionHandler` 转为 HTTP 200 响应，响应体 `success=false`、`code=400`。
- 未捕获异常：返回 HTTP 500，响应体为 `Result.fail(INTERNAL_ERROR)`。
- 前端 `fetchJson()` 依赖“业务错误 HTTP 200 + 响应体 success=false”的约定，修改异常语义时必须同步前端。

## 关键文件

- `src/main/java/com/geek/webrouter/Application.java`：应用入口。
- `src/main/java/com/geek/webrouter/config/DynamicRouteService.java`：动态 Gateway 路由注册与刷新。
- `src/main/java/com/geek/webrouter/config/LocalPortProxyService.java`：每路由本地端口代理。
- `src/main/java/com/geek/webrouter/config/ProxyRequestLogFilter.java`：Gateway 请求日志过滤器。
- `src/main/java/com/geek/webrouter/web/service/impl/RouteConfigServiceImpl.java`：路由 JSON 文件读写、校验、冲突检测。
- `src/main/java/com/geek/webrouter/web/service/ProxyRequestLogService.java`：内存请求统计与 SSE 日志流。
- `src/main/java/com/geek/webrouter/web/controller/RouteConfigController.java`：管理页面与路由配置 REST API。
- `src/main/java/com/geek/webrouter/web/controller/ProxyRequestLogController.java`：请求日志 REST/SSE API。
- `src/main/java/com/geek/webrouter/web/support/RouteTargetUrlNormalizer.java`：目标地址协议归一化。
- `src/main/resources/templates/index.html`、`src/main/resources/static/js/app.js`、`src/main/resources/static/css/style.css`：管理后台页面、交互和样式；当前无 npm 构建。
- `src/main/resources/application.yml`：端口、Gateway、Thymeleaf、Actuator 配置。

## 运行与验证

```bash
mvn test
mvn spring-boot:run
```

- 默认端口：`8090`。
- 管理后台：`http://localhost:8090/admin`。
- Actuator：`/actuator/health`、`/actuator/info`。

## 修改约定

- 改动态路由优先看 `DynamicRouteService`；写操作后必须刷新动态路由，否则 Gateway 不会立即生效。
- 改本地端口代理优先看 `LocalPortProxyService`；注意刷新时停止/重启代理和端口冲突处理。
- 改配置存储优先看 `RouteConfigServiceImpl`；保持 `id` 文件名机制和旧字段 `pathPrefix` 兼容。
- 改 API 保持 `Result<T>` 结构；业务错误优先抛 `BusinessException`。
- 新增路由字段需同步：`RouteConfig`、`RouteConfigDto`、`RouteConfigServiceImpl` 读写/校验、`index.html` 表单、`app.js` 列表/复制/编辑/JSON 预览逻辑，并考虑旧 JSON 兼容。
- 改前端时同步检查 `index.html`、`app.js`、`style.css`；本项目没有 npm 构建流程。
- 改请求日志时同时考虑 Gateway 路由和本地端口代理两条路径，避免统计口径不一致。
- 改常量或配置值前先全局搜索引用；特别注意 `application.yml` 默认端口为 `8090`，而 `CommonConstants.DEFAULT_PORT` 当前为 `8080`。

## 其他注意

- `config/routes` 是运行时目录，受启动工作目录影响。
- 配置文件中的旧 `pathPrefix` 仍需兼容；写回 JSON 时应保持 `pathPrefix` 与 `pathPrefixes[0]` 同步。
- 多路径前缀会产生派生 Gateway routeId；日志展示和统计通常应归并到基础 `id`。
- 本地端口代理与 Gateway 转发语义不同：Gateway 会按前缀 `StripPrefix`，本地端口代理只按前缀隔离请求但保留原始 URI。
