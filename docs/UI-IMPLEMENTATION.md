# wrouter UI 实现映射

把 [UI 设计系统](UI-DESIGN-SYSTEM.md) 落到具体文件、类名与数据契约上。
**改动 UI 时必须同步更新本文**；`src/test/java/com/geek/webrouter/AdminUiContractTest.java` 会校验其中关键契约。

---

## 1. 文件职责

| 文件 | 职责 |
| --- | --- |
| `frontend/tailwind.config.js` | 令牌的 Tailwind 投影：`surface.*` 颜色**全部指向 CSS 变量**、圆角、字体、关键帧 |
| `frontend/src/styles.css` | 唯一权威样式表：CSS 变量（浅/深两套）、基础层、全部组件类、响应式断点 |
| `frontend/vite.config.ts` | 构建入口；`base: '/admin/'`、产物固定名 `assets/app.js` / `assets/app.css`、vitest 配置 |
| `frontend/src/lib/preferences.ts` | 主题与视图偏好的读写、`data-theme` 应用、系统主题探测 |
| `frontend/src/lib/utils.ts` | `cn()`、`stripProtocol()`、`formatDuration()`、`formatTime()`、`copyText()` |
| `frontend/src/components/ui/*` | 基础组件（button / card / badge / input / textarea / checkbox / tabs / dialog / alert-dialog / table） |
| `frontend/src/App.tsx` | 应用外壳：`AppSidebar`、`AppTopbar`、`OverviewBand`、`SplitStat`、路由视图切换、抽屉编排 |
| `frontend/src/features/routes/RouteCard.tsx` | 卡片视图 |
| `frontend/src/features/routes/RouteList.tsx` | 列表视图（与卡片共享数据与 Accent） |
| `frontend/src/features/routes/RouteToolbar.tsx` | 筛选 / 排序 / 视图切换 / 导入导出 / 新增 |
| `frontend/src/features/routes/RouteDetailDrawer.tsx` | Topology-first 详情抽屉 |
| `frontend/src/features/routes/RouteTopology.tsx` | 拓扑图（Client → wrouter → 端口 → 目标） |
| `frontend/src/features/routes/RouteSparkline.tsx` | 卡片/列表共用的迷你流量曲线 |
| `frontend/src/features/routes/RouteFormDialog.tsx` | 新增/编辑/拷贝路由表单（`RouteFormDialog` + 可复用 `RouteFormPanel`） |
| `frontend/src/features/routes/DeleteConfirmDialog.tsx` | 删除二次确认 |
| `frontend/src/features/routes/route-utils.ts` | 路由派生数据、Accent 分配、状态判定与类名、筛选与排序、校验 |
| `frontend/src/features/routes/route-detail-utils.ts` | 拓扑模型构建、抽屉指标、JSON 格式化 |
| `frontend/src/features/routes/route-metrics.ts` | 指标类型、格式化、Sparkline 几何 |
| `frontend/src/features/logs/RouteLogDialog.tsx` | 日志面板（`RouteLogPanel`）+ 请求详情抽屉 |
| `frontend/src/features/logs/log-utils.ts` | 日志归并、Top 排序、快照归一 |
| `frontend/src/features/system/VersionDialog.tsx` | 版本与自动更新抽屉 |
| `frontend/src/features/system/version-utils.ts` | 平台/安装方式文案、构建时间与体积格式化、更新可用性判定 |
| `frontend/src/lib/app-meta.ts` | 版本号兜底、Gateway 地址、仓库名、流量窗口 |
| `src/main/resources/templates/index.html` | Thymeleaf 挂载页 + 资源版本号 |
| `frontend/public/favicon.svg` | 品牌图形（节点 + 连线） |
| `src/main/resources/static/admin/**` | Vite 构建产物（由 `npm run build` 生成，需提交） |

---

## 2. 令牌映射

### 2.1 颜色

