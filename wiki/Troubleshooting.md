# 常见问题

## 管理后台打不开

```bash
curl http://localhost:9999/actuator/health
```

确认访问 <http://localhost:9999/admin>；默认端口与监听地址来自 `src/main/resources/application.yml`（`127.0.0.1:9999`）。

## 新增路由后没有生效

1. 路由是否 `enabled=true`。
2. `pathPrefixes` 是否以 `/` 开头且格式合法。
3. `targetUrl` 是否可访问。
4. 接口是否返回 `success=true`（HTTP 200 但 `success=false` 说明是业务错误）。
5. 使用本地端口代理时，访问路径是否命中该路由 `pathPrefixes`。

## 前缀路径访问不到

Gateway 会按层级 `StripPrefix`：请求 `/test/hello` 时上游收到 `/hello`。若目标服务实际需要完整路径，请改用本地端口代理（保留原始 URI），或调整目标服务路径。

## 本地端口代理访问不到

1. 路由是否 `enabled=true` 且配置了 `localPort`。
2. `localIp:localPort` 是否被其他进程占用。
3. 命中前缀时 `accessPageBaseUrl` 是否已配置。
4. `localIp` 是否是本机可监听地址。

命中前缀走 `accessPageBaseUrl`，未命中走 `targetUrl`。结果不符预期时先确认请求路径与两个地址分别指向的上游。

## 两条入口路径不一致

这是预期行为，不是缺陷：

| 请求 `/test/hello` | 上游收到 |
| --- | --- |
| Gateway `:9999` | `/hello`（剥离前缀） |
| 本地端口代理 | `/test/hello`（保留原始 URI） |

## 修改 pathPrefixes 后浏览器仍显示旧页面

刷新只影响后续 HTTP 请求，已加载页面内的既有状态无法被主动改变。建议强制刷新浏览器、关闭旧标签页后重新打开，并查看后台日志确认请求是否重新到达代理（关注 `本地端口代理转发请求`、`本地端口代理拒绝未配置前缀请求`）。

## API 返回 HTTP 200 但 success=false

这是项目约定。业务异常与参数校验错误返回 HTTP 200，响应体 `success=false`、`code` 为业务码（校验错误为 400）。前端据此判断业务成败。

## 请求日志为空

1. 请求是否真的经过 Gateway 或本地端口代理。
2. 路由是否启用、路径是否命中前缀。
3. 应用是否刚重启——统计在内存中，重启即清空。

## 本地端口代理返回 502

响应体为文本 `Proxy request failed`。常见原因：目标服务未启动、`targetUrl` 填写错误、连接被拒绝、目标服务提前关闭连接。

## 统计数字对不上

- 同时配置 `localPort` 的路由，一次客户端请求可能产生两条日志（Gateway + 本地代理）。
- 派生 routeId `<id>__<n>` 会归并到基础 ID；但 `GET /admin/api/proxy-logs/routes/{routeId}` 按传入值精确匹配，传 `<id>__1` 会得到空快照。
- 滚动窗口是桶级近似：60 秒窗口按 1 秒桶、30 分钟序列按 60 秒桶，跨桶即过期。

## 端口被占用

主端口由 `server.port` 决定（默认 `9999`）；本地端口由各路由 `localPort` 决定。冲突时先停用占用该绑定的路由，或改用其他端口。前端顶栏展示的 Gateway 地址来自 `frontend/src/lib/app-meta.ts`，改端口时需同步。

## 发布包运行失败

确认本机已安装 JDK 21、包内存在 `config/application.yml` 与 `config/routes/`、当前目录有写权限。安装与启动方式的完整说明见 [打包与发布](../docs/PACKAGING.md)。
