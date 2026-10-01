# wrouter 使用说明

本文说明如何启动、配置和使用 `wrouter` `1.3.0`，并解释 Gateway 转发、本地端口代理、请求日志和管理 API 的当前行为。

## 环境要求

- JDK 21
- Maven 3.9+
- Git（用于版本管理和发布）

## 启动应用

```bash
mvn test
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:9999`。

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
  "accessPageBaseUrl": "http://localhost:8082",
  "accessPage": "/test/hello",
  "localIp": "127.0.0.1",
  "localPort": 18081,
  "enabled": true
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 内部路由 ID；创建时自动生成，用作配置文件名和 Gateway routeId 基础值。 |
| `name` | 展示名称，不能为空，不能与其他路由重复。 |
| `pathPrefixes` | 路径前缀列表；同一路由内不能重复，不同路由可复用相同前缀。为空时本地监听请求走默认地址。 |
| `pathPrefix` | 兼容旧配置的单路径字段，写回时与 `pathPrefixes[0]` 同步。 |
| `targetUrl` | 默认地址（兜底）；API 入参可省略协议，保存时默认补 `http://`。 |
| `accessPageBaseUrl` | 代理地址；本地监听请求命中 `pathPrefixes` 时转发到此地址。配置路径前缀时必填。 |
| `accessPage` | 可选访问页；管理后台“访问”按钮优先打开该路径或绝对 URL。 |
| `localIp` | 本地监听 IP；空值且配置了本地端口时默认 `127.0.0.1`。 |
| `localPort` | 本地监听端口；当前管理 API/后台表单要求填写 `1-65535`。 |
| `enabled` | 是否启用；禁用后保留文件，但不注册 Gateway 路由，也不启动本地端口代理。 |

## 管理后台

打开 <http://localhost:9999/admin> 后可以：

- 在**卡片式路由列表**中浏览所有路由，每张卡片显示 Path 前缀、Target、本地端口、状态、每分钟请求数、平均延迟和最近 30 分钟流量曲线。
- 通过 KPI 行（路由总数 / 运行中 / 已停用 / 请求数·分钟 / 平均延迟）一键筛选状态。
- 用搜索、状态筛选和排序（最近创建 / 名称 / 流量 / 延迟）定位路由。
- 新增、编辑、拷贝、删除路由，启用或禁用路由。
- 为一条路由配置多个路径前缀。
- 配置本地监听 IP/端口、默认地址（兜底）、代理地址和访问页。
- 打开右侧详情抽屉：概览（请求链路拓扑 + 基本信息 + 运行状态 + 最近日志）、配置（表单 + JSON 实时同步）、日志、指标。
- 在「日志」视图中查看单路由实时流、Top 路径、单耗时 Top、诊断分析。
- 查看原始 JSON 配置和配置目录。
- 通过侧边栏导出 / 导入配置，或复制 Gateway / 本地端口访问地址。

保存路由后，后台会立即刷新 Gateway 路由和本地端口代理。

界面视觉与交互规范见 [docs/UI-DESIGN-SYSTEM.md](./docs/UI-DESIGN-SYSTEM.md)，实现映射见 [docs/UI-IMPLEMENTATION.md](./docs/UI-IMPLEMENTATION.md)，
后端核心能力（配置模型、动态转发、本地端口代理、请求观测、API 与校验规则）见 [docs/CORE-FEATURES.md](./docs/CORE-FEATURES.md)。

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
  "accessPageBaseUrl": "http://localhost:8082",
  "localIp": "127.0.0.1",
  "localPort": 18081,
  "enabled": true
}
```

请求：

```bash
curl http://localhost:9999/test/hello
```

上游请求地址：

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
  "accessPageBaseUrl": "http://localhost:8082",
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
http://localhost:8082/test/hello
```

本地端口代理特点：

- 只对 `enabled=true` 且设置了 `localPort` 的路由启动。
- `localIp` 为空时使用 `127.0.0.1`。
- 命中当前路由 `pathPrefixes` 的请求转发到 `accessPageBaseUrl`。
- 未命中当前路由 `pathPrefixes` 的请求转发到 `targetUrl` 默认地址。
- 不执行 `StripPrefix`，原始请求 URI 会追加到选中的上游地址后。
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

> URL 路径中的路由标识传入配置文件 ID，例如 `route-20260603234846-4d2deb`。控制器内部变量名仍为 `{name}`，对外语义按 routeId 使用。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/api/routes` | 查询全部路由，按配置文件最后修改时间倒序。 |
| `GET` | `/admin/api/routes/{routeId}` | 查询单条路由。 |
| `GET` | `/admin/api/routes/{routeId}/raw` | 查看原始 JSON 文件内容。 |
| `POST` | `/admin/api/routes` | 创建路由并刷新代理。 |
| `PUT` | `/admin/api/routes/{routeId}` | 更新路由并刷新代理。 |
| `DELETE` | `/admin/api/routes/{routeId}` | 删除路由并刷新代理。 |

创建示例：

```bash
curl -X POST http://localhost:9999/admin/api/routes \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test"],
    "targetUrl": "localhost:8081",
    "accessPageBaseUrl": "localhost:8082",
    "accessPage": "/test/hello",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

