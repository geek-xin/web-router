# wrouter UI 设计系统

> 管理后台的视觉与交互契约。任何新增页面、组件或主题都要满足本文；与本文冲突的实现视为缺陷。
>
> 适用范围：`frontend/src/**`、`src/main/resources/templates/index.html`、`src/main/resources/static/admin/**`。
>
> 本文是**唯一权威**的视觉规范；[UI 实现映射](UI-IMPLEMENTATION.md) 记录规范到文件、类名与数据契约的对应关系，
> `src/test/java/com/geek/webrouter/AdminUiContractTest.java` 把其中关键条目固化为断言。

---

## 1. 定位

wrouter 是**本地 API 网关 / 反向代理 / 路由管理器**，使用者是开发者与运维。

界面要回答一个问题：

> **看到一条路由，立刻知道它是什么、从哪里进入、转发到哪里、现在是否正常、流量多少、出问题去哪里看。**

信息链固定为：

`@text
Route → Gateway → Local Port → Target → Traffic → Logs → Debug
`@

### 1.1 视觉方向

取自「暖纸画布 + 白色操作外壳」的桌面控制台：外层是米灰纸感画布，内层是一整块白色圆角外壳，
侧栏用略深一档的暖灰区分层级，内容区靠**排版层级与留白**而不是边框和阴影来分组。
标题使用衬线体，数据与路径一律等宽体。**唯一的高饱和区域留给概览带**，
其余界面保持低饱和的墨色与暖灰。

### 1.2 是 / 不是

| 是 | 不是 |
| --- | --- |
| 暖纸画布上的白色控制台 | 深色霓虹终端 |
| 衬线标题 + 等宽数据 | 全站无衬线粗体标题 |
| 卡片式 / 列表式路由管理 | 默认 Ant Design、shadcn Dashboard |
| 高信息密度技术工具 | 营销落地页 |
| 单处克制的蓝色渐变带 | 满屏渐变、玻璃拟态 |

### 1.3 四条签名特征

1. **Paper Shell** — 米灰画布上浮一整块白色圆角外壳（`.app-shell`），侧栏为暖灰 `--surface-sunken`。
   这是全站**唯一**的分层手段：不使用背景网络装饰线、不使用玻璃模糊。
2. **Editorial Heading** — 页面标题、抽屉标题、区块标题用衬线体 Newsreader；
   正文、标签与技术数据一律不用衬线，保证小字号可读。
3. **Single Gradient Band** — 概览带（`.band`）是唯一允许出现装饰性渐变的位置：
   左侧白色主数字卡 + 右侧蓝色柱状带。
4. **Topology-first Detail** — 路由详情抽屉首屏永远是 `Client → wrouter → Local Port → Target` 拓扑。

> 品牌识别来自 **Paper Shell + Editorial Heading + Single Gradient Band + Route Card + Topology-first Detail**，
> 不来自发光、玻璃、大圆角或多色渐变。

---

## 2. 主题

浅色是默认主题，深色是同一套语义令牌的变体，通过 `<html data-theme="light|dark">` 切换。

| 规则 | 说明 |
| --- | --- |
| 默认 | 未设置时跟随系统 `prefers-color-scheme` |
| 用户选择 | 显式切换后写入 `localStorage`（键 `wrouter.theme`），此后不再跟随系统 |
| 取值 | 只允许 `light` / `dark` |
| 切换入口 | 顶栏图标按钮（深色时显示太阳，浅色时显示月亮），必须有 `aria-label` |
| 原生控件 | 同步 `color-scheme`，让滚动条与表单控件跟随 |
| 未覆盖项 | 圆角、尺寸、字体三组令牌不随主题变化，深色块里不重复声明 |

**实现约束**：

- 颜色一律走 CSS 变量；Tailwind 的 `surface-*` 调色板也指向同一批变量，
  因此 `bg-surface-panel` 之类的工具类会自动跟随主题。
- **禁止在组件里写死十六进制颜色**（`favicon.svg` 与品牌 SVG 的 `var()` 例外）。
- 深浅两套都必须满足 §9 的对比度要求；只调浅色不调深色视为未完成。

---

## 3. 色彩令牌

以下为 `frontend/src/styles.css` 中的**完整**令牌表（52 项），深色列标注「继承」表示不随主题变化。

### 3.1 画布与表面

| Token | 浅色（默认） | 深色 | 用途 |
| --- | --- | --- | --- |
| `--surface-canvas` | `#F1F0EC` | `#14161A` | 最外层画布 |
| `--surface-shell` | `#FFFFFF` | `#1B1E24` | 白色操作外壳 |
| `--surface-sunken` | `#F4F3EF` | `#171A1F` | 侧栏、输入底、表头、指标卡 |
| `--surface-panel` | `#FFFFFF` | `#1F232A` | 卡片 / 面板 / 控件底 |
| `--surface-panel-hover` | `#FAFAF8` | `#252A32` | 卡片与行 hover |
| `--surface-raised` | `#FFFFFF` | `#23272F` | Drawer、Dialog、Toast |
| `--surface-fallback-bg` | `#F7F6F2` | `#1C2026` | 拓扑未命中分支底 |

### 3.2 描边

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--surface-line` | `#E7E4DC` | `#2E333C` | 默认边框 |
| `--surface-line-strong` | `#D8D4C9` | `#3C424D` | 强边框 / 分隔 / 悬停边框 |
| `--surface-line-faint` | `#EFEDE7` | `#272C34` | 行内细线、卡片操作区分隔 |

### 3.3 墨色

| Token | 浅色 | 深色 | 对比度（浅色） | 用途 |
| --- | --- | --- | --- | --- |
| `--surface-ink` | `#101F33` | `#F2F4F7` | 16.6:1 | 标题与主数据 |
| `--surface-ink-muted` | `#4E5C6E` | `#A8B0BC` | 6.8:1 | 正文、标签、副标题 |
| `--surface-ink-subtle` | `#667080` | `#828B99` | 5.0:1 | 元信息、字段名、刻度 |

