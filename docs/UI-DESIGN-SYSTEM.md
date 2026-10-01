# wrouter UI 设计系统

> 管理后台的视觉与交互契约。任何新增页面、组件或主题都要满足本文；与本文冲突的实现视为缺陷。
>
> 适用范围：`frontend/src/**`、`src/main/resources/templates/index.html`、`src/main/resources/static/admin/**`。

---

## 1. 定位

wrouter 是**本地 API 网关 / 反向代理 / 路由管理器**，使用者是开发者与运维。

界面要回答一个问题：

> **看到一条路由，立刻知道它是什么、从哪里进入、转发到哪里、现在是否正常、流量多少、出问题去哪里看。**

信息链固定为：

```text
Route → Gateway → Local Port → Target → Traffic → Logs → Debug
```

### 是 / 不是

| 是 | 不是 |
| --- | --- |
| Developer Network Console | 通用 SaaS Admin |
| 卡片式 / 列表式路由管理 | 默认 Ant Design、shadcn Dashboard |
| 高信息密度技术工具 | 营销落地页 |
| 轻量未来感 | 玻璃拟态展示页 |

### 三条签名特征

1. **Network Line Decoration** — 背景有极淡的网络节点与连线（透明度 0.05 左右）。
2. **Route Accent** — 每条路由有自己的强调色，只作用于图标、Sparkline、状态点、选中边框。
3. **Topology-first Detail** — 路由详情永远先展示 `Client → wrouter → 本地端口 → 目标服务`。

> 品牌识别来自 **Route Card + Network Topology + Route Accent + Technical Data + Inspector Drawer**，
> 不来自渐变、玻璃、发光或大圆角。

---

## 2. 主题：深色 / 浅色

两套主题共用**同一组语义令牌**，通过 `<html data-theme="light|dark">` 切换。

| 规则 | 说明 |
| --- | --- |
| 默认 | 未设置时跟随系统 `prefers-color-scheme` |
| 用户选择 | 显式切换后写入 `localStorage`（键 `wrouter.theme`），此后不再跟随系统 |
| 取值 | 只允许 `light` / `dark` |
| 切换入口 | 顶栏图标按钮（深色时显示太阳，浅色时显示月亮） |
| 原生控件 | 同步 `color-scheme`，让滚动条与表单控件跟随 |

**实现约束**：颜色一律走 CSS 变量；Tailwind 的 `console-*` 调色板也指向同一批变量，
因此 `bg-console-panel` 之类的工具类会自动跟随主题。**禁止在组件里写死十六进制颜色。**

---

## 3. 色彩令牌

### 3.1 基础色

| Token | 深色 | 浅色 | 用途 |
| --- | --- | --- | --- |
| `--console-bg` | `#07111F` | `#F2F6FC` | 应用底色、侧边栏 |
| `--console-bg-alt` | `#091525` | `#FFFFFF` | 输入框、次级面板 |
| `--console-panel` | `#0F1D30` | `#FFFFFF` | 卡片 / KPI / 面板 |
| `--console-panel-hover` | `#12243B` | `#F6F9FE` | 卡片与行 hover |
| `--console-raised` | `#132741` | `#FFFFFF` | Drawer、Dialog |
| `--console-line` | `rgba(120,170,230,.14)` | `rgba(19,47,84,.12)` | 默认边框 |
| `--console-line-strong` | `rgba(120,170,230,.24)` | `rgba(19,47,84,.2)` | 强边框 |
| `--console-ink` | `#F3F7FF` | `#0C1B2E` | 标题与主数据 |
| `--console-ink-muted` | `#A8B8CE` | `#46586E` | 正文、标签 |
| `--console-ink-subtle` | `#71839B` | `#6B7C93` | 元信息 |
| `--console-primary` | `#1683FF` | `#0A6BD8` | 主操作、选中态、焦点环 |
| `--console-success` | `#10D9A0` | `#0E9F76` | 运行中 |
| `--console-warning` | `#F59E0B` | `#B45309` | 异常 |
| `--console-error` | `#EF4444` | `#D92D20` | 错误 / 危险 |

### 3.2 Route Accent

| Token | 深色 | 浅色 |
| --- | --- | --- |
| `--accent-blue` | `#1683FF` | `#0A6BD8` |
| `--accent-cyan` | `#16C7E8` | `#0E7490` |
| `--accent-purple` | `#8B5CF6` | `#6D28D9` |
| `--accent-orange` | `#F59E0B` | `#B45309` |
| `--accent-green` | `#10D9A0` | `#0E9F76` |
| `--accent-slate` | `#64748B` | `#64748B` |