| 规范 Token | CSS 变量 | Tailwind | 典型用法 |
| --- | --- | --- | --- |
| 画布 | `--surface-canvas` | `bg-surface-canvas` | body、`.app-canvas` |
| 外壳 | `--surface-shell` | `bg-surface-shell` | `.app-shell` |
| 下沉底 | `--surface-sunken` | `bg-surface-sunken` | Sidebar、输入底、表头、指标卡 |
| 面板 | `--surface-panel` | `bg-surface-panel` | 卡片、面板、控件 |
| Hover 面板 | `--surface-panel-hover` | `bg-surface-panel-hover` | 卡片/行 hover |
| 浮层 | `--surface-raised` | `bg-surface-raised` | Drawer、Dialog、Toast |
| 边框 | `--surface-line` / `-strong` / `-faint` | `border-surface-line` 等 | 全部 |
| 文本 | `--surface-ink` / `-muted` / `-subtle` | `text-surface-ink` 等 | 全部 |
| 主色 | `--surface-accent` | `bg-surface-accent` | 焦点环、选中态 |
| 主按钮底 | `--surface-navy` | `bg-surface-navy` | `.btn-primary` |
| 状态色 | `--surface-success/warning/error` | `text-surface-success` 等 | 状态与徽标 |
| 概览带墨色 | `--band-ink` / `--band-ink-muted` | — | 渐变上的文字 |
| Accent | `--accent` | — | Route Card / List |

**Tailwind 调色板必须写成 `var(--surface-*)`**（见 `tailwind.config.js`），
否则工具类不会跟随 `data-theme` 切换。

### 2.2 圆角、阴影与尺寸

| 规范 | 令牌 / 类 |
| --- | --- |
| Button / Input 10px | `--radius-control` / `rounded-control` |
| 拓扑节点 10px | `--radius-node` / `rounded-node` |
| Card 14px | `--radius-card` / `rounded-card` |
| 外壳 / 概览带 20px | `--radius-band` / `rounded-band` |
| Drawer / Dialog 18px | `--radius-panel` / `rounded-panel` |
| 胶囊 | `--radius-pill` |
| 卡片阴影 | `--shadow-card` |
| 选中光环 | `--shadow-select` |
| 浮层阴影 | `--shadow-float` |
| 侧栏宽度 | `--sidebar-width`（236px） |

### 2.3 字体

| 角色 | 令牌 | Tailwind |
| --- | --- | --- |
| 正文 | `--font-sans` | `font-sans` |
| 标题 | `--font-display` | `font-display` |
| 技术数据 | `--font-mono` | `font-mono` / `.mono` / `.numeric` |

---

## 3. 组件类名契约

以下清单与 `frontend/src/styles.css` 一一对应，按样式表章节分组。

### 3.1 应用骨架

`.app-canvas` · `.app-shell` · `.app-shell-collapsed` · `.app-main` · `.app-content` ·
`.panel-scroll` · `.panel-footnote`

> `.app-canvas` / `.app-shell` 锁 `100dvh` 且 `overflow: hidden`，**页面不滚动**；
> `.panel-scroll` 是唯一滚动区（`flex:1; min-height:0; overflow-y:auto`）。
> `.panel-footnote` / `.toolbar` / `.band` 为 `flex: none`，常驻不滚。详见设计系统 §5.5。

### 3.2 侧边栏

`.app-sidebar` · `.brand` · `.brand-mark` · `.brand-text` · `.brand-name` · `.brand-sub` ·
`.sidebar-footer` · `.sidebar-toggle` · `.sidebar-toggle-label` ·
`.side-section` · `.side-section-status` · `.side-label` · `.side-spacer` ·
`.nav-item` · `.nav-item-active` · `.nav-icon` · `.nav-text` · `.nav-count` ·
`.status-panel` · `.status-panel-head` · `.status-panel-title` · `.status-panel-address` · `.status-panel-meta` · `.status-panel-paths`