> `--surface-ink-subtle` 必须 ≥4.5:1（正文级）。历史上用过 `#8A93A0`（仅 3.1:1）与 `#78818F`（深色 4.0:1），
> 均不达标，已修正。改这个值前先按 §9 复算。

### 3.4 强调与状态

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--surface-accent` | `#2F6FED` | `#6B9BFF` | 焦点环、选中、链接、图表高亮 |
| `--surface-accent-strong` | `#1B4FD0` | `#8FB4FF` | 强调文字（环比徽标） |
| `--surface-accent-soft` | `#E8F0FE` | `rgba(107,155,255,.16)` | 图标底、选中底 |
| `--surface-accent-border` | `rgba(47,111,237,.42)` | `rgba(107,155,255,.45)` | 拓扑网关节点边框 |
| `--surface-on-accent` | `#FFFFFF` | `#0E1013` | 深色主按钮上的文字 |
| `--surface-navy` | `#101F33` | `#E9EEF7` | 主按钮底、品牌标记底 |
| `--surface-success` | `#1E9E6A` | `#3FCB92` | 运行中 |
| `--surface-warning` | `#C2760A` | `#E3A13C` | 异常、检查中 |
| `--surface-error` | `#C0392B` | `#F1705F` | 错误 / 危险 / 必填标记 |

### 3.5 概览带（唯一渐变区）

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--band-grad-a` | `#F7F9FE` | `#1C222C` | 渐变起点（左） |
| `--band-grad-b` | `#DCEAFF` | `#22304A` | 渐变中点 |
| `--band-grad-c` | `#8FC1FF` | `#2E5288` | 渐变终点（右，饱和蓝） |
| `--band-bar` | `#A8CEFB` | `#33507F` | 柱状图普通柱 |
| `--band-bar-hot` | `#FFFFFF` | `#BFD6FF` | 柱状图高亮柱 |
| `--band-ink` | `#101F33` | `#F2F4F7` | 渐变上的主数字 |
| `--band-ink-muted` | `#2B3D52` | `#C3CCD8` | 渐变上的说明与刻度 |

> **为什么需要 `--band-ink*`**：概览带右端是饱和蓝（`#8FC1FF`），
> `--surface-ink-subtle` 在其上只有 2.7:1。渐变上的文字必须用专用墨色，
> 不能复用页面墨色令牌。

### 3.6 交互、浮层与滚动

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--surface-focus-ring` | `rgba(47,111,237,.22)` | `rgba(107,155,255,.28)` | 输入框 focus 光环 |
| `--surface-scrim` | `rgba(23,26,32,.34)` | `rgba(6,8,11,.6)` | 抽屉遮罩 |
| `--surface-scrim-strong` | `rgba(23,26,32,.46)` | `rgba(6,8,11,.7)` | 对话框遮罩 |
| `--surface-selection` | `rgba(47,111,237,.18)` | `rgba(107,155,255,.26)` | 文本选中 |
| `--surface-scrollbar` | `#D5D1C6` | `#3A404A` | 滚动条滑块 |
| `--surface-scrollbar-hover` | `#C2BDB0` | `#4A515D` | 滚动条 hover |
| `--surface-skeleton` | `#EDEBE4` | `#262B33` | 骨架底色 |
| `--surface-skeleton-sheen` | `rgba(255,255,255,.7)` | `rgba(255,255,255,.06)` | 骨架微光 |

### 3.7 阴影（只用四级）

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--shadow-hairline` | `0 1px 1px rgba(28,32,40,.03)` | `0 1px 1px rgba(0,0,0,.3)` | 面板、卡片默认 |
| `--shadow-card` | `0 1px 2px rgba(28,32,40,.05)` | `0 1px 2px rgba(0,0,0,.34)` | 白色外壳、概览主卡 |
| `--shadow-card-hover` | `0 4px 14px rgba(28,32,40,.08)` | `0 4px 16px rgba(0,0,0,.42)` | 卡片 hover |
| `--shadow-float` | `0 12px 32px rgba(28,32,40,.12)` | `0 14px 36px rgba(0,0,0,.5)` | Drawer、Dialog、Toast |
| `--shadow-select` | `0 0 0 1px rgba(47,111,237,.24), 0 6px 18px rgba(47,111,237,.1)` | `0 0 0 1px rgba(107,155,255,.3), 0 6px 18px rgba(0,0,0,.4)` | 卡片选中光环 |

禁止使用五级以上阴影；禁止彩色阴影（`--shadow-select` 的蓝色描边除外）。

### 3.8 圆角与尺寸

| Token | 值 | 用途 |
| --- | --- | --- |
| `--radius-control` | `10px` | 按钮、输入框、下拉、分段控件、导航项 |
| `--radius-node` | `10px` | 拓扑节点 |
| `--radius-card` | `14px` | 路由卡片、指标卡、日志行容器 |
| `--radius-panel` | `18px` | 抽屉、对话框、面板 |
| `--radius-band` | `20px` | 白色外壳、概览带 |
| `--radius-pill` | `999px` | 胶囊：搜索框、状态胶囊、版本胶囊、徽标 |
| `--sidebar-width` | `236px` | 侧边栏宽度（收起态见 §6.2） |

禁止在普通组件上使用超过 20px 的圆角（胶囊除外）。

### 3.9 Route Accent

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--accent`（blue） | `#2F6FED` | `#6B9BFF` |
| cyan | `#0E8FA8` | `#35BBD4` |
| purple | `#7A5AF0` | `#A78BFA` |
| orange | `#D98324` | `#E9A852` |
| green | `#1E9E6A` | `#3FCB92` |
| slate | `#64748B` | `#94A3B8` |

**分配规则**：按路由在列表中的序号取模分配（`routeAccentClass(index)`），
保证同一路由在卡片视图、列表视图与详情抽屉中颜色一致。

**使用范围**：图标底色、Sparkline 描边与渐变、状态点。**禁止整卡换色、禁止正文着色。**

---

## 4. 排版

