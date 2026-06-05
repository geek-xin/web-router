# web-router 使用说明

本文说明如何启动 `web-router`、管理路由配置、理解 Gateway 转发与本地端口代理的差异，以及调用管理 API。

## 环境要求

- JDK 21
- Maven 3.9+

## 快速启动

在项目根目录执行：

```bash
mvn test
mvn spring-boot:run
```

默认服务地址：`http://127.0.0.1:8090`。

启动后访问：

- 管理后台：<http://localhost:8090/admin>
- 健康检查：<http://localhost:8090/actuator/health>

`GET /` 会自动重定向到 `/admin`。

## 配置文件位置

路由配置保存在应用启动工作目录下：

```text
config/routes/<id>.json
```

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

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 内部路由 ID；创建时自动生成，并作为配置文件名和 Gateway 路由 ID 基础值。 |
| `name` | 展示名称，不能为空，不能与其他路由重复。 |
| `pathPrefixes` | 路径前缀列表，至少一项；同一前缀不能重复配置。 |
| `pathPrefix` | 兼容旧配置的单路径字段，写回时与 `pathPrefixes[0]` 同步。 |
| `targetUrl` | 目标服务地址；API 入参可省略协议，保存时默认补 `http://`。 |
| `localIp` | 可选本地监听 IP；空值默认 `127.0.0.1`。 |
| `localPort` | 可选本地监听端口；为空表示不启用本地端口代理。 |
| `enabled` | 是否启用；禁用后保留配置文件，但不注册 Gateway 路由，也不启动本地端口代理。 |

## 管理后台使用

1. 启动应用后打开 <http://localhost:8090/admin>。
2. 点击新增路由，填写：
   - 路由名称。
   - 一个或多个路径前缀。
   - 目标服务地址。
   - 可选本地监听 IP 和端口。
   - 是否启用。
3. 保存后应用会立即刷新 Gateway 路由和本地端口代理。
4. 可在列表中编辑、删除、启用/禁用路由，或查看原始 JSON 配置。

## Gateway 路径转发规则

启用的路由会按每个 `pathPrefixes` 项注册一条 Gateway 路由：

- `/` 匹配 `/**`。
- `/test` 匹配 `/test/**`。
- `/test/api` 会生成 `StripPrefix=2`。

例如配置：

```json
{
  "name": "测试服务",
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

也就是说，Gateway 转发会按路径前缀层级剥离前缀。

## 本地端口代理规则

如果路由配置了 `localPort`，应用会为该路由额外启动一个本地代理：

```json
{
  "name": "测试服务",
  "pathPrefixes": ["/test"],
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

会按原始 URI 追加到目标地址后，转发到：

```text
http://localhost:8081/test/hello
```

注意：本地端口代理只使用 `pathPrefixes` 做入口隔离，不会执行 `StripPrefix`。

## 管理 API

所有管理 API 统一返回 `Result<T>` 结构：

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000
}
```

业务错误通常返回 HTTP 200，但响应体中 `success=false`。参数校验错误的响应体 `code=400`。

### 路由配置 API

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

更新示例：

```bash
curl -X PUT http://localhost:8090/admin/api/routes/route-20260603234846-4d2deb \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test", "/api/test"],
    "targetUrl": "http://localhost:8081",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

删除示例：

```bash
curl -X DELETE http://localhost:8090/admin/api/routes/route-20260603234846-4d2deb
```

## 请求日志 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/proxy-logs` | 全部路由日志快照。 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}` | 指定路由日志快照。 |
| `GET` | `/admin/api/proxy-logs/stream` | 全部路由实时日志 SSE。 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}/stream` | 指定路由实时日志 SSE。 |

日志快照包含：

- `totalRequests`：请求总数。
- `uniqueIpCount`：去重 IP 数。
- `requestsByIp`：按 IP 聚合的请求数。
- `recentLogs`：最近 100 条请求日志。

SSE 示例：

```bash
curl -N http://localhost:8090/admin/api/proxy-logs/stream
```

## 校验规则

- `id` 只允许英文、数字、下划线和连字符。
- `name` 不能为空，不能与其他路由重复。
- `pathPrefixes` 至少一项；每项必须以 `/` 开头，只允许英文、数字、下划线、连字符和 `/`。
- 路径前缀冲突检查只判断完全相同前缀，不判断父子前缀包含关系。
- `targetUrl` 入参格式为 `host:port` 或 `http(s)://host:port`，保存前会归一化为带协议 URL，且不能与其他路由重复。
- `localPort` 范围为 `1-65535`。
- `localIp` 允许空值、`localhost` 或 IPv4。
- `localIp:localPort` 不能与其他启用本地绑定的路由重复。

## 常见注意事项

- `config/routes` 是运行时目录，受应用启动工作目录影响。
- 禁用路由会保留 JSON 文件，但不会注册 Gateway 路由或启动本地端口代理。
- Gateway 转发会剥离路径前缀；本地端口代理不会剥离路径前缀。
- 修改、删除或新增路径前缀后，应用会刷新本地端口代理；刷新只影响后续 HTTP 请求。
- 本地端口代理失败时返回 HTTP 502，响应文本为 `Proxy request failed`。
