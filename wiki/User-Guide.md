# 用户指南

当前版本：`1.1.0`。

## 管理后台

启动应用后打开：

```text
http://localhost:8090/admin
```

管理后台支持：

- 新增、编辑、删除路由。
- 启用或禁用路由。
- 为一条路由维护多个路径前缀。
- 配置本地监听 IP/端口、默认地址（兜底）、代理地址和访问页。
- 查看原始 JSON 配置和配置目录。
- 复制 Gateway 或本地端口访问地址。
- 查看全部/单路由请求统计、Top 路径、慢请求和最近请求日志。
- 通过日志弹窗实时刷新请求记录。

保存路由后，应用会立即刷新 Gateway 路由和本地端口代理。

## 路由配置示例

```json
{
  "id": "route-20260603234846-4d2deb",
  "name": "测试服务",
  "pathPrefix": "/test",
  "pathPrefixes": ["/test", "/api/test"],
  "targetUrl": "http://localhost:8081",
  "accessPageBaseUrl": "http://localhost:8082",
  "accessPage": "/test/hello",
  "localIp": "127.0.0.1",
  "localPort": 18081,
  "enabled": true
}
```

## 字段说明

| 字段 | 说明 |
| --- | --- |
| `id` | 内部路由 ID；创建时自动生成，并作为配置文件名和 Gateway routeId 基础值。 |
| `name` | 展示名称，不能为空，不能与其他路由重复。 |
| `pathPrefixes` | 路径前缀列表；同一路由内不能重复，不同路由可复用相同前缀。为空时本地监听请求走默认地址。 |
| `pathPrefix` | 兼容旧配置的单路径字段，写回时与 `pathPrefixes[0]` 同步。 |
| `targetUrl` | 默认地址（兜底）；API 入参可省略协议，保存时默认补 `http://`。 |
| `accessPageBaseUrl` | 代理地址；本地监听请求命中 `pathPrefixes` 时转发到此地址。配置路径前缀时必填。 |
| `accessPage` | 可选访问页；管理后台“访问”按钮优先打开该路径或绝对 URL。 |
| `localIp` | 本地监听 IP；空值且配置本地端口时默认 `127.0.0.1`。 |
| `localPort` | 本地监听端口；当前管理 API/后台表单要求填写 `1-65535`。 |
| `enabled` | 是否启用；禁用后保留配置文件，但不注册 Gateway 路由，也不启动本地端口代理。 |

## 校验规则

- `id` 只允许英文、数字、下划线和连字符。
- `name` 不能为空，不能与其他路由重复。
- `pathPrefixes` 可以为空；为空时本地监听请求走默认地址。配置路径前缀时代理地址必填。
- 每个路径前缀必须以 `/` 开头，只允许英文、数字、下划线、连字符和 `/`。
- 非根路径末尾 `/` 会被规范化移除，例如 `/api/` -> `/api`。
- 同一路由内路径前缀不能重复；不同路由可复用相同前缀。
- `targetUrl` 入参格式为 `host:port` 或 `http(s)://host:port`，保存前会归一化为带协议 URL；可与其他路由重复。
- `accessPageBaseUrl` 入参格式为 `host:port` 或 `http(s)://host:port`。
- `localPort` 当前管理 API/后台表单必填，范围为 `1-65535`。
- `localIp` 允许空值、`localhost` 或有效 IPv4；非空时会被校验。
- `localIp:localPort` 不能与其他启用本地绑定的路由重复。

## Gateway 转发与本地端口代理区别

| 项目 | Gateway 转发 | 本地端口代理 |
| --- | --- | --- |
| 入口 | 应用主端口，例如 `8090` | 每条路由自己的 `localIp:localPort` |
| 匹配方式 | 按每个 `pathPrefixes` 注册 Gateway Path 谓词 | 根据是否命中当前路由 `pathPrefixes` 选择上游地址 |
| 前缀处理 | 会按路径层级 `StripPrefix` | 不剥离前缀 |
| 转发路径 | 剥离前缀后的路径 | 原始请求 URI |
| 未命中路径 | 不命中该 Gateway 路由 | 转发到 `targetUrl` 默认地址 |

例如配置：

```json
{
  "pathPrefixes": ["/test"],
  "targetUrl": "http://localhost:8081",
  "accessPageBaseUrl": "http://localhost:8082"
}
```

请求 `/test/hello` 时：

- Gateway 转发到默认地址 `/hello`。
- 本地端口代理命中前缀时转发到代理地址 `/test/hello`。

## 请求日志

请求日志统计包括：

- 总请求数。
- 去重 IP 数。
- 按 IP 聚合的请求次数。
- Top 路径统计。
- 慢请求 Top。
- 最近 100 条请求日志。

日志统计保存在内存中，应用重启后会清空。多路径派生 routeId 会归并到基础路由 ID。
