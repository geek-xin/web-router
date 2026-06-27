# 架构与机制

## 配置模型

核心实体：`RouteConfig`。

一条路由包含展示名称、路径前缀列表、默认地址（兜底）`targetUrl`、代理地址 `accessPageBaseUrl`、访问页 `accessPage`、本地监听地址/端口和启用状态。旧字段 `pathPrefix` 仍被读取，并在写回时与 `pathPrefixes[0]` 同步。

## 配置存储

路由配置由 `RouteConfigServiceImpl` 读写，保存为本地 JSON 文件：

```text
config/routes/<id>.json
```

机制：

- `config/routes` 是运行时目录，受应用启动工作目录影响。
- 文件名来自路由 `id`，读取时文件名 ID 是权威值。
- `resolveFilePath()` 会校验 ID，只允许英文、数字、下划线和连字符。
- `listAll()` 按文件最后修改时间倒序返回。
- 写入前会校验展示名称、路径前缀、默认地址、代理地址、本地 IP/端口和本地绑定冲突。

## 动态 Gateway 路由

动态路由由 `DynamicRouteService` 管理。

刷新流程：

1. 读取全部路由配置。
2. 筛选 `enabled=true` 的配置。
3. 将每个 `pathPrefixes` 项转换为一条 Gateway 路由定义。
4. 与当前 Gateway 路由快照做差异比较。
5. 删除已变化或已移除的 routeId。
6. 保存新增或变化的路由定义。
7. 发布 `RefreshRoutesEvent`。
8. 刷新本地端口代理。

### 多路径前缀 routeId

一条路由可以配置多个 `pathPrefixes`：

- 第 1 条使用基础 `id`。
- 后续前缀使用 `<id>__<index>`。

例如：

```text
route-demo
route-demo__1
route-demo__2
```

请求日志展示和统计会归并到基础 routeId。

### Path 与 StripPrefix

| 配置前缀 | Gateway Path | StripPrefix |
| --- | --- | --- |
| `/` | `/**` | `0` |
| `/test` | `/test/**` | `1` |
| `/test/api` | `/test/api/**` | `2` |

## 本地端口代理

本地端口代理由 `LocalPortProxyService` 管理。

启动条件：

- 路由 `enabled=true`。
- 设置了 `localPort`。

监听地址：

```text
effectiveLocalIp():localPort
```

`localIp` 为空时默认 `127.0.0.1`。

转发行为：

- 使用 Reactor Netty `HttpServer` + `HttpClient`。
- 每个本地监听绑定到一条启用路由。
- 请求路径命中该路由 `pathPrefixes` 时使用 `accessPageBaseUrl`，未命中时使用 `targetUrl` 默认地址。
- 保留原始 URI，不剥离前缀。
- 透传请求方法、请求体和大部分 Header。
- 将 `Host` 改为目标地址 Host。
- 响应设置 `Connection: close` 和禁用缓存头。
- 代理失败时返回 `HTTP 502` + `Proxy request failed`。

刷新行为：

- 同一 `localIp:localPort` 绑定会更新内存路由快照，避免重启监听。
- 移除、禁用或变更绑定时停止不再需要的监听。
- 刷新只保证后续新请求使用新配置，已在途请求可能仍使用已捕获的旧快照。

## 请求日志链路

请求日志由 `ProxyRequestLogService` 维护。

记录入口：

- `ProxyRequestLogFilter`：记录 Gateway 代理请求。
- `LocalPortProxyService`：记录本地端口代理请求。

暴露入口：

- `ProxyRequestLogController`：日志快照和 SSE API。
- 管理后台日志面板和单路由日志弹窗。

统计内容：

- 总请求数。
- 去重 IP 数。
- 按 IP 请求次数排序。
- Top 路径统计。
- 慢请求 Top。
- 最近 100 条请求日志。

## 管理后台资源

- `src/main/resources/templates/index.html`：Thymeleaf 挂载页和后端注入的元数据。
- `frontend/src/App.tsx`：React 管理后台入口。
- `frontend/src/features/*`：表单、列表、复制、日志、SSE 和详情抽屉交互。
- `frontend/src/styles.css`：后台布局和组件样式入口。
- `src/main/resources/static/admin/assets/app.js`：Vite 构建后的浏览器脚本。
- `src/main/resources/static/admin/assets/app.css`：Vite 构建后的样式文件。

当前前端源码通过 `frontend/` 下的 npm/Vite 构建流程产出静态资源，最终由 Spring Boot 直接提供。
