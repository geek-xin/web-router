# 开发指南

面向维护者：关键文件、修改约定与验证方式。

## 环境

| 工具 | 版本 |
| --- | --- |
| JDK | 21（`JAVA_HOME` 必须指向 21；更高版本会因 Lombok 与 javac 不兼容而编译失败） |
| Maven | 3.9+ |
| Node.js | 22（仅改前端时需要） |

## 关键文件

| 文件 | 说明 |
| --- | --- |
| `src/main/java/com/geek/webrouter/Application.java` | 应用入口 |
| `config/DynamicRouteService.java` | 动态 Gateway 路由注册与差量刷新 |
| `config/LocalPortProxyService.java` | 每路由本地端口代理 |
| `config/ProxyRequestLogFilter.java` | Gateway 请求日志过滤器 |
| `config/AdminCacheControlFilter.java` | 入口页不缓存、版本化资源长缓存 |
| `web/service/impl/RouteConfigServiceImpl.java` | 路由 JSON 读写、校验、冲突检测 |
| `web/service/ProxyRequestLogService.java` | 内存统计、最近日志与 SSE |
| `web/controller/RouteConfigController.java` | 管理页面与路由 REST API |
| `web/controller/ProxyRequestLogController.java` | 日志 REST / SSE API |
| `web/controller/AppVersionController.java` | 版本信息与自动更新 API |
| `web/service/AutoUpdateService.java` | 无感自动更新调度 |
| `web/service/impl/UpdateServiceImpl.java` | 检查、下载校验、生成脚本、退出 |
| `web/support/ReleaseAssets.java` | 发布资产命名契约（权威实现） |
| `web/support/AssetVersionResolver.java` | 静态资源版本串（内容哈希） |
| `web/support/AppHomeResolver.java` | 应用根目录解析 |
| `src/main/resources/templates/index.html` | Thymeleaf 挂载页 |
| `frontend/src/App.tsx`、`frontend/src/features/**` | React 管理后台 |
| `frontend/src/styles.css` | 唯一权威样式表 |
| `docs/CORE-FEATURES.md` | 核心功能权威说明 |

## 修改约定

### 动态路由

改 `DynamicRouteService`：写操作后必须刷新，否则 Gateway 不会立即生效。
多前缀会派生 `<id>__<n>` 的 routeId，日志统计通常要归并回基础 ID。

### 本地端口代理

改 `LocalPortProxyService`：注意刷新时停止 / 原地更新 / 重启的区分，以及端口冲突处理。
新增任何代理入口时，必须在请求进入与结束时调用 `InFlightRequestTracker.begin()` / `end()`，
否则自动更新会在有流量时重启并掐断请求。

### 配置存储

改 `RouteConfigServiceImpl`：保持「文件名 = 路由 ID」机制，不要绕过 `resolveFilePath()`，
保持旧字段 `pathPrefix` 与 `pathPrefixes[0]` 同步。

### 新增路由字段

需要同步：`RouteConfig`、`RouteConfigDto`、`RouteConfigServiceImpl`（读写 / 校验 / 冲突检测）、
`frontend/src/features/routes/**`（表单 / 列表 / 复制 / 编辑 / JSON 预览）、构建后的静态资源，并考虑旧 JSON 兼容。

### 前端

```bash
cd frontend
npm ci
npm run typecheck
npm test
npm run build     # 产物写入 src/main/resources/static/admin/
```

改 UI 前先读 `docs/UI-DESIGN-SYSTEM.md`，改完同步 `docs/UI-IMPLEMENTATION.md`——
`AdminUiContractTest` 会校验规范中的类名、令牌与数据契约。

### 核心机制与文档

改路由配置、动态转发、本地端口代理、请求日志/指标、管理 API、校验规则、版本与更新后，
必须同步 `docs/CORE-FEATURES.md`——`CoreFeaturesDocContractTest` 会校验阈值、接口路径、错误码与文档互链。

### 打包

改打包脚本或资产命名时，同步 `ReleaseAssets` 与 `docs/PACKAGING.md`；
`scripts/build-release.sh` 会在发布前逐项校验资产名。

## 验证命令

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
mvn test                                            # 198 个后端测试
cd frontend && npx vitest run                       # 74 个前端单测
```

## 发布流程

1. 提升版本号：`pom.xml`、`frontend/package.json`、`frontend/src/lib/app-meta.ts`。
2. 更新 `CHANGELOG.md`（把「未发布」改为新版本号与日期）。
3. 提交并推送 `main`。
4. 打 tag 并推送：`git tag v<version> && git push origin v<version>`。
5. CI（`.github/workflows/release.yml`）在四平台矩阵构建并发布 GitHub Release。

也可以手动触发：Actions → Release → Run workflow，填 `version` 与 `prerelease`。
