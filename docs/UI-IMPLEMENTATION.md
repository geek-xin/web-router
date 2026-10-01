# wrouter UI 实现映射

把 [UI 设计系统](UI-DESIGN-SYSTEM.md) 落到具体文件、类名与数据契约上。
**改动 UI 时必须同步更新本文**；`src/test/java/com/geek/webrouter/AdminUiContractTest.java` 会校验其中关键契约。

---

## 1. 文件职责

| 文件 | 职责 |
| --- | --- |
| `frontend/tailwind.config.js` | 令牌的 Tailwind 投影：`console.*` 颜色**全部指向 CSS 变量**、圆角、阴影、字体、关键帧 |
| `frontend/src/styles.css` | CSS 变量（深/浅两套）、基础层、组件层 |
| `frontend/src/lib/preferences.ts` | 主题与视图偏好的读写、`data-theme` 应用、系统主题探测 |
| `frontend/src/components/ui/*` | 基础组件（button / card / badge / input / select / tabs / dialog / table / checkbox / textarea） |
| `frontend/src/App.tsx` | 应用外壳：Sidebar、Top Header、KPI 行、路由视图切换、抽屉编排 |
| `frontend/src/features/routes/RouteCard.tsx` | 卡片视图 |
| `frontend/src/features/routes/RouteList.tsx` | 列表视图（与卡片共享数据与 Accent） |
| `frontend/src/features/routes/RouteToolbar.tsx` | 搜索 / 筛选 / 排序 / 视图切换 / 新增 |
| `frontend/src/features/routes/RouteDetailDrawer.tsx` | Topology-first 详情抽屉 |
| `frontend/src/features/routes/RouteTopology.tsx` | 拓扑图（Client → wrouter → 端口 → 目标） |
| `frontend/src/features/routes/RouteSparkline.tsx` | 卡片/列表共用的迷你流量曲线 |
| `frontend/src/features/routes/route-utils.ts` | 路由派生数据、Accent 分配、状态判定、筛选与排序 |
| `frontend/src/features/routes/route-metrics.ts` | 指标类型、格式化、Sparkline 几何 |
| `frontend/src/features/system/VersionDialog.tsx` | 版本与自动更新抽屉 |
| `frontend/src/features/system/version-utils.ts` | 平台/安装方式文案、构建时间与体积格式化、更新可用性判定 |
| `frontend/src/features/logs/RouteLogDialog.tsx` | 日志与诊断视图 |
| `frontend/src/lib/app-meta.ts` | 版本号兜底、Gateway 地址、仓库名、流量窗口 |
| `src/main/resources/templates/index.html` | Thymeleaf 挂载页 + 资源版本号 |
| `frontend/public/favicon.svg` | 品牌图形（节点 + 连线） |

---

## 2. 令牌映射

### 2.1 颜色

| 规范 Token | CSS 变量 | Tailwind | 典型用法 |
| --- | --- | --- | --- |
| 应用底色 | `--console-bg` | `bg-console-bg` | body、Sidebar |
| 次级底色 | `--console-bg-alt` | `bg-console-bg-alt` | 输入框、表头 |
| 面板 | `--console-panel` | `bg-console-panel` | 卡片、KPI |
| Hover 面板 | `--console-panel-hover` | `bg-console-panel-hover` | 卡片/行 hover |
| 浮层 | `--console-raised` | `bg-console-raised` | Drawer、Dialog |
| 边框 | `--console-line` / `--console-line-strong` | `border-console-line` | 全部 |
| 文本 | `--console-ink` / `-muted` / `-subtle` | `text-console-ink` 等 | 全部 |
| 主色 | `--console-primary` | `bg-console-primary` | 主按钮、选中态 |
| 状态色 | `--console-success/warning/error` | `text-console-success` 等 | 状态与徽标 |
| Accent | `--accent-*` | — | Route Card / List |

**Tailwind 调色板必须写成 `var(--console-*)`**（见 `tailwind.config.js`），
否则工具类不会跟随 `data-theme` 切换。

### 2.2 圆角与阴影

| 规范 | Tailwind / 类 |
| --- | --- |
| Button / Input 7px | `rounded-control` |
| 拓扑节点 8px | `rounded-node` |
| Card 10px | `rounded-card` |
| Drawer / Dialog 12px | `rounded-panel` |
| 卡片阴影 | `shadow-card`（= `--console-shadow-card`） |
| 选中光环 | `shadow-select`（= `--console-shadow-select`） |

禁止在普通组件上使用 `20px` 以上圆角。

### 2.3 字体

| 角色 | Tailwind |
| --- | --- |
| 正文 | `font-sans`（Inter + PingFang SC + Noto Sans SC） |
| 技术数据 | `font-mono`（JetBrains Mono） + `tabular-nums` |

---

## 3. 组件类名契约

### 3.1 外壳与导航

| 类名 | 要点 |
| --- | --- |
| `.console-shell` | `grid-template-columns: 208px minmax(0,1fr)` |
| `.console-sidebar` | sticky + 100vh，右侧 1px 分隔 |
| `.console-main` / `.console-workspace` | 主列 / 内容区（最大 1536px，20px 内边距） |
| `.console-topbar` | 56px，sticky，`--console-topbar-bg` + 模糊 |
| `.version-pill` | 顶栏版本入口，<900px 隐藏 |
| `.side-brand` / `.side-group-label` / `.side-nav-item` / `.side-nav-item-active` / `.side-badge` | 侧边栏元素 |
| `.runtime-card` / `.runtime-card-version-button` | 底部网关运行卡与版本入口 |