更新示例：

```bash
curl -X PUT http://localhost:9999/admin/api/routes/route-20260603234846-4d2deb \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test", "/api/test"],
    "targetUrl": "http://localhost:8081",
    "accessPageBaseUrl": "http://localhost:8082",
    "accessPage": "/test/hello",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

删除示例：

```bash
curl -X DELETE http://localhost:9999/admin/api/routes/route-20260603234846-4d2deb
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
- 慢请求 Top。
- 最近 100 条请求日志。

订阅 SSE：

```bash
curl -N http://localhost:9999/admin/api/proxy-logs/stream
```

## 校验规则

- `id` 只允许英文、数字、下划线和连字符。
- `name` 不能为空，不能与其他路由重复。
- `pathPrefixes` 可以为空；每项必须以 `/` 开头，只允许英文、数字、下划线、连字符和 `/`。
- 路径前缀会规范化：非根路径末尾 `/` 会被移除，例如 `/api/` -> `/api`。
- 路径前缀冲突只判断“完全相同前缀”，不判断父子包含关系。
- `targetUrl` 入参格式为 `host:port` 或 `http(s)://host:port`，保存前归一化为带协议 URL；可与其他路由重复。
- `accessPageBaseUrl` 入参格式为 `host:port` 或 `http(s)://host:port`；配置路径前缀时不能为空。
- `localPort` 当前管理 API/后台表单必填，范围为 `1-65535`。
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
target/wrouter-1.3.0-jar.tar.gz
target/dist/wrouter-1.3.0-jar.tar.gz
target/wrouter-1.3.0-jar.zip
target/dist/wrouter-1.3.0-jar.zip
```

发布包包含：

- Spring Boot 可执行 JAR。
- Linux/macOS：`run.sh` 一键后台启动脚本，启动后写入 `wrouter.pid`，日志输出到 `logs/wrouter.out`。
- Linux/macOS：`stop.sh` 一键停止脚本，优先按 `wrouter.pid` 停止，必要时按当前包目录内 JAR 进程兜底停止。
- Windows：`run.bat` 一键后台启动脚本，启动后写入 `wrouter.pid`，日志输出到 `logs\wrouter.out` 和 `logs\wrouter.err`。
- Windows：`stop.bat` 一键停止脚本，优先按 `wrouter.pid` 停止，必要时按当前包目录内 JAR 进程兜底停止。
- `config/application.yml` 后台配置文件。
- `config/routes` 路由配置目录；如果本地已有 `config/routes/*.json`，会一并打入发布包。
- `README.md`、`USAGE.md`、`CHANGELOG.md`。

需要 Windows / macOS / Linux 的**安装包**或**免安装 app-image**（内置运行时，目标机无需 JDK）：

```bash
scripts/build-package.sh --type app-image --skip-tests   # 免安装镜像（当前平台）
scripts/build-package.sh --type installer --skip-tests   # 安装包：dmg / deb+rpm / exe
scripts/build-release.sh --only jar,app-image,installer  # 一次产出全部契约资产
```

jpackage 不能交叉构建，一个平台一台匹配的机器；产物落在 `target/release/`，并附带 `SHA256SUMS.txt`。
完整说明（资产命名契约、CI 发布、未签名提示）见[打包与发布](./docs/PACKAGING.md)。

### 版本号与自动更新

管理后台侧边栏与顶栏常驻显示当前版本；点击打开「版本与更新」抽屉，可查看版本号、构建时间、运行平台、
安装方式与更新通道，并检查/执行更新。

| 接口 | 作用 |
| --- | --- |
| `GET /admin/api/version` | 当前版本、构建时间、平台、安装方式、更新通道 |
| `GET /admin/api/update/check` | 比对 GitHub Release 并选出匹配当前平台的更新包 |
| `POST /admin/api/update/apply` | 下载校验后生成更新脚本，应用退出并重启完成升级 |

更新过程：下载 → 校验 SHA-256 → 生成脚本到 `<home>/updates/` → 应用退出 → 脚本备份并替换 → 重启。
失败会自动回滚，过程写入 `<home>/updates/update.log`，旧版本备份在 `<home>/updates/backup-<旧版本>/`。
仅 `jar` 与 `app-image` 两种形态支持自动更新；可选设置 `WROUTER_GITHUB_TOKEN` 提升 GitHub API 速率限制。

Linux/macOS 解压后启动和停止：

```bash
./run.sh
./stop.sh
```

Windows 解压后启动和停止：

```bat
run.bat
stop.bat
```

## 常见注意事项

- `config/routes` 受应用启动工作目录影响。
- 禁用路由会保留 JSON 文件，但不会注册 Gateway 路由或启动本地端口代理。
- Gateway 转发会剥离路径前缀；本地端口代理不会剥离路径前缀。
- 修改 `pathPrefixes` 后刷新只影响后续 HTTP 请求；已加载页面若没有重新请求，代理无法主动改变页面内状态。
- 请求日志保存在内存中，应用重启后清空。
- 管理后台源码位于 `frontend/`，发布前需要通过 `npm run build` 更新 Spring Boot 静态资源。