**分配规则**：按路由在列表中的序号取模分配（`routeAccentClass(index)`），
保证同一路由在卡片视图、列表视图与详情抽屉中颜色一致。

**使用范围**：图标底色、Sparkline 描边与渐变、状态点、选中边框。**禁止整卡换色、禁止正文着色。**

### 3.3 状态色

| 状态 | 文案 | 颜色 |
| --- | --- | --- |
| Running | `● 运行中` | `--console-success` |
| Stopped | `● 已停用` | `--console-slate` |
| Warning | `● 异常` | `--console-warning` |
| Error | `● 错误` | `--console-error` |

### 3.4 背景规则

- 禁止纯黑与大面积渐变。
- 主内容区允许一层极淡径向渐变（`--console-glow-1/2`）。
- 网络装饰线透明度不超过 `.08`，不干扰文字。

---

## 4. 排版

| 角色 | 字体 | 字号 / 字重 |
| --- | --- | --- |
| 页面标题 | Inter / PingFang SC | 24px / 600 |
| 区块标题 | Inter / PingFang SC | 15–16px / 600 |
| 正文 | Inter / PingFang SC | 13–14px / 400–500 |
| 元信息 | Inter / PingFang SC | 11–12px |
| 关键数字 | Inter（tabular-nums） | 20–28px / 600 |
| Path / Port / URL / 版本 | JetBrains Mono | 12px |

规则：

- 字体栈 `Inter, "PingFang SC", "Noto Sans SC", "Microsoft YaHei", system-ui, sans-serif`。
- 等宽栈 `"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace`。
- **所有** Path、Port、Target URL、routeId、时间戳、耗时、百分比、版本号用等宽字体。
- 关键数字必须带单位或上下文：`12.4k / min`、`18ms`、`99.8%`。
- 全大写英文只用于 11px 小节标签，字距 `0.14em`。

---

## 5. 布局

```text
┌──────────────┬──────────────────────────────────────────┐
│  Sidebar     │ Top Header 56px                          │
│  208px       ├──────────────────────────────────────────┤
│              │ Main Workspace（可滚动）                  │
│              │  KPI 行 → 工具栏 → 路由视图              │
└──────────────┴──────────────────────────────────────────┘
```

打开详情时：

```text
┌──────────┬───────────────────────────────┬──────────────┐
│ Sidebar  │ 路由卡片（2 列）/ 列表         │ Detail 420px │
└──────────┴───────────────────────────────┴──────────────┘
```

| 元素 | 尺寸 |
| --- | --- |
| Sidebar | 208px（200–216） |
| Top Header | 56px（54–60） |
| Detail Drawer | 420px（400–460，≤ 视口 35%） |
| 内容内边距 / 最大宽度 | 20px / 1536px |
| Card gap | 16px（14–18） |
| Route Card 高度 | 180–220px，≤ 260px |

断点：≥1440 三列；1100–1440 三列 + 覆盖式 Drawer；860–1100 两列；640–860 单列 + 折叠侧边栏；<640 单列。

---

## 6. 组件

### 6.1 Sidebar

```text
WROUTER（Logo + Local Gateway Console）

概览
路由管理            24     ← 数量徽标
日志
运行状态
  ● Gateway
  ● Local Proxy
系统设置
  配置管理 / 导出配置 / 导入配置 / 关于

┌──────────────────────┐   ← 底部 Gateway Runtime Card
│ ● 网关运行中          │
│ 127.0.0.1:9999        │
│ v1.3.0 · macOS · …    │   ← 点击打开版本抽屉
└──────────────────────┘
```

导航项 34px 高、8px 圆角，图标 16px + 文本 13px；当前项用主色淡底 + 左侧 2px 指示条。
分区标签 11px、`letter-spacing:.14em`。运行状态用 8px 状态点（运行中带呼吸动画）。

### 6.2 Top Header

左侧页面标题 + 副标题；右侧依次：

```text
v1.3.0   ● Gateway Running   127.0.0.1:9999   [主题] [搜索] [通知] [设置] [头像]
```

- 高度 56px，底部 1px 分隔，背景 `--console-topbar-bg` + 8px 模糊。
- **版本胶囊**（`.version-pill`）：等宽 `v<版本>`，点击打开版本抽屉；<900px 隐藏。
- **主题切换**（`.icon-button`）：深色显示太阳、浅色显示月亮；必须有 `aria-label`。

### 6.3 KPI 行

固定 5 张：`路由总数`、`运行中`、`已停用`、`请求数 / 分钟`、`平均延迟`。