### 4.1 字体栈

| 角色 | 令牌 | 栈 |
| --- | --- | --- |
| 正文 | `--font-sans` | `Inter, "PingFang SC", "Noto Sans SC", "Microsoft YaHei", ui-sans-serif, system-ui, sans-serif` |
| 标题 | `--font-display` | `Newsreader, "Iowan Old Style", "Palatino Linotype", Palatino, "Source Han Serif SC", "Noto Serif SC", Georgia, serif` |
| 技术数据 | `--font-mono` | `"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace` |

三套字体由 `styles.css` 顶部的一次 `fonts.googleapis.com` 请求加载（Newsreader 只取 400/500）。

### 4.2 字号与字重

| 角色 | 字体 | 字号 / 字重 | 行高 |
| --- | --- | --- | --- |
| 页面标题 | Newsreader | 32px / 400 | 1.15 |
| 抽屉标题 | Newsreader | 23px / 400 | 1.2 |
| 区块标题 | Newsreader | 21px / 400 | — |
| 弹窗标题 | Newsreader | 22px / 400 | — |
| 卡片标题 | Inter | 14.5px / 600 | — |
| 卡片摘要 / 元信息 | Inter | 11–12.5px / 400–500 | 1.4 |
| 字段名 | Inter | 10.5px / 500 | — |
| 关键数字 | JetBrains Mono | 17–46px / 500–600 | 1–1.1 |
| Path / Port / URL / 版本 / 耗时 | JetBrains Mono | 11–12.5px / 400–500 | — |

规则：

- **所有** Path、Port、Target URL、routeId、时间戳、耗时、百分比、版本号用等宽字体。
- 等宽数字必须带 `font-variant-numeric: tabular-nums`（`.numeric` / `.font-mono` 已内置），保证列对齐。
- 衬线体只用于**标题**；正文、标签与数据一律不用衬线。
- 关键数字必须带单位或上下文：`42 / min`、`18ms`、`99.8%`、`1,840`。
- 行宽控制在 80 字符内；空态说明限制 `max-width: 42ch`。

---

## 5. 布局

### 5.1 主骨架

`@text
┌───────────────────────────────────────────────────────────┐
│ 画布 .app-canvas（--surface-canvas，26px 内边距）           │
│  ┌──────────────┬───────────────────────────────────────┐  │
│  │ .app-sidebar │ .app-topbar（衬线标题 + 搜索 + 状态）  │  │
│  │ 236px        ├───────────────────────────────────────┤  │
│  │ 暖灰底        │ .app-content（可滚动）                 │  │
│  │              │  .band 概览带（唯一渐变区域）           │  │
│  │              │  .surface-panel 路由面板               │  │
│  └──────────────┴───────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
`@

### 5.2 打开详情时

`@text
┌──────────┬───────────────────────────────┬──────────────┐
│ Sidebar  │ 路由卡片（3 列）                │ .drawer 468px│
└──────────┴───────────────────────────────┴──────────────┘
`@

### 5.3 尺寸表

| 元素 | 尺寸 |
| --- | --- |
| 画布内边距 | 26px（≤980px 时 14px） |
| 外壳圆角 `--radius-band` | 20px |
| Sidebar `--sidebar-width` | 236px |
| 概览带 | 双列等宽，左白色主卡 + 右柱状带，内边距 16px，卡间距 18px |
| 内容最大宽度 | 1560px |
| Detail Drawer | 468px（≤ 视口 94%） |
| Dialog 默认宽度 | `min(94vw, 780px)` |
| Card gap | 14px |
| Route Card 高度 | 190–220px |
| 控件高度 | 38px（`.btn-sm` 32px） |

### 5.4 响应式断点

| 断点 | 变化 |
| --- | --- |
| ≥1720px | 路由卡片 4 列 |
| 1440–1720px | 路由卡片 3 列 |
| ≤1439px | 列表视图隐藏 Sparkline 列 |
| ≤1320px | 顶栏状态胶囊隐藏地址 |
| ≤1100px | 概览带单列；列表视图折叠为三行堆叠并显示行内标签；日志 5 项指标退化为 3 列 |
| ≤980px | 侧栏转为顶部横向导航；画布内边距 14px |
| ≤760px | 表单与字段网格单列；隐藏版本胶囊、头像、状态地址；分段控件只留图标 |

**唯一权威实现**是 `frontend/src/styles.css` 底部的 `@media` 块；上表用于理解意图，改断点必须同步本节。

### 5.5 滚动约定（页面不滚，列表内滚）

**首页整体不出现滚动条**，滚动只发生在明确指定的区域内部。

@@@text
┌─ .app-canvas   height:100dvh  overflow:hidden ───────────────┐
│ ┌─ .app-shell  height:calc(100dvh - 52px) ────────────────┐  │
│ │ sidebar │ .app-topbar        固定                       │  │
│ │ 固定    │ .band              固定（flex:none）           │  │
│ │         │ ┌─ .surface-panel ────────────────────────┐   │  │
│ │         │ │ .toolbar                   固定          │   │  │
│ │         │ │ ┌─ .panel-scroll  ← 唯一滚动区 ───────┐ │   │  │
│ │         │ │ │ 路由卡片 / 日志表格                  │ │   │  │
│ │         │ │ └─────────────────────────────────────┘ │   │  │
│ │         │ │ .panel-footnote            固定          │   │  │
│ │         │ └─────────────────────────────────────────┘   │  │
│ └─────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
@@@

| 元素 | 滚动行为 |
| --- | --- |
| `.app-canvas` | `height: 100dvh` + `overflow: hidden`，页面级不滚动 |
| `.app-shell` | `height: calc(100dvh - 52px)`（52px = 上下画布内边距） |
| `.app-content` | `display:flex; flex-direction:column; overflow:hidden` |
| `.app-content > .surface-panel` | `flex:1; min-height:0`，把剩余高度交给内部 |
| `.panel-scroll` | **唯一允许滚动的区域**：`flex:1; min-height:0; overflow-y:auto` + `overscroll-behavior:contain` |
| `.panel-footnote` / `.toolbar` / `.band` | `flex:none`，常驻不随列表滚动 |
| 侧栏 `.app-sidebar` | 自身可滚（`overflow-y:auto`），与内容区互不影响 |