收起态由 `.app-shell-collapsed` 承担（覆盖 `--sidebar-width` 为 76px 并隐藏文案）；
侧栏项在收起态必须带 `title`，`.sidebar-toggle` 必须带 `aria-label` + `aria-expanded`。
收起按钮位于 `.sidebar-footer`（侧栏最下方）；≤980px 横向导航形态下整块 `.sidebar-footer` 隐藏。

### 3.3 状态点

`.dot` + `.state-running` · `.state-stopped` · `.state-warning` · `.state-error` · `.state-checking`

> 状态点用 `state-*`；日志 HTTP 状态码用 `status-*`（见 3.11）。**两套命名空间不可混用**，
> `routeStatusDotClass()` 必须返回 `state-*`。

### 3.4 顶栏

`.app-topbar` · `.topbar-heading` · `.topbar-title` · `.topbar-subtitle` · `.topbar-actions` ·
`.topbar-search` · `.topbar-search-icon` · `.topbar-search-key` ·
`.state-pill` · `.state-pill-address` · `.version-pill` ·
`.icon-button` · `.icon-button-dot` · `.topbar-avatar`

### 3.5 概览带

`.band` · `.band-lead` · `.band-lead-head` · `.band-lead-icon` · `.band-lead-title` ·
`.band-lead-figure` · `.band-lead-delta` · `.band-delta-chip` · `.band-lead-split` ·
`.band-split-item` · `.band-split-label` · `.band-split-value` · `.band-split-figure` · `.band-split-unit` ·
`.band-chart` · `.band-chart-head` · `.band-chart-label` · `.band-chart-figure` · `.band-chart-note` ·
`.band-bars` · `.band-bars-empty` · `.band-bar` · `.band-bar-hot` · `.band-bar-ticks` · `.band-bar-tick` ·
`.band-lead-figure-row` · `.band-toggle` ·
`.band-collapsed` · `.band-summary` · `.band-summary-icon` · `.band-summary-figure` · `.band-summary-unit` ·
`.band-summary-sep` · `.band-summary-text` · `.band-summary-stats` · `.band-summary-stat`

### 3.6 面板与工具栏

`.surface-panel` · `.toolbar` · `.toolbar-heading` · `.toolbar-title` · `.toolbar-note` ·
`.search-field` · `.search-field-icon` · `.segmented` · `.segmented-option` · `.segmented-option-active`

### 3.7 控件

`.field-input` · `.field-select` ·
`.btn` · `.btn-primary` · `.btn-danger` · `.btn-ghost` · `.btn-sm` · `.btn-icon` ·
`.chip` · `.chip-accent` · `.chip-success` · `.chip-warning` · `.chip-danger`

### 3.8 路由卡片

`.route-card-board` · `.route-card` · `.route-card-selected` · `.route-card-muted` ·
`.route-card-head` · `.route-card-icon` · `.route-card-title` · `.route-card-name` · `.route-card-desc` · `.route-card-status` ·
`.route-card-body` · `.route-card-path` · `.route-prefix-chip` · `.route-prefix-chip-more` ·
`.route-card-meta` · `.route-field-label` · `.route-field-value` ·
`.route-card-metrics` · `.route-metric` · `.route-metric-figure` · `.route-metric-value` · `.route-metric-unit` · `.route-metric-caption` ·
`.route-card-spark` · `.route-card-actions` · `.route-card-action` · `.route-card-action-label` · `.route-card-action-danger` ·
`.accent-blue` · `.accent-cyan` · `.accent-purple` · `.accent-orange` · `.accent-green` · `.accent-slate` ·
`.sparkline` · `.sparkline-line` · `.sparkline-area` · `.sparkline-idle`

### 3.9 路由列表

`.route-list` · `.route-list-head` · `.route-list-head-cell` · `.route-list-row` · `.route-list-row-selected` · `.route-list-row-muted` ·
`.route-list-main` · `.route-list-name` · `.route-list-desc` · `.route-list-paths` · `.route-list-targets` · `.route-list-port` ·
`.route-list-metrics` · `.route-list-num` · `.route-list-stacked-label` · `.route-list-spark` · `.route-list-actions`

### 3.10 抽屉、拓扑与弹窗

