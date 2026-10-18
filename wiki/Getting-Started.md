<div align="center">

<img src="https://raw.githubusercontent.com/geek-xin/web-router/main/docs/assets/logo.svg" alt="wrouter" width="48" height="48">

# 快速开始

**五分钟从零到第一条生效的路由**

</div>

---

## 选择你的安装方式

| 你的情况 | 推荐方式 | 需要 JDK |
| --- | --- | --- |
| 只想用起来，不打算改代码 | 下载安装包 | 不需要 |
| 想解压即用、不写系统目录 | 免安装 app-image | 不需要 |
| 已有 JDK 环境、要自定义部署 | 传统 JAR | JDK 21 |
| 想改代码或参与开发 | 源码运行 | JDK 21 + Maven 3.9+ |

## 方式一：下载安装（推荐）

从 [Releases](https://github.com/geek-xin/web-router/releases/latest) 下载对应平台产物：

| 平台 | 安装包 | 免安装 |
| --- | --- | --- |
| macOS · Apple Silicon | `wrouter-<version>-macos-arm64.dmg` | `…-macos-arm64-app.zip` |
| macOS · Intel | `wrouter-<version>-macos-x64.dmg` | `…-macos-x64-app.zip` |
| Windows · x64 | `wrouter-<version>-windows-x64.exe` | `…-windows-x64-app.zip` |
| Linux · x64 | `wrouter-<version>-linux-x64.deb` / `.rpm` | `…-linux-x64-app.tar.gz` |

校验后安装：

```bash
shasum -a 256 -c SHA256SUMS.txt

# macOS
open wrouter-<version>-macos-arm64.dmg     # 拖入「应用程序」

# Linux
sudo dpkg -i wrouter-<version>-linux-x64.deb
sudo rpm -i  wrouter-<version>-linux-x64.rpm

# Windows：双击 exe 按向导安装
```

启动后打开 <http://localhost:9999/admin>。

> **首次打开的系统提示**
> macOS：「无法验证开发者」→ 右键 →「打开」，或在「系统设置 → 隐私与安全性」里允许。
> Windows：SmartScreen →「更多信息」→「仍要运行」。

## 方式二：免安装 app-image

解压后直接运行目录里的可执行文件，配置与数据都在该目录内，删除目录即卸载。

```bash
# macOS
unzip wrouter-<version>-macos-arm64-app.zip
open wrouter-<version>-macos-arm64.app

# Linux
tar -xzf wrouter-<version>-linux-x64-app.tar.gz
./wrouter-<version>-linux-x64/bin/wrouter

# Windows：解压后双击 wrouter-<version>-windows-x64\wrouter.exe
```

配置与数据都在解压目录内（`Contents/app/config` 或 `lib/app/config`），删除目录即卸载。

## 方式三：传统 JAR

```bash
unzip wrouter-<version>-jar.zip
cd wrouter-<version>
./run.sh          # Windows: run.bat
```

需要 JDK 21。**启动工作目录决定配置位置**：路由文件在 `./config/routes/`。
可用 `-Dwrouter.home=/path` 或环境变量 `WROUTER_HOME` 显式指定应用根目录。

## 方式四：从源码运行

```bash
git clone https://github.com/geek-xin/web-router.git
cd web-router

mvn spring-boot:run     # 默认 127.0.0.1:9999
```

只有改前端时才需要 Node.js：

```bash
cd frontend && npm ci && npm run build   # 产物写入 src/main/resources/static/admin/
```

## 创建第一条路由

打开 <http://localhost:9999/admin> → 点「新增路由」，或直接调 API：

```bash
curl -X POST http://localhost:9999/admin/api/routes \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test"],
    "targetUrl": "localhost:8081",
    "accessPageBaseUrl": "localhost:8082",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

`targetUrl` 与 `accessPageBaseUrl` 可省略协议，保存时自动补 `http://`。
保存成功后 Gateway 路由与本地端口代理**立即刷新**，无需重启。

## 验证转发

假设目标服务在 `http://localhost:8081`，路由 `pathPrefixes=["/test"]`：

| 入口 | 请求 | 上游收到 | 原因 |
| --- | --- | --- | --- |
| Gateway `:9999` | `http://localhost:9999/test/hello` | `http://localhost:8081/hello` | 按层级 `StripPrefix` |
| 本地端口 `:18081` | `http://127.0.0.1:18081/test/hello` | `http://localhost:8082/test/hello` | 保留原始 URI，命中前缀走代理地址 |
| 本地端口 `:18081` | `http://127.0.0.1:18081/other` | `http://localhost:8081/other` | 未命中前缀走默认地址 |

```bash
curl http://localhost:9999/test/hello
curl http://127.0.0.1:18081/test/hello
```

> 两条入口语义不同是**设计如此**，不是缺陷。见 [用户指南](User-Guide#两条入口的转发语义差异)。

## 看看流量

后台的「请求日志」视图提供实时流、Top 路径、单耗时 Top 与诊断分析。也可以用 API：

```bash
curl http://localhost:9999/admin/api/proxy-logs/metrics    # 紧凑指标
curl -N http://localhost:9999/admin/api/proxy-logs/stream  # SSE 实时流
```

## 下一步

- [用户指南](User-Guide)：路由字段、校验规则、观测口径、版本与更新
- [架构与机制](Architecture)：动态路由、本地端口代理、自动更新时序
- [常见问题](Troubleshooting)：访问不到、统计对不上、端口冲突