### 3.2 KPI 与工具栏

| 类名 | 要点 |
| --- | --- |
| `.kpi-row` | 2 / 3 / 5 列断点 |
| `.kpi-card` / `.kpi-card-active` | 高度 ≤92px；选中态主色边框 |
| `.kpi-label` / `.kpi-value` / `.kpi-delta` / `.kpi-spark` | 11px 标签 / 24px 等宽数值 / 变化量 / 迷你曲线 |
| `.route-toolbar` | 搜索 + 筛选 + 排序 + 视图切换 + 操作 |
| `.console-search` / `.console-input` / `.console-select` | 38px 高，7px 圆角 |
| `.view-switch` / `.view-switch-option` / `.view-switch-option-active` | 卡片·列表分段控件 |

### 3.3 路由视图

| 类名 | 要点 |
| --- | --- |
| `.route-card-board` / `.route-card-board-with-drawer` | 1/2/3 列网格；抽屉打开时 2 列 |
| `.route-card` / `.route-card-selected` / `.route-card-muted` | 卡片主体与状态 |
| `.route-card-head` / `.route-card-body` / `.route-card-spark` / `.route-card-actions` | 卡片分区 |
| `.route-prefix-chip` / `.route-prefix-chip-more` | Path 标签与 `+N` |
| `.route-list` / `.route-list-head` / `.route-list-row` / `.route-list-row-selected` / `.route-list-row-muted` | 列表视图 |
| `.route-list-col-*` | 列表列（勾选框 / 数字 / 曲线 / 操作） |
| `.accent-blue` … `.accent-slate` | Accent，只设置 `--accent` / `--accent-soft` |
| `.status-dot` + `.status-running/stopped/warning/error` | 8px 状态点 |

### 3.4 拓扑与抽屉

| 类名 | 要点 |
| --- | --- |
| `.topology-flow` / `.topology-node` / `.topology-node-{client,gateway,port,target}` | 四段语义节点 |
| `.topology-link` / `.topology-link-flow` / `.topology-link-idle` | 连线与流动光点 |
| `.topology-fallback` | 虚线兜底分支 |
| `.route-drawer` / `.route-drawer-head` / `.route-drawer-body` / `.route-drawer-actions` | 抽屉骨架（420px） |
| `.route-drawer-tabs` / `.route-drawer-tab` | 标签页 |
| `.drawer-section` / `.drawer-section-index` / `.drawer-field` / `.drawer-metric` | ①②③④ 分节 |
| `.network-backdrop` | 背景网络装饰（透明度 `--network-opacity`） |

### 3.5 表单、日志与基础组件

| 类名 | 要点 |
| --- | --- |
| `.console-field` / `.console-field-label` / `.console-field-hint` | 表单字段 |
| `.console-error-box` / `.required-field-mark` | 错误区与必填标记 |
| `.prefix-editor` / `.prefix-chip-remove` | 路径前缀编辑器 |
| `.console-dialog` / `.console-dialog-title` / `.console-overlay` | 对话框 |
| `.log-table` / `.method-badge` / `.status-badge` | 日志表与徽标 |
| `.console-empty` / `.console-skeleton` | 空态与骨架 |

`Button` 变体：`primary` / `default` / `outline` / `ghost` / `danger`；
尺寸：`sm` 32px、`default` 38px、`icon` 32px 方形。
`Badge` 变体：`default` / `primary` / `success` / `warning` / `danger` / `muted`。

---

## 4. 主题与偏好

| 项 | 实现 |
| --- | --- |
| 主题取值 | `ThemeMode = 'dark' \| 'light'`（`lib/preferences.ts`） |
| 视图取值 | `RouteViewMode = 'card' \| 'list'` |
| 存储键 | `wrouter.theme`、`wrouter.routeView` |
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
路由卡片、列表行与 KPI 行都用它，避免逐条拉取日志明细。

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

| 界面状态 | 判定 |
| --- | --- |
| 运行中 | `enabled` 且失败率 ≤ 5% |
| 已停用 | `!enabled` |
| 异常 | `enabled` 且失败率 > 5% |
| 错误 | `enabled` 且失败率 ≥ 20% |

失败率在 `requestsLastMinute > 0` 时用 `failedLastMinute / requestsLastMinute`，否则退回累计口径。

---

## 6. 相关文档

| 文档 | 内容 |
| --- | --- |
| [核心功能](CORE-FEATURES.md) | 后端能力、接口与数据口径的权威说明 |
| [UI 设计系统](UI-DESIGN-SYSTEM.md) | 本文映射的视觉与交互规范 |
| [打包与发布](PACKAGING.md) | 交付形态与自动更新 |

---

## 7. 变更检查表

1. 新颜色 / 圆角 / 阴影是否来自本文与 `styles.css` 令牌，而不是硬编码十六进制。
2. 新增 Tailwind 颜色是否写成 `var(--console-*)`。
3. 新组件是否使用 `.console-*` 语义类名前缀。
4. 是否仍满足设计系统 §10 验收清单（含**深浅色都可读**）。
5. 是否同步更新本文的类名与数据契约表。
6. 是否运行 `npm run typecheck`、`npm test`、`mvn test`。
7. 是否运行 `npm run build` 并提交 `src/main/resources/static/admin/**` 产物。
