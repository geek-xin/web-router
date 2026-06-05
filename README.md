# web-router 使用说明

`web-router` 是一个轻量 Web 路由代理，基于 Spring Boot 3.5、Spring Cloud Gateway、Reactor Netty 和 Thymeleaf。它通过本地 JSON 文件维护路由配置，支持按路径前缀转发、单路由本地端口代理、管理后台动态刷新和代理请求日志统计。

## 功能概览

- **动态路由代理**：按一个或多个路径前缀匹配请求并转发到目标服务。
- **多路径前缀**：单条路由可配置多个 `pathPrefixes`。
- **前缀剥离**：Gateway 转发时按路径层级自动生成 `StripPrefix`。
- **本地端口代理**：单条路由可额外监听独立 `localIp:localPort`。
- **本地持久化**：配置保存到 `config/routes/<id>.json`。
- **热刷新**：通过管理后台/API 增删改路由后立即刷新，无需重启。
- **请求日志**：提供总量、IP 统计、最近请求和 SSE 实时日志。

## 环境要求

- JDK 21
- Maven 3.9+

主要版本：

- Spring Boot `3.5.2`
- Spring Cloud `2024.0.1`

## 快速启动

```bash
mvn test
mvn spring-boot:run
```

默认服务端口：`8090`

启动后访问：

- 管理后台：<http://localhost:8090/admin>
- 健康检查：<http://localhost:8090/actuator/health>

`GET /` 会自动重定向到 `/admin`。

## 路由配置

配置文件保存在应用启动工作目录下的 `config/routes`：

```text
config/routes/<id>.json
```

示例：

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

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 内部路由 ID；创建时自动生成，并作为配置文件名和 Gateway 路由 ID 基础值。 |
| `name` | 展示名称，不能为空，不能与其他路由重复。 |
| `pathPrefixes` | 路径前缀列表，至少一项。 |
| `pathPrefix` | 兼容旧配置的单路径字段，写回时与 `pathPrefixes[0]` 同步。 |
| `targetUrl` | 目标服务地址；API 入参可省略协议，保存时默认补 `http://`。 |
| `localIp` | 可选本地监听 IP，空值默认 `127.0.0.1`。 |
| `localPort` | 可选本地监听端口；为空表示不启用本地端口代理。 |
| `enabled` | 是否启用；禁用后保留配置文件但不注册代理。 |

## 转发规则

### Gateway 路径转发

启用的路由会按 `pathPrefixes` 注册 Gateway 路由：

- `/test` 匹配 `/test/**`
- `/` 匹配 `/**`
- `/test/api` 会生成 `StripPrefix=2`

例如：

```json
{
  "pathPrefixes": ["/test"],
  "targetUrl": "http://localhost:8081",
  "enabled": true
}
```

请求：

```bash
curl http://localhost:8090/test/hello
```

会转发到：

```text
http://localhost:8081/hello
```

### 本地端口代理

如果配置了 `localPort`，应用会为该路由额外启动一个 Reactor Netty 本地代理：

```json
{
  "targetUrl": "http://localhost:8081",
  "localIp": "127.0.0.1",
  "localPort": 18081,
  "enabled": true
}
```

请求：

```bash
curl http://127.0.0.1:18081/test/hello
```

会按原始 URI 追加到 `targetUrl` 后，即转发到：

```text
http://localhost:8081/test/hello
```

注意：本地端口代理不会像 Gateway 路径转发一样执行 `StripPrefix`。

## 管理 API

所有管理 API 返回统一结构：

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000
}
```

业务错误通常仍返回 HTTP 200，但响应体中 `success=false`。

### 路由管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/routes` | 查询全部路由。 |
| `GET` | `/admin/api/routes/{id}` | 查询单条路由。 |
| `GET` | `/admin/api/routes/{id}/raw` | 查看原始 JSON 文件内容。 |
| `POST` | `/admin/api/routes` | 创建路由并刷新代理。 |
| `PUT` | `/admin/api/routes/{id}` | 更新路由并刷新代理。 |
| `DELETE` | `/admin/api/routes/{id}` | 删除路由并刷新代理。 |

创建示例：

```bash
curl -X POST http://localhost:8090/admin/api/routes \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test"],
    "targetUrl": "localhost:8081",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

### 请求日志

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/proxy-logs` | 全部路由日志快照。 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}` | 指定路由日志快照。 |
| `GET` | `/admin/api/proxy-logs/stream` | 全部路由实时日志 SSE。 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}/stream` | 指定路由实时日志 SSE。 |

日志快照包含：

- `totalRequests`：请求总数
- `uniqueIpCount`：去重 IP 数
- `requestsByIp`：按 IP 聚合的请求数
- `recentLogs`：最近 100 条请求日志

## 校验规则

- `name` 不能为空，不能重复。
- `pathPrefixes` 至少一项，格式为 `^/[-a-zA-Z0-9_/]*$`。
- 路径前缀不能与其他路由完全重复。
- `targetUrl` 入参格式为 `host:port` 或 `http(s)://host:port`，不能重复。
- `localPort` 范围为 `1-65535`。
- `localIp` 允许空值、`localhost` 或 IPv4。
- `localIp:localPort` 不能与其他路由重复。

## 开发提示

- 动态路由逻辑：`src/main/java/com/geek/webrouter/config/DynamicRouteService.java`
- 本地端口代理：`src/main/java/com/geek/webrouter/config/LocalPortProxyService.java`
- 配置读写校验：`src/main/java/com/geek/webrouter/web/service/impl/RouteConfigServiceImpl.java`
- 管理 API：`src/main/java/com/geek/webrouter/web/controller/RouteConfigController.java`
- 请求日志 API：`src/main/java/com/geek/webrouter/web/controller/ProxyRequestLogController.java`
- 前端页面：`src/main/resources/templates/index.html`、`src/main/resources/static/js/app.js`、`src/main/resources/static/css/style.css`

修改写操作后必须刷新动态路由，否则 Gateway 和本地端口代理不会立即生效。