**为什么用 `100dvh`**：移动端浏览器地址栏会动态改变视口高度，`100vh` 会导致底部被裁切。
两个属性成对声明（`100vh` 兜底 + `100dvh` 覆盖），不支持 `dvh` 的浏览器自动退回 `vh`。

**矮视口自适应**：当视口高度不足时，按 `max-height` 分档压缩概览带（柱子高度、主数字字号、内边距），
把高度让给列表区，保证至少一张路由卡片完整可见：

| 档位 | 压缩内容 |
| --- | --- |
| `max-height: 820px` | 概览带内边距 12px、主数字 34px、柱子 58px、标题 27px |
| `max-height: 800px` | 柱子 36px、主数字 30px、图表数字 20px、隐藏副标题 |

> 新增任何页面级区块时，必须放进 `.app-content` 的 flex 列里并声明 `flex: none`，
> 否则会把 `.panel-scroll` 挤到不可用。**不要给 `.app-content` 加回 `overflow-y: auto`**。

---

## 6. 组件

类名与 `frontend/src/styles.css` 一一对应。**加粗**为签名组件。

### 6.1 应用骨架

| 类名 | 要点 |
| --- | --- |
| `.app-canvas` | 米灰画布，26px 内边距 |
| `.app-shell` | `grid-template-columns: var(--sidebar-width) minmax(0,1fr)`，20px 圆角，`--shadow-card`；列宽带 200ms 过渡 |
| `.app-shell-collapsed` | 收起态：把 `--sidebar-width` 覆盖为 76px |
| `.app-main` / `.app-content` | 主列 / 内容区（`min-width: 0`，可滚动） |

### 6.2 侧边栏 `.app-sidebar`

`@text
[wrouter 标记]  wrouter
                Gateway Console

路由管理            24     ← .nav-count 数量徽标
请求日志

运行状态
  ● Gateway
  ● Local Proxy

系统设置
  配置目录 / 导出配置 / 导入配置 / 关于

        ⋮  .side-spacer 把底部区推到底
┌──────────────────────┐   ← .status-panel 底部运行卡
│ ● 网关运行中          │
│ 127.0.0.1:9999        │
│ v1.3.0 · macOS · …    │   ← .status-panel-meta，点击开版本抽屉
└──────────────────────┘
──────────────────────────   ← .sidebar-footer 上分隔线
[⇤ 收起侧边栏]               ← .sidebar-toggle 固定在侧栏最下方
`@

| 类名 | 要点 |
| --- | --- |
| `.brand` / `.brand-mark` / `.brand-name` / `.brand-sub` | 品牌区；≤980px 隐藏 `.brand-sub` |
| `.sidebar-footer` | 侧栏底部区（运行卡之下），承载收起按钮；有 1px 上分隔线 |
| `.sidebar-toggle` | 收起 / 展开按钮，**固定在侧栏最下方**；展开态显示文案，收起态只剩图标并居中 |
| `.sidebar-toggle-label` | 按钮文案；窄轨与 ≤980px 下隐藏 |
| `.side-section` / `.side-label` | 分组；`.side-section-status` 在 ≤980px 隐藏（顶栏已表达网关状态） |
| `.nav-item` / `.nav-item-active` | 38px 高、10px 圆角；当前项**白底 + `--shadow-hairline`**，不用彩色底也不用左侧指示条 |
| `.nav-icon` / `.nav-text` / `.nav-count` | 16px 图标 / 省略号文本 / 等宽计数徽标 |
| `.side-spacer` | 把运行卡推到最底 |
| `.status-panel` / `.status-panel-head` / `.status-panel-title` / `.status-panel-address` / `.status-panel-meta` / `.status-panel-paths` | 运行卡各部件；`.status-panel-paths` 展示配置目录，可换行 |

#### 收起态（icon rail，**默认**）

**侧边栏默认收起。** 首次访问（`localStorage` 无 `wrouter.sidebar`）时以 76px 窄轨启动，
把宽度让给路由列表；用户手动展开后写入 `localStorage`，此后以存储值为准。

点击侧栏**最下方**的 `.sidebar-toggle` 在展开（236px）与收起（76px）之间切换，选择写入
`localStorage`（键 `wrouter.sidebar`，取值 `expanded` / `collapsed`），刷新后保持。

| 项 | 实现 |
| --- | --- |
| 默认值 | `DEFAULT_SIDEBAR_MODE = 'collapsed'`（`lib/preferences.ts`） |
| 取值解析 | `resolveSidebarMode(stored)`：有存储用存储，无存储用默认值 |
| 首访行为 | 不写 `localStorage`，只以收起态渲染；用户首次点击切换才落盘 |

| 收起后 | 表现 |
| --- | --- |
| 侧栏宽度 | 76px（`.app-shell-collapsed { --sidebar-width: 76px }`） |
| 品牌行 | 竖排：标记在上、切换按钮在下 |
| 隐藏 | `.brand-text`、`.side-label`、`.nav-text`、`.nav-count`、`.status-panel-title`、`.status-panel-address`、`.status-panel-meta`、`.status-panel-paths`、`.side-section-status` |
| 保留 | 全部图标按钮（`.nav-item` 居中）、运行卡状态点 |
| 可达性 | 收起时每个导航项都必须带 `title`，切换按钮带 `aria-label` + `aria-expanded` |
| ≤980px | 切换按钮隐藏，始终按展开态渲染（顶部横向导航不需要收起） |

> 收起态**不隐藏功能**：所有操作仍可点击，只是文案让位给图标。
> 因此新增侧栏项时必须同时提供 `title`（收起态）与 `.nav-text`（展开态）。

### 6.3 状态点 `.dot`

7px 圆点，四种状态类，**除颜色外必须有文字**（`routeStatusText()`）：

