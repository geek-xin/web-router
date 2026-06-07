# web-router 使用说明

本文说明如何启动、配置和使用 `web-router`，并解释 Gateway 转发、本地端口代理、请求日志和管理 API 的当前行为。

## 环境要求

- JDK 21
- Maven 3.9+
- Git（用于版本管理和发布）

## 启动应用

```bash
mvn test
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8090`。

常用入口：

| 地址 | 说明 |
| --- | --- |
| `GET /` | 重定向到 `/admin` |
| `GET /admin` | 管理后台 |
| `GET /actuator/health` | 健康检查 |
| `GET /actuator/info` | 应用信息 |

## 路由配置文件

路由配置保存在应用启动工作目录下：

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

| 字段 | 说明 |
| --- | --- |
| `id` | 内部路由 ID；创建时自动生成，用作配置文件名和 Gateway routeId 基础值。 |
| `name` | 展示名称，不能为空，不能与其他路由重复。 |
| `pathPrefixes` | 路径前缀列表，至少一项；同一路由内和不同路由间都不能重复。 |
| `pathPrefix` | 兼容旧配置的单路径字段，写回时与 `pathPrefixes[0]` 同步。 |
| `targetUrl` | 目标服务地址；API 入参可省略协议，保存时默认补 `http://`。 |
| `localIp` | 可选本地监听 IP；空值且配置了本地端口时默认 `127.0.0.1`。 |
| `localPort` | 可选本地监听端口；为空表示不启用独立本地端口代理。 |
| `enabled` | 是否启用；禁用后保留文件，但不注册 Gateway 路由，也不启动本地端口代理。 |

## 管理后台

打开 <http://localhost:8090/admin> 后可以：

- 新增、编辑、删除路由。
- 启用或禁用路由。
- 为一条路由配置多个路径前缀。
- 配置可选本地监听 IP/端口。
- 查看原始 JSON 配置和配置目录。
- 复制 Gateway 或本地端口访问地址。
- 查看全部/单路由请求统计、Top 路径、最近请求日志和实时刷新状态。

保存路由后，后台会立即刷新 Gateway 路由和本地端口代理。

## Gateway 转发规则

启用的路由会按每个 `pathPrefixes` 项注册一条 Gateway 路由：

- 第 1 条使用基础 `id`。
- 后续前缀使用 `<id>__<index>`，例如 `route-demo__1`。
- `/` 匹配 `/**`。
- `/test` 匹配 `/test/**`。
- `/test/api` 会执行 `StripPrefix=2`。

示例：

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

目标服务收到：

```text
/hello
```

## 本地端口代理规则

如果启用路由配置了 `localPort`，应用会为该路由启动独立本地监听：

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

目标服务收到：

```text
/test/hello
```

本地端口代理特点：

- 只对 `enabled=true` 且设置了 `localPort` 的路由启动。
- `localIp` 为空时使用 `127.0.0.1`。
- 只允许命中当前路由 `pathPrefixes` 的请求进入；未命中请求返回 `404`。
- 不执行 `StripPrefix`，原始请求 URI 会追加到 `targetUrl` 后。
- 透传请求方法、请求体和大部分 Header，并把 `Host` 改为目标地址 Host。
- 响应设置 `Connection: close` 和禁用缓存头，减少配置刷新后的旧连接/旧缓存影响。
- 代理失败时返回 HTTP 502，文本为 `Proxy request failed`。

## 管理 API

所有管理 API 统一返回：

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000
}
```

业务错误和参数校验错误通常返回 HTTP 200，但响应体 `success=false`；参数校验错误响应体 `code=400`。前端依赖这一语义。

### 路由配置 API

> 路径变量当前由控制器命名为 `{name}`，实际传入的是配置文件 ID，例如 `route-20260603234846-4d2deb`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/routes` | 查询全部路由，按配置文件最后修改时间倒序。 |
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

### 请求日志 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/proxy-logs` | 全部路由日志快照。 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}` | 指定路由日志快照。 |
| `GET` | `/admin/api/proxy-logs/stream` | 全部路由实时日志 SSE。 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}/stream` | 指定路由实时日志 SSE。 |

快照内容包括：

- 总请求数。
- 去重 IP 数。
- 按 IP 请求次数排序。
- Top 路径统计。
- 最近 100 条请求日志。

订阅 SSE：

```bash
curl -N http://localhost:8090/admin/api/proxy-logs/stream
```

## 校验规则

- `id` 只允许英文、数字、下划线和连字符。
- `name` 不能为空，不能与其他路由重复。
- `pathPrefixes` 至少一项；每项必须以 `/` 开头，只允许英文、数字、下划线、连字符和 `/`。
- 路径前缀会规范化：非根路径末尾 `/` 会被移除，例如 `/api/` -> `/api`。
- 路径前缀冲突只判断“完全相同前缀”，不判断父子包含关系。
- `targetUrl` 入参格式为 `host:port` 或 `http(s)://host:port`，保存前归一化为带协议 URL，且不能与其他路由重复。
- `localPort` 范围为 `1-65535`。
- `localIp` 允许空值、`localhost` 或有效 IPv4；非空时即使未配置 `localPort` 也会校验。
- `localIp:localPort` 不能与其他启用本地绑定的路由重复。

## 打包发布

生成发布包：

```bash
scripts/build-dist.sh
```

默认跳过测试；需要打包前运行测试：

```bash
scripts/build-dist.sh --with-tests
```

输出位置：

```text
target/dist/web-router-1.0.0-SNAPSHOT.tar.gz
```

发布包包含：

- Spring Boot 可执行 JAR。
- `run.sh` 一键启动脚本。
- `config/application.yml` 后台配置文件。
- `config/routes` 路由配置目录；如果本地已有 `config/routes/*.json`，会一并打入发布包。
- `README.md`、`USAGE.md`、`CHANGELOG.md`。

## 常见注意事项

- `config/routes` 受应用启动工作目录影响。
- 禁用路由会保留 JSON 文件，但不会注册 Gateway 路由或启动本地端口代理。
- Gateway 转发会剥离路径前缀；本地端口代理不会剥离路径前缀。
- 修改 `pathPrefixes` 后刷新只影响后续 HTTP 请求；已加载页面若没有重新请求，代理无法主动改变页面内状态。
- 请求日志保存在内存中，应用重启后清空。
- 当前项目没有 npm 构建流程，前端静态资源直接由 Spring Boot 提供。