`.drawer-scrim` · `.drawer` · `.drawer-head` · `.drawer-title-row` · `.drawer-title` · `.drawer-file` · `.drawer-paths` ·
`.drawer-tabs` · `.drawer-tab` · `.drawer-body` · `.drawer-actions` ·
`.section` · `.section-head` · `.section-index` · `.section-title` · `.field-grid` · `.field` · `.field-value` ·
`.metric-grid` · `.metric-grid-5` · `.metric-figure` · `.log-rows` · `.log-row` · `.log-row-path` · `.json-editor` ·
`.topology` · `.topology-node` · `.topology-node-client` · `.topology-node-gateway` · `.topology-node-port` · `.topology-node-target` ·
`.topology-node-icon` · `.topology-node-body` ·
`.topology-node-kicker` · `.topology-node-value` · `.topology-node-note` ·
`.topology-link` · `.topology-link-flow` · `.topology-link-idle` · `.topology-fallback` · `.topology-fallback-label` · `.topology-fallback-value` ·
`.overlay` · `.modal` · `.modal-title` · `.modal-description` · `.modal-close` · `.modal-actions` ·
`.form-grid` · `.form-field` · `.form-label` · `.form-hint` · `.required-mark` · `.alert-error` · `.prefix-editor` · `.prefix-remove`

### 3.11 日志表

`.table-wrap` · `.data-table` · `.cell-mono` ·
`.method-badge` + `.method-get` · `.method-post` · `.method-put` · `.method-delete` · `.method-other` ·
`.status-badge` + `.status-2xx` · `.status-3xx` · `.status-4xx` · `.status-5xx` · `.status-none`

### 3.12 空态、骨架、滚动与轻提示

`.empty-state` · `.empty-title` · `.empty-copy` · `.skeleton` · `.scroll-area` · `[data-sonner-toast]`

> 全站**没有** `.network-backdrop`：分层由 Paper Shell 承担，背景装饰已删除。

---

## 4. 主题与偏好

| 项 | 实现 |
| --- | --- |
| 主题取值 | `ThemeMode = 'dark' | 'light'`（`lib/preferences.ts`） |
| 视图取值 | `RouteViewMode = 'card' | 'list'` |
| 侧栏取值 | `SidebarMode = 'expanded' | 'collapsed'`，**默认 `collapsed`**（`DEFAULT_SIDEBAR_MODE`） |
| 概览带取值 | `BandMode = 'expanded' | 'collapsed'`，**默认 `expanded`**（`DEFAULT_BAND_MODE`） |
| 存储键 | `wrouter.theme`、`wrouter.routeView`、`wrouter.sidebar`、`wrouter.band` |
| 应用方式 | `applyTheme()` 写 `<html data-theme>` 并同步 `style.colorScheme` |
| 默认 | 未存偏好时用 `prefersDarkScheme()` 跟随系统；系统变化时实时同步，用户显式选择后不再跟随 |
| 存储降级 | `safeStorage()` 在 localStorage 不可用（隐私模式/测试环境）时返回 `null`，偏好只在当前会话生效 |

---

## 5. 数据契约

### 5.1 路由配置

沿用 `RouteConfig`：`id`、`name`、`pathPrefix`、`pathPrefixes`、`targetUrl`、
`accessPageBaseUrl`、`accessPage`、`localIp`、`localPort`、`enabled`。

### 5.2 流量指标

`GET /admin/api/proxy-logs` 与 `/routes/{routeId}` 的 `ProxyRequestLogSnapshot` 在既有字段之后追加：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `requestsLastMinute` | `long` | 最近 60 秒滚动请求数 |
| `failedLastMinute` | `long` | 最近 60 秒滚动失败数（status ≥ 400） |
| `averageDurationMs` | `long` | 累计平均耗时 |
| `trafficBuckets` | `long[30]` | 最近 30 分钟每分钟请求数，由旧到新 |