- 高度 ≤92px，11px 标签 + 24px 等宽数值。
- 允许 26px 迷你趋势线；禁止饼图 / 柱状图 / 圆环 / 巨型百分比。
- 点击前三张切换筛选，当前筛选显示主色边框与 `当前筛选` 标签。

### 6.4 工具栏

```text
[ 搜索路由名称、Path、Target… ] [全部状态▾] [排序▾] [导出] [导入] [卡片|列表] [+ 新增路由]
```

控件高 38–40px、圆角 7px。搜索框左侧 16px 图标，占位文案写明可搜索字段。
**视图切换**（`.view-switch`）为分段控件：两个选项，当前项用面板底 + 阴影；<640px 只显示图标。

### 6.5 路由卡片视图（默认）

```text
┌────────────────────────────────────────────┐
│ ☐ ● 用户服务                       运行中  │
│     命中前缀走代理地址，其余走默认地址      │
│ /api/user   /user   +1                     │
│ Target                 本地端口            │
│ 127.0.0.1:18100        18081               │
│ 42 / min               18ms                │
│ ▁▂▃▂▅▆▇▆▇▇▇▇▇▇                            │
│ 查看 日志 拷贝 访问 停用              🗑    │
└────────────────────────────────────────────┘
```

结构自上而下固定：头部（勾选框 + Accent 图标 + 名称 + 状态）→ 行为摘要 → Path 标签（>3 折叠 `+N`）
→ Target / 本地端口 → 请求数·分钟 / 平均延迟 → Sparkline（28–34px）→ 操作行。

这是 wrouter 的默认形态，规范名 **Card-based Route List**；列表视图只是它的紧凑投影，不是替代品。

| 状态 | 表现 |
| --- | --- |
| 运行中 | 正常对比度 |
| 已停用 | 整卡 `opacity:.62`，Sparkline 静止 |
| 选中 | 边框 `--console-primary` + `--console-shadow-select`，禁止霓虹 |
| Hover | 背景 `--console-panel-hover`，边框 `--console-hover-border` |

**禁止**所有卡片完全同构的模板感；允许在统一栅格内做轻度结构变化。

### 6.6 路由列表视图（可选）

```text
   路由                      PATH          TARGET / 本地端口   请求/MIN  延迟  30 分钟   操作
☐ ● 用户服务   运行中   /api/user /user   127.0.0.1:18100     42      18ms  ▁▂▃▅▇    查看 日志 …
```

- 与卡片视图**共享同一套数据、强调色与操作**，只是把每张卡压成一行并加列头（`.route-list-head`）。
- 列头 10.5px 大写、`--console-ink-subtle`。
- 行 hover / 选中 / 停用降权规则与卡片一致。
- 响应式：<1440 隐藏 Sparkline 列；<1100 隐藏列头并改为两行堆叠；操作按钮在窄屏退化为纯图标（保留 `aria-label`）。
- 两种视图都**不是** `<table>`：用 CSS Grid 保证与卡片同一套视觉语言。

### 6.7 详情抽屉（Topology-first）

宽度 420px，从右侧滑入，结构固定：

```text
用户服务                        ● 运行中      [×]
/api/user  /user

概览   配置   日志   指标
──────────────────────────────────
① 路由拓扑  Client → wrouter → 本地端口 → 目标服务
② 基本信息  路由名称 / Path Prefix / Target URL / 代理地址 / 本地绑定 / 状态 / 访问页
③ 运行状态  请求数 / 成功率 / 平均延迟 + 30 分钟曲线
④ 最近日志  时间 方法 路径 状态 耗时（最多 5 条 + 查看全部）
──────────────────────────────────
[编辑] [访问] [日志]              [删除]
```

- **概览首屏必须是拓扑**，不允许先放表单或 JSON。
- 基本信息值用等宽字体；字段名 11px `--console-ink-subtle`。
- 底部操作栏常驻：`编辑` 主色，`删除` 危险描边。

### 6.8 拓扑

节点为 8px 圆角矩形：图标 + 标签 + 技术元信息。语义固定四段
**Client → wrouter 网关 → 本地端口 → 目标服务**（英文规范写法 `Client → wrouter → Local Port → Target`），连线 1px + 流动光点（1.8s），
未命中分支用虚线并标注「未命中 → 默认地址」。停用路由整链降权、连线停止流动。
<980px 纵向堆叠，箭头旋转 90°。

### 6.9 版本与更新抽屉

结构：① 当前安装（版本号 / 构建时间 / 运行平台 / 安装方式 / 更新通道 / 自动更新）
② 更新检查（[`检查更新`] → 状态文案 → 最新版本 / 资产大小 / 通道 → 下载资产 → 更新说明
→ [`一键更新`] [`查看 Release 说明`]）。