| 类名 | 语义 | 颜色令牌 |
| --- | --- | --- |
| `.state-running` | 运行中 | `--surface-success` |
| `.state-stopped` | 已停用 | `--surface-ink-subtle` |
| `.state-warning` | 异常 | `--surface-warning` |
| `.state-error` | 错误 | `--surface-error` |
| `.state-checking` | 检查中 | `--surface-warning` + 1.6s 呼吸 |

> ⚠️ **命名空间陷阱**：状态点用 `state-*`，日志表 HTTP 状态码徽标用 `status-*`（§6.11）。
> `routeStatusDotClass()` 必须返回 `state-*`；返回 `status-*` 会让状态点全部退化为灰色。

### 6.4 顶栏 `.app-topbar`

`@text
路由控制台                          [🔍 搜索…  ⌘K] ● Gateway 运行中 127.0.0.1:9999 v1.3.0 [主题][通知][WR]
管理本地路由、代理端口和请求转发
`@

| 类名 | 要点 |
| --- | --- |
| `.topbar-heading` / `.topbar-title` / `.topbar-subtitle` | 标题块；标题用衬线 32px，`white-space: nowrap` |
| `.topbar-actions` | `flex: 1 1 auto` + `flex-wrap`；空间不足时整块换行，**不允许挤压标题** |
| `.topbar-search` | 胶囊搜索框，`flex: 0 1 240px`，`min-width: 160px` |
| `.topbar-search-icon` / `topbar-search-key` | 左侧图标 / 右侧 `⌘K` 提示 |
| `.state-pill` / `.state-pill-address` | 网关状态胶囊；地址在 ≤1320px 隐藏 |
| `.version-pill` | 等宽 `v<版本>`，点击开版本抽屉；≤760px 隐藏 |
| `.icon-button` / `.icon-button-dot` | 38px 圆形图标按钮，必须有 `aria-label`；`-dot` 带红点 |
| `.topbar-avatar` | `WR` 等宽字标；≤760px 隐藏 |

### 6.5 概览带 `.band`（签名组件）

固定双列：左侧 `.band-lead` 白色主数字卡，右侧 `.band-chart` 30 分钟柱状带。

`@text
┌──────────────────────────────┬───────────────────────────────┐
│ [图标] 本分钟请求   [每5秒刷新]│ 最近 30 分钟请求               │
│ 1,284                        │ 18,420                        │
│ [↑12.4%] 相比上一分钟         │ 峰值 42 / 分钟 · 平均延迟 18ms │
│ ─────────────────────────    │                               │
│ ● 路由总数  ● 运行中  ● 已停用│  ▁▂▃▂▅▆▇▆▇▇▇█                 │
│ 24 条       21 条     3 条    │  -29m        -15m        现在  │
└──────────────────────────────┴───────────────────────────────┘
`@

| 类名 | 要点 |
| --- | --- |
| `.band` | `linear-gradient(105deg, grad-a, grad-b 46%, grad-c)`，20px 圆角 |
| `.band-lead` | 白底 14px 圆角 + `--shadow-card` |
| `.band-lead-head` / `.band-lead-icon` / `.band-lead-title` | 卡头与图标底 |
| `.band-lead-figure` | 46px 等宽主数字 |
| `.band-lead-delta` / `.band-delta-chip` | 环比行与徽标；无数据时显示「上一分钟没有请求，暂无可比数据」 |
| `.band-lead-split` / `.band-split-item` / `.band-split-label` / `.band-split-value` / `.band-split-figure` / `.band-split-unit` | 三个可点筛选项，`aria-pressed` 表达当前筛选 |
| `.band-chart-head` / `.band-chart-label` / `.band-chart-figure` / `.band-chart-note` | 右列文案，全部用 `--band-ink*` |
| `.band-bars` / `.band-bar` / `.band-bar-hot` | 30 根柱子；有数据最低 6%，无数据 4%；末根默认高亮，hover 跟随鼠标 |
| `.band-bars-empty` | 无任何流量时整条基线降权，避免误导为「有数据」 |
| `.band-lead-figure-row` | 主数字与环比的横向基线对齐行 |
| `.band-toggle` | 收起 / 展开按钮，常驻卡头右侧；带 `aria-expanded` + `aria-controls` |
| `.band-collapsed` / `.band-summary` / `.band-summary-icon` / `.band-summary-figure` / `.band-summary-unit` / `.band-summary-sep` / `.band-summary-text` / `.band-summary-stats` / `.band-summary-stat` | 收起态的一行摘要（见下方「收起态」） |
| `.band-bar-ticks` / `.band-bar-tick` | 三个刻度：`-29m` / `-15m` / `现在`（不是 30 个，避免截断） |

禁止饼图 / 圆环 / 面积图 / 巨型百分比。柱状图必须带 `role="img"` 与描述性 `aria-label`。

#### 收起态（一行摘要）

