<div align="center">

# 开发指南

**环境、代码地图、修改约定与发布流程**

</div>

---

## 环境

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **21** | `JAVA_HOME` 必须指向 21；更高版本会因 Lombok 与 javac 不兼容而编译失败 |
| Maven | 3.9+ | |
| Node.js | 22 | 仅改前端时需要 |

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
mvn -v | head -3                                    # 确认 Java version: 21
```

## 代码地图

```text
src/main/java/com/geek/webrouter/
├── Application.java                  应用入口
├── config/
│   ├── DynamicRouteService.java      Gateway 动态路由（差量刷新）
│   ├── LocalPortProxyService.java    每路由本地端口代理
│   ├── ProxyRequestLogFilter.java    Gateway 请求日志过滤器
│   ├── AdminCacheControlFilter.java  入口页不缓存
│   └── UpdateProperties.java         wrouter.update.* 配置
└── web/
    ├── controller/                   页面 + REST/SSE API
    ├── service/                      业务服务（含 AutoUpdateService）
    ├── service/impl/                 实现（路由读写、更新、版本）
    └── support/                      ReleaseAssets / AppHomeResolver / AssetVersionResolver …

frontend/src/
├── App.tsx                           应用外壳：侧边栏、顶栏、概览带、路由网格
├── features/routes/                  卡片、列表、表单、详情、拓扑、指标
├── features/logs/                    日志面板与请求详情
├── features/system/                  版本抽屉、更新日志铃铛
├── components/ui/                    基础组件（button / input / select / dialog …）
└── styles.css                        唯一权威样式表
```

## 修改约定

### 动态路由

改 `DynamicRouteService`：写操作后必须刷新，否则 Gateway 不会立即生效。
多前缀会派生 `<id>__<n>` 的 routeId，日志统计通常要归并回基础 ID。

### 本地端口代理

改 `LocalPortProxyService`：注意刷新时停止 / 原地更新 / 重启的区分，以及端口冲突处理。
**新增任何代理入口时，必须在请求进入与结束时调用 `InFlightRequestTracker.begin()` / `end()`**，
否则自动更新会在有流量时重启并掐断请求。

### 配置存储

改 `RouteConfigServiceImpl`：保持「文件名 = 路由 ID」机制，不要绕过 `resolveFilePath()`，
保持旧字段 `pathPrefix` 与 `pathPrefixes[0]` 同步。

### 新增路由字段

需要同步：

1. `RouteConfig`、`RouteConfigDto`
2. `RouteConfigServiceImpl`（读写 / 校验 / 冲突检测）
3. `frontend/src/features/routes/**`（表单 / 列表 / 复制 / 编辑 / JSON 预览）
4. 重新构建静态资源
5. 考虑旧 JSON 兼容

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

## 验证

```bash
mvn test                    # 198 个后端测试（含 UI 与文档契约测试）
cd frontend && npx vitest run   # 74 个前端单测
```

契约测试是这套仓库的「文档防过期」机制：文档与实现不一致时测试直接失败。

## 发布流程

1. 提升版本号：`pom.xml`、`frontend/package.json`（含 lock）、`frontend/src/lib/app-meta.ts`。
2. 更新 `CHANGELOG.md`：把「未发布」改为新版本号与日期。
3. 提交并推送 `main`。
4. 打 tag 并推送：

   ```bash
   git tag v<version> && git push origin v<version>
   ```

5. CI（`.github/workflows/release.yml`）在四平台矩阵构建并发布 GitHub Release，
   正文自动带上 `CHANGELOG.md` 对应版本的说明。

也可以手动触发：Actions → Release → Run workflow，填 `version` 与 `prerelease`。

### 本地打包

```bash
scripts/build-release.sh --platform macos-arm64 --only jar,app-image,installer --skip-tests
# 产物在 target/release/，含 SHA256SUMS.txt 与 release-manifest.txt
```

jpackage 不能交叉构建：一个平台必须在对应机器（或 CI 矩阵）上打包。