- 版本号、构建时间、资产名一律等宽；版本号带 `v` 前缀。
- 打开时自动静默检查一次；检查中图标旋转。
- 「一键更新」仅在「有更新 + 有匹配资产 + 当前安装支持自更新」时可用，否则禁用并在下方说明原因（不弹错误 toast）。
- 平台与安装方式用中文文案：`macOS · Apple Silicon`、`免安装程序`、`JAR 服务`。

### 6.10 表单与对话框

Dialog 表面 `--console-raised`，圆角 12px，遮罩 `--console-scrim` + 模糊。
输入框高 38px、圆角 7px，focus 时主色边框 + `--console-focus-ring` 光环。
必填标记用 `--console-error`；错误区用 `--console-error-box-*`。删除必须二次确认。

### 6.11 日志视图

表头 `--console-bg-alt`；方法徽标 GET 青 / POST 蓝 / PUT 橙 / DELETE 红；
状态码 2xx 绿、3xx 青、4xx 橙、5xx 红（颜色走 `--console-*-ink/-bg/-border` 令牌，深浅色各自可读）。
路径、时间、耗时用等宽；时间只显示 `HH:mm:ss`。

### 6.12 空态与加载态

空态：居中图标 + 15px 标题 + 13px 说明 + 主操作。加载态：骨架屏（`--console-skeleton-sheen`），不用整屏 spinner。

---

## 7. 动效

| 场景 | 时长 | 缓动 |
| --- | --- | --- |
| hover / 边框变化 | 160ms | `ease-out` |
| Drawer 滑入 | 220ms | `cubic-bezier(.22,.61,.36,1)` |
| 状态点呼吸 | 1.6s 循环 | `ease-in-out` |
| 拓扑流动 | 1.8s 循环 | `linear` |
| Sparkline 更新 | 300ms | `ease-out` |

**禁止**大幅缩放、旋转、粒子、3D、彩虹渐变、闪烁。
**必须**在 `prefers-reduced-motion: reduce` 下关闭全部循环动画。

---

## 8. 品牌符号

Logo 由节点与连线构成，禁止盾牌 / 云 / 服务器 / 齿轮作为核心图形：

```text
●────●
 \  /
  ●
```

背景允许出现极淡节点连线（透明度 ≤.08），不抢内容焦点。

---

## 9. 无障碍

- 正文对比度 ≥ 4.5:1；`--console-ink-subtle` 只用于非关键元信息且 ≥11px。
- 全部交互元素键盘可达，焦点环 `0 0 0 2px var(--console-primary)`。
- 图标按钮必须有 `aria-label`；状态除颜色外必须有文字。
- 图表提供文本替代（如 `aria-label="最近 30 分钟请求数"`）。
- 两套主题都必须满足上述对比度要求。

---

## 10. 相关文档

| 文档 | 内容 |
| --- | --- |
| [核心功能](CORE-FEATURES.md) | 路由配置、动态转发、本地端口代理、请求观测、管理 API |
| [UI 实现映射](UI-IMPLEMENTATION.md) | 本文规范到文件、类名与数据契约的映射 |
| [打包与发布](PACKAGING.md) | 交付形态与自动更新 |

---

## 11. 验收清单

结构类：

- [ ] 仍是 **Card-based Route List**（卡片式路由列表）且为默认视图
- [ ] 列表视图是显式可选，**没有被改成 Table**：两种视图都用 CSS Grid，不出现 `<table>`
- [ ] 有明显且一致的 wrouter 品牌特征（Network Node + Route Line）
- [ ] 存在 Network / Route 视觉元素
- [ ] 存在 Gateway Runtime 信息

路由卡片类：

- [ ] Route Card 包含 Path
- [ ] Route Card 包含 Target
- [ ] Route Card 包含 Local Port
- [ ] Route Card 包含 Status
- [ ] Route Card 包含 Traffic / Latency
- [ ] 存在 Sparkline

详情抽屉类：

- [ ] 存在 Route Detail Drawer
- [ ] Drawer 首屏是 Route Topology，不允许先放表单或 JSON

主题与克制类：

- [ ] **深色 / 浅色都可切换且都可读**，默认跟随系统
- [ ] 颜色全部走 CSS 变量，没有硬编码十六进制
- [ ] 没有过度模板化
- [ ] 没有过度玻璃拟态
- [ ] 没有过多渐变
- [ ] 没有过多圆角
- [ ] 适合开发者长期使用

任意一项不满足，必须返工。