概览带右上角 \`.band-toggle\` 在展开与收起之间切换；收起后整条带压成 **46px 单行摘要**，
把高度让给路由列表（实测 1512×739 下：列表区 298px → 402px，正好容纳一张完整卡片）。

`@text
┌──────────────────────────────────────────────────────────────────────┐
│ [◔] 0 次 / 分钟 │ 30 分钟 0 次 · 峰值 0 / 分钟 · 平均延迟 0ms   ● 路由 1  ● 运行 1  ● 停用 0   ⌄ │
└──────────────────────────────────────────────────────────────────────┘
`@

| 收起后 | 表现 |
| --- | --- |
| 容器 | \`.band-collapsed\`：\`display:block\`，白底 + 1px 描边，不再使用渐变 |
| 摘要行 | \`.band-summary\`：46px 高，主数字 + 一段说明 + 三个筛选按钮 + 切换按钮 |
| 主数字 | \`.band-summary-figure\` 19px 等宽；单位 \`.band-summary-unit\` |
| 说明 | \`.band-summary-text\`：30 分钟总量 / 峰值 / 平均延迟，超长省略 |
| 筛选 | \`.band-summary-stat\`：与展开态的 \`.band-split-item\` **功能等价**，\`aria-pressed\` 表达当前筛选 |
| 保留 | 全部关键指标都可读，**不因收起而丢失信息**，只是密度更高 |
| 可达性 | \`.band-toggle\` 带 \`aria-label\` + \`aria-expanded\` + \`aria-controls\` |
| 持久化 | 写入 \`localStorage\`（键 \`wrouter.band\`，取值 \`expanded\` / \`collapsed\`） |

**默认展开**（\`DEFAULT_BAND_MODE = 'expanded'\`）：保持既有视觉不变，收起是用户在矮视口下的主动选择。

> 与侧边栏收起不同：侧边栏默认收起，概览带默认展开。
> 两者的共同约定是**收起只压缩呈现密度，不隐藏功能**——所有可点元素在收起态仍可点，且必须有 \`title\` 或可见文案兜底。

### 6.6 面板与工具栏

| 类名 | 要点 |
| --- | --- |
| `.surface-panel` | 18px 圆角面板，`--shadow-hairline` |
| `.toolbar` / `.toolbar-heading` / `.toolbar-title` / `.toolbar-note` | 工具栏；标题用衬线 21px，说明写「共 N 条，当前显示 M 条」 |
| `.search-field` / `.search-field-icon` | 面板内搜索框（仅日志视图使用；路由搜索只在顶栏） |
| `.segmented` / `.segmented-option` / `.segmented-option-active` | 卡片·列表分段控件；≤760px 只显示图标 |

### 6.7 路由卡片视图 `.route-card`（默认，签名组件）

`@text
┌────────────────────────────────────────────┐
│ ☐ [◆] 用户服务                      ● 运行中 │
│       命中前缀走代理地址，其余走默认地址      │
│ /api/user   /user   +1                     │
│ Target                 本地端口             │
│ 127.0.0.1:18100        18081               │
│ 42 / min               18ms                │
│ 请求数                 平均延迟             │
│ ▁▂▃▂▅▆▇▆▇▇▇▇▇▇                             │
│ 查看 日志 拷贝 访问 停用                🗑   │
└────────────────────────────────────────────┘
`@

结构自上而下**固定**：头部（勾选框 + Accent 图标 + 名称 + 状态）→ 行为摘要 → Path 标签（>3 折叠 `+N`）
→ Target / 本地端口 → 请求数·分钟 / 平均延迟 → Sparkline（34px）→ 操作行。

| 类名 | 要点 |
| --- | --- |
| `.route-card-board` | 1 / 2（≥900px）/ 3（≥1440px）/ 4（≥1720px）列网格，gap 14px |
| `.route-card` / `.route-card-selected` / `.route-card-muted` | 卡片主体；选中用 `--surface-accent` 边框 + `--shadow-select`；停用 `opacity:.62` |
| `.route-card-head` / `.route-card-icon` / `.route-card-title` / `.route-card-name` / `.route-card-desc` / `.route-card-status` | 头部各部件 |
| `.route-card-path` / `.route-prefix-chip` / `.route-prefix-chip-more` | Path 标签；无前缀时显示「全部兜底」 |
| `.route-card-meta` / `.route-field-label` / `.route-field-value` | 字段网格；标签 10.5px，值等宽 |
| `.route-card-metrics` / `.route-metric` / `.route-metric-figure` / `.route-metric-value` / `.route-metric-unit` / `.route-metric-caption` | 指标；**数值在上、口径在下**，保证「—」也能读懂 |
| `.route-card-spark` | Sparkline 容器，34px |
| `.route-card-actions` / `.route-card-action` / `.route-card-action-label` / `.route-card-action-danger` | 操作行；删除按钮 `margin-left: auto` 推到最右 |

这是 wrouter 的默认形态，规范名 **Card-based Route List**；列表视图只是它的紧凑投影，不是替代品。

### 6.8 路由列表视图 `.route-list`（可选）

`@text
   路由                     PATH          TARGET / 本地端口   请求/MIN  延迟  30 分钟   操作
☐ ● 用户服务 运行中   /api/user /user   127.0.0.1:18100     42      18ms  ▁▂▃▅▇    查看 日志 …
`@

| 类名 | 要点 |
| --- | --- |
| `.route-list` / `.route-list-head` / `.route-list-head-cell` | 容器与列头（10.5px，`--surface-ink-subtle`） |
| `.route-list-row` / `.route-list-row-selected` / `.route-list-row-muted` | 行状态，规则与卡片一致 |
| `.route-list-main` / `.route-list-name` / `.route-list-desc` | 名称列（含状态点） |
| `.route-list-paths` / `.route-list-port` | Path 列与端口列；chip 超出必须省略 |
| `.route-list-metrics` / `.route-list-num` | 两个数字列；`.route-list-metrics` 默认 `display: contents`，折叠时变 `flex` 并排 |
| `.route-list-spark` / `.route-list-actions` | 曲线列与操作列 |
| `.route-list-stacked-label` | 仅 ≤1100px 显示：列头消失后用行内小标签补回列语义 |

- 与卡片视图**共享同一套数据、强调色与操作**。
- 两种视图都**不是** `<table>`：用 CSS Grid 保证与卡片同一套视觉语言。
- 操作按钮在 ≤1100px 退化为纯图标（保留 `aria-label`）。

### 6.9 详情抽屉 `.drawer`（Topology-first，签名组件）

宽度 468px，从右侧滑入，结构固定：

`@text
演示环境                        ● 运行中      [×]
/realTimeMonitor

