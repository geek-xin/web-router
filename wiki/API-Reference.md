# API 参考

所有管理 API 统一返回 `Result<T>`。

## 响应结构

成功响应：

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000
}
```

业务错误通常返回 HTTP 200，但响应体中 `success=false`。参数校验错误响应体 `code=400`。

## 页面入口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/` | 重定向到 `/admin` |
| `GET` | `/admin` | 渲染 Thymeleaf 管理后台 |

## 路由配置 API

> 控制器路径变量当前命名为 `{name}`，实际使用的是配置文件 ID。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/routes` | 查询全部路由，按配置文件最后修改时间倒序 |
| `GET` | `/admin/api/routes/{id}` | 查询单条路由 |
| `GET` | `/admin/api/routes/{id}/raw` | 查看原始 JSON 文件内容 |
| `POST` | `/admin/api/routes` | 创建路由并刷新代理 |
| `PUT` | `/admin/api/routes/{id}` | 更新路由并刷新代理 |
| `DELETE` | `/admin/api/routes/{id}` | 删除路由并刷新代理 |

### 创建路由

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

### 更新路由

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

### 删除路由

```bash
curl -X DELETE http://localhost:8090/admin/api/routes/route-20260603234846-4d2deb
```

## 请求日志 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/proxy-logs` | 全部路由日志快照 |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}` | 指定路由日志快照 |
| `GET` | `/admin/api/proxy-logs/stream` | 全部路由实时日志 SSE |
| `GET` | `/admin/api/proxy-logs/routes/{routeId}/stream` | 指定路由实时日志 SSE |

### 查看日志快照

```bash
curl http://localhost:8090/admin/api/proxy-logs
curl http://localhost:8090/admin/api/proxy-logs/routes/route-20260603234846-4d2deb
```

### 订阅 SSE 日志

```bash
curl -N http://localhost:8090/admin/api/proxy-logs/stream
curl -N http://localhost:8090/admin/api/proxy-logs/routes/route-20260603234846-4d2deb/stream
```

## 异常语义

| 类型 | HTTP 状态 | 响应体 |
| --- | --- | --- |
| 成功 | `200` | `success=true` |
| 业务异常 | `200` | `success=false` |
| 参数校验异常 | `200` | `success=false, code=400` |
| 未捕获异常 | `500` | `success=false` |

前端 `fetchJson()` 依赖“业务错误 HTTP 200 + 响应体 `success=false`”的约定。

