# API 参考

所有管理 API 统一返回 `Result<T>`。URL 中的 `{routeId}` 是配置文件 ID，如 `route-20260603234846-4d2deb`。

## 响应结构

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000
}
```

| 场景 | HTTP 状态 | 响应体 |
| --- | --- | --- |
| 成功 | 200 | `success=true, code=200` |
| 业务异常 | **200** | `success=false, code=<业务码>` |
| 参数校验失败 | **200** | `success=false, code=400` |
| 未捕获异常 | 500 | `success=false, code=500` |
| 本地端口代理上游失败 | 502 | 文本 `Proxy request failed`（非 `Result` 结构） |

前端 `fetchJson()` 依赖「业务错误 HTTP 200 + `success=false`」这一约定。

## 页面

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/` | 重定向到 `/admin` |
| `GET` | `/admin` | 管理后台页面 |

## 路由配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/routes` | 全部路由，按文件 mtime 倒序 |
| `GET` | `/admin/api/routes/{routeId}` | 单条路由 |
| `GET` | `/admin/api/routes/{routeId}/raw` | 原始 JSON 文件内容 |
| `GET` | `/admin/api/routes/export` | 导出全部路由 |
| `POST` | `/admin/api/routes/import` | 批量导入（总是新增） |
| `POST` | `/admin/api/routes` | 创建，成功后刷新代理 |
| `PUT` | `/admin/api/routes/{routeId}` | 更新，成功后刷新代理 |
| `DELETE` | `/admin/api/routes/{routeId}` | 删除，成功后刷新代理 |

### 创建路由

```bash
curl -X POST http://localhost:9999/admin/api/routes \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test", "/api/test"],
    "targetUrl": "localhost:8081",
    "accessPageBaseUrl": "localhost:8082",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

### 更新与删除

```bash
curl -X PUT http://localhost:9999/admin/api/routes/route-20260603234846-4d2deb \
  -H 'Content-Type: application/json' \
  -d '{ "name": "测试服务", "pathPrefixes": ["/test"], "targetUrl": "http://localhost:8081",
        "accessPageBaseUrl": "http://localhost:8082", "localIp": "127.0.0.1", "localPort": 18081, "enabled": true }'

curl -X DELETE http://localhost:9999/admin/api/routes/route-20260603234846-4d2deb
```

## 日志与指标

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/proxy-logs` | 全部路由日志快照 |
| `GET` | `/admin/api/proxy-logs/metrics` | 全部路由紧凑指标，供卡片 / KPI 使用 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}` | 指定路由快照 |
| `GET` | `/admin/api/proxy-logs/stream` | 全部路由 SSE，事件名 `proxy-request` |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}/stream` | 指定路由 SSE |

`/metrics` 返回 `Map<routeId, RouteTrafficMetrics>`：`routeId`、`totalRequests`、`failedRequests`、`slowRequests`、`totalDurationMs`、`requestsLastMinute`、`failedLastMinute`、`averageDurationMs`、`trafficBuckets`（由旧到新，长度 30）。未产生过请求的路由不会出现在结果中。

```bash
curl http://localhost:9999/admin/api/proxy-logs/metrics
curl -N http://localhost:9999/admin/api/proxy-logs/routes/route-20260603234846-4d2deb/stream
```

## 版本与更新

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/version` | 当前版本信息（version / buildTime / platform / installMode / updateSupported） |
| `GET` | `/admin/api/update/check` | 检查更新；**始终 `success=true`**，失败原因写在 `message` |
| `POST` | `/admin/api/update/apply` | 下载更新包并生成更新脚本；不可更新时抛 `BusinessException` |

## 运维端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/actuator/health` | 健康检查 |
| `GET` | `/actuator/info` | 应用信息 |

## 错误码

| 错误码 | 值 | 含义 |
| --- | --- | --- |
| `BAD_REQUEST` | 400 | 请求参数错误 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `DUPLICATE_NAME` | 409 | 路由名称已存在 |
| `DUPLICATE_PREFIX` | 409 | 路径前缀重复 |
| `DUPLICATE_LOCAL_BINDING` | 409 | 本地监听地址已存在 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `CONFIG_IO_ERROR` | 500 | 配置文件读写失败 |