概览   配置   日志   指标
──────────────────────────────────
① 路由拓扑  Client → wrouter → 本地端口 → 目标服务
② 基本信息  路由名称 / Path Prefix / Target URL / 代理地址 / 本地绑定 / 状态 / 访问页
③ 运行状态  请求数 / 成功率 / 平均延迟 + 30 分钟曲线
④ 最近日志  时间 方法 路径 状态 耗时（最多 5 条 + 查看全部）
──────────────────────────────────
[编辑] [访问] [日志]              [删除]
`@

| 类名 | 要点 |
| --- | --- |
| `.drawer-scrim` / `.drawer` | 遮罩（z-index 40）/ 抽屉（z-index 41），`--shadow-float` |
| `.drawer-head` / `.drawer-title-row` / `.drawer-title` | 头部；标题衬线 23px |
| `.drawer-file` | 头部配置文件名，**必须省略号截断**，不能撑破抽屉 |
| `.drawer-paths` | Path 标签行 |
| `.drawer-tabs` / `.drawer-tab` | 标签页；当前项用 2px 底部墨色条 |
| `.drawer-body` / `.drawer-actions` | 滚动主体 / 常驻底部操作栏 |
| `.section` / `.section-head` / `.section-index` / `.section-title` | ①②③④ 分节；序号是**真实序列**，不是装饰 |
| `.field-grid` / `.field` / `.field-value` | 基本信息两列网格 |
| `.metric-grid` / `.metric-grid-5` / `.metric-figure` | 指标卡；`-5` 用于日志视图 5 项 |
| `.log-rows` / `.log-row` / `.log-row-path` | 最近日志列表（等宽 11.5px） |
| `.json-editor` | JSON 文本域（等宽、可编辑态高亮） |

- **概览首屏必须是拓扑**，不允许先放表单或 JSON。
- 底部操作栏常驻：`编辑` 主色，`删除` 危险描边。

### 6.10 拓扑 `.topology`

节点为 10px 圆角矩形：图标 + 标签 + 技术元信息。语义固定四段
**Client → wrouter 网关 → 本地端口 → 目标服务**（英文规范写法 `Client → wrouter → Local Port → Target`）。

| 类名 | 要点 |
| --- | --- |
| `.topology` | 纵向排列 |
| `.topology-node` / `.topology-node-{client,gateway,port,target}` | 四段节点；`-gateway` 用强调底 + 强调边框 |
| `.topology-node-icon` / `.topology-node-body` / `.topology-node-kicker` / `.topology-node-value` / `.topology-node-note` | 节点内部结构 |
| `.topology-link` / `.topology-link-flow` / `.topology-link-idle` | 连线 1px + 1.8s 流动光点；停用时静止 |
| `.topology-fallback` / `.topology-fallback-label` / `.topology-fallback-value` | 虚线兜底分支「未命中 → 默认地址」 |

### 6.11 表单与弹窗

| 类名 | 要点 |
| --- | --- |
| `.overlay` / `.modal` / `.modal-title` / `.modal-description` / `.modal-close` | 遮罩 z-index 50 / 弹窗 z-index 51；标题衬线 22px |
| `.modal-actions` | 操作区**粘底**：内容滚动而按钮始终可见 |
| `.form-grid` / `.form-field` / `.form-label` / `.form-hint` / `.required-mark` | 表单两列网格 |
| `.alert-error` | 错误区（红底红字） |
| `.prefix-editor` / `.prefix-remove` | 路径前缀编辑器 |

### 6.12 控件

| 类名 | 要点 |
| --- | --- |
| `.field-input` / `.field-select` | 38px 高、10px 圆角；focus 主色边框 + 3px 光环；下拉箭头用内联 SVG |
| `.btn` / `.btn-primary` / `.btn-danger` / `.btn-ghost` / `.btn-sm` / `.btn-icon` | 按钮族；主按钮用 `--surface-navy` 底（深色下用 `--surface-accent`） |
| `.chip` / `.chip-accent` / `.chip-success` / `.chip-warning` / `.chip-danger` | 徽标族（胶囊） |

`Button` 变体：`primary` / `default` / `outline` / `ghost` / `danger`；
尺寸：`sm` 32px、`default` 38px、`icon` 38px 方形。
`Badge` 变体：`default` / `primary` / `success` / `warning` / `danger` / `muted`。

### 6.13 日志视图与日志表

**作用域**：默认「全部路由」，聚合展示所有路由的日志，**不需要先选一条**。
顶部下拉切换作用域，标题随之变化（`路由日志 · 全部路由` / `路由日志 · <路由名>`）。

| 作用域 | 数据源 | 实时流 |
| --- | --- | --- |
| 全部路由 | `/admin/api/proxy-logs` | `/admin/api/proxy-logs/stream`，始终可连 |
| 单路由 | `/admin/api/proxy-logs/routes/{id}` | `/routes/{id}/stream`，仅路由启用时连 |

> 全部路由模式**不做空态拦截**：没有日志时直接显示「暂无代理请求」，
> 而不是要求用户先做选择。空态只服务于「真的没有数据」这一种情况。

日志表类名：

| 类名 | 要点 |
| --- | --- |
| `.table-wrap` | 容器，`min-width: 0` + `overflow: auto`（宽表不得撑宽父网格） |
| `.data-table` | 表头 `--surface-sunken` 且 sticky |
| `.cell-mono` | 等宽单元格 |
| `.method-badge` + `.method-get/post/put/delete/other` | 方法徽标：GET 青 / POST 蓝 / PUT 橙 / DELETE 红 |
| `.status-badge` + `.status-2xx/3xx/4xx/5xx/none` | 状态码徽标：2xx 绿、3xx 青、4xx 橙、5xx 红 |

> `status-*` 是**日志状态码**命名空间，与状态点 `state-*` 严格区分。

### 6.14 空态、骨架与滚动

| 类名 | 要点 |
| --- | --- |
| `.empty-state` / `.empty-title` / `.empty-copy` | 居中图标 + 14.5px 标题 + ≤42ch 说明 + 主操作 |
| `.skeleton` | 骨架屏 + `shimmer` 1.4s 微光；不用整屏 spinner |
| `.scroll-area` | 细滚动条（9px，圆角滑块，content-box 内缩） |

### 6.15 版本与更新抽屉

结构：① 当前安装（版本号 / 构建时间 / 运行平台 / 安装方式 / 更新通道 / 自动更新）
② **自动更新**（策略说明 + 在途请求 / 空闲判定 / 待应用版本 / 静默阈值 + 等待原因）
③ 手动更新（[`检查更新`] → 状态文案 → 最新版本 / 资产大小 / 通道 → 下载资产 / 更新说明
→ [`一键更新`] [`查看 Release 说明`]）。

**自动更新小节**（打开抽屉时每 3 秒轮询 `/admin/api/update/auto`）：

| 字段 | 含义 |
| --- | --- |
| 策略说明 | `autoUpdateSummary()`：后台自动检查并下载，空闲时无感完成更新 |
| 在途请求 | 当前正在代理中的请求数，>0 时不会重启 |
| 空闲判定 | `空闲` / `忙碌` |
| 待应用版本 | 已下载待安装的版本，未暂存时为 `—` |
| 静默阈值 | 判定空闲所需的静默秒数 |
| 等待原因 | `autoUpdateIdleText()`：有 N 个请求进行中 / 等待静默 N 秒 / 可安全更新 |

> 这一节的意义是**让「无感」可解释**：客户看到「有新版本但还没重启」时，
> 能立刻明白系统在等流量空档，而不是卡住了。

- 版本号、构建时间、资产名一律等宽；版本号带 `v` 前缀。
- 打开时自动静默检查一次；检查中图标旋转。
- 「一键更新」仅在「有更新 + 有匹配资产 + 当前安装支持自更新」时可用，否则禁用并在下方说明原因（不弹错误 toast）。
- 平台与安装方式用中文文案：`macOS · Apple Silicon`、`免安装程序`、`JAR 服务`。

### 6.16 轻提示

`[data-sonner-toast]` 系列覆盖 sonner 默认样式，使 Toast 跟随主题
（表面 `--surface-raised`、边框 `--surface-line-strong`、阴影 `--shadow-float`、字体 `--font-sans`）。

---

## 7. 动效

| 场景 | 时长 | 缓动 | 实现 |
| --- | --- | --- | --- |
| hover / 边框 / 颜色变化 | 140ms | `ease-out` | `transition` |
| 输入框 focus 光环 | 140ms | `ease-out` | `transition` |
| Drawer 滑入 `drawer-in` | 220ms | `cubic-bezier(.22,.61,.36,1)` | `@keyframes` |
| 遮罩淡入 `scrim-in` | 160ms | `ease-out` | `@keyframes` |
| 状态点呼吸 `dot-pulse` | 1.6s 循环 | `ease-in-out` | `@keyframes` |
| 拓扑流动 `link-flow` | 1.8s 循环 | `linear` | `@keyframes` |
| 骨架微光 `shimmer` | 1.4s 循环 | `ease-in-out` | `@keyframes` |

**禁止**大幅缩放、旋转、粒子、3D、彩虹渐变、闪烁。
**必须**在 `prefers-reduced-motion: reduce` 下把全部循环动画与过渡压到 `0.001ms`（已实现，见 `styles.css` 末尾）。

---

## 8. 品牌符号

Logo 由节点与连线构成，禁止盾牌 / 云 / 服务器 / 齿轮作为核心图形：

`@text
●────●
 \  /
   ●