紧凑接口 `GET /admin/api/proxy-logs/metrics` 返回 `Map<routeId, RouteTrafficMetrics>`，
字段为 `routeId`、`totalRequests`、`failedRequests`、`slowRequests`、`totalDurationMs`、
`requestsLastMinute`、`failedLastMinute`、`averageDurationMs`、`trafficBuckets`。
路由卡片、列表行与概览带都用它，避免逐条拉取日志明细。

降级：字段缺失 → 显示 `—`，Sparkline 退化为水平基线，**不得抛错**。

### 5.3 版本与更新

| 接口 | 用途 |
| --- | --- |
| `GET /admin/api/version` | 顶栏版本胶囊、侧边栏运行卡、版本抽屉「当前安装」 |
| `GET /admin/api/update/check` | 打开抽屉时静默检查一次，也可手动检查 |
| `POST /admin/api/update/apply` | 下载 + 校验 + 生成更新脚本，随后应用自动重启 |

降级：`/version` 失败 → 用 `app-meta.ts` 兜底常量；`/update/check` 失败 → 显示后端返回的 `message`；
「一键更新」仅在 `updateAvailable && assetUrl && updateSupported` 同时成立时可用。

### 5.4 状态语义

| 界面状态 | 判定 | 类名 |
| --- | --- | --- |
| 运行中 | `enabled` 且失败率 ≤ 5% | `state-running` |
| 已停用 | `!enabled` | `state-stopped` |
| 异常 | `enabled` 且失败率 > 5% | `state-warning` |
| 错误 | `enabled` 且失败率 ≥ 20% | `state-error` |

失败率在 `requestsLastMinute > 0` 时用 `failedLastMinute / requestsLastMinute`，否则退回累计口径。
判定入口：`deriveRouteStatus()`；文案：`routeStatusText()`；类名：`routeStatusDotClass()`。

---

## 6. 构建与样式加载顺序

| 项 | 说明 |
| --- | --- |
| 构建命令 | `npm run build`（= `npm run typecheck` + `vite build`） |
| 类型检查 | `tsc -p tsconfig.app.json --noEmit && tsc -p tsconfig.node.json --noEmit` |
| 产物 | `src/main/resources/static/admin/assets/app.js` / `app.css`（固定文件名，需提交） |
| 缓存失效 | `src/main/resources/templates/index.html` 里的 `?v=` 查询串 |
| 样式分层 | `@tailwind base` / `@tailwind components` 在文件顶部，**`@tailwind utilities` 在文件末尾** |

> **为什么 utilities 必须在末尾**：组件类与 Tailwind 工具类特异性相同（都是单类），
> 靠源码顺序决定胜负。若 utilities 在组件类之前输出，`w-[min(92vw,430px)]` 会被 `.modal { width }` 覆盖，
> 删除确认弹窗会撑到 780px。改动样式表结构时务必保持这个顺序。

---

## 7. 相关文档

| 文档 | 内容 |
| --- | --- |
| [核心功能](CORE-FEATURES.md) | 后端能力、接口与数据口径的权威说明 |
| [UI 设计系统](UI-DESIGN-SYSTEM.md) | 本文映射的视觉与交互规范 |
| [打包与发布](PACKAGING.md) | 交付形态与自动更新 |

---

## 8. 变更检查表

1. 新颜色 / 圆角 / 阴影是否来自设计系统令牌与 `styles.css`，而不是硬编码十六进制。
2. 新增 Tailwind 颜色是否写成 `var(--surface-*)`。
3. 新组件是否复用 `.surface-panel` / `.band-*` / `.drawer-*` / `.field-*` 等既有语义类名，而不是另起一套。
4. 是否仍满足设计系统 §11 验收清单（含**浅深两套对比度都达标**）。
5. 状态点是否用 `state-*`、日志状态码是否用 `status-*`。
6. 是否同步更新本文的类名清单与数据契约表。
7. 是否运行 `npm run typecheck`、`npm test`、`mvn test`。
8. 是否运行 `npm run build` 并提交 `src/main/resources/static/admin/**` 产物。