`@

| 位置 | 实现 |
| --- | --- |
| 侧栏标记 | `RouteNodeMark`（`currentColor` 描边，随主题） |
| 卡片 / 列表图标 | `RouteGlyph`（Accent 着色，节点填充用 `--surface-panel`） |
| favicon | `frontend/public/favicon.svg`：`#101F33` 底 + 白线 + 一个 `#8FC1FF` 节点 |

**不使用背景网络装饰线**——分层由 Paper Shell 承担。

---

## 9. 无障碍

| 项 | 要求 | 现状 |
| --- | --- | --- |
| 正文对比度 | ≥ 4.5:1 | 25 项抽样全部通过（浅色 / 深色） |
| 大字对比度 | ≥ 3:1 | 通过 |
| 键盘可达 | 全部交互元素 | 28 个可聚焦元素，均可达 |
| 焦点环 | `0 0 0 2px var(--surface-accent)` | 全局 `:focus-visible`，实测 Tab 生效 |
| 图标按钮 | 必须有 `aria-label` | 已核查，无遗漏 |
| 状态 | 除颜色外必须有文字 | `routeStatusText()` |
| 图表 | 必须有文本替代 | 概览带 `role="img"` + `aria-label`；Sparkline 同 |
| 弹窗 | `role="dialog"` / `aria-modal` | 抽屉与对话框均已标注 |
| 动效 | 尊重 `prefers-reduced-motion` | 已实现 |

**回归防线**：改墨色或表面色后必须复算对比度。浅色下 `--surface-ink-subtle` 与
`--surface-ink-muted` 曾因取值过浅不达标，深色 `--band-ink*` 也因复用页面墨色不达标，均已修正。

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
- [ ] 存在 **Paper Shell**：米灰画布 + 白色圆角外壳 + 暖灰侧栏
- [ ] 存在 **Editorial Heading**：页面、抽屉、区块、弹窗标题均为衬线体
- [ ] 存在 **Single Gradient Band**：全站只有概览带一处装饰性渐变
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

- [ ] **浅色默认 / 深色可选，两套都可读且对比度达标**
- [ ] 颜色全部走 CSS 变量，没有硬编码十六进制
- [ ] 没有过度模板化
- [ ] 没有过度玻璃拟态
- [ ] 没有过多渐变
- [ ] 没有过多圆角
- [ ] 适合开发者长期使用

工程类：

- [ ] 没有「定义了但没有任何组件使用」的 CSS 类或令牌
- [ ] 状态点用 `state-*`、日志状态码用 `status-*`，两者没有混用
- [ ] Tailwind 工具类能覆盖组件默认值（`@tailwind utilities` 在样式表末尾输出）
- [ ] 空态、加载态、停用态、错误态都有明确实现
- [ ] `npm run typecheck` / `npm test` / `mvn test` 全部通过

任意一项不满足，必须返工。
