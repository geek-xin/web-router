# 快速开始

## 环境要求

| 方式 | 需要 |
| --- | --- |
| 安装包 / 免安装程序 | 无需 JDK（内置运行时） |
| 传统 JAR | JDK 21 |
| 从源码运行 | JDK 21、Maven 3.9+ |

## 方式一：下载安装（推荐）

从 [Releases](https://github.com/geek-xin/web-router/releases/latest) 下载对应平台产物：

| 平台 | 安装包 | 免安装 |
| --- | --- | --- |
| macOS Apple Silicon | `wrouter-<version>-macos-arm64.dmg` | `-macos-arm64-app.zip` |
| macOS Intel | `wrouter-<version>-macos-x64.dmg` | `-macos-x64-app.zip` |
| Windows x64 | `wrouter-<version>-windows-x64.exe` | `-windows-x64-app.zip` |
| Linux x64 | `-linux-x64.deb` / `.rpm` | `-linux-x64-app.tar.gz` |

macOS 打开 dmg 拖入「应用程序」；Windows 运行 exe 按向导安装；Linux 用 `sudo dpkg -i` 或 `sudo rpm -i`。
免安装包解压后直接运行目录里的可执行文件。

启动后打开 <http://localhost:9999/admin>。

## 方式二：从源码运行

```bash
git clone https://github.com/geek-xin/web-router.git
cd web-router

mvn test                # 可选：先跑一遍测试
mvn spring-boot:run     # 默认 127.0.0.1:9999
```

`GET /` 会自动重定向到 `/admin`。

## 方式三：JAR 部署

```bash
unzip wrouter-<version>-jar.zip -d wrouter
cd wrouter
./run.sh          # Windows: run.bat
```

工作目录决定配置位置：路由文件在 `./config/routes/`。

## 创建第一条路由

在管理后台点「新增路由」，或直接调 API：

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

## 查看请求日志

```bash
curl http://localhost:9999/admin/api/proxy-logs/metrics   # 紧凑指标
curl -N http://localhost:9999/admin/api/proxy-logs/stream # SSE 实时流
```

后台的「请求日志」视图提供实时流、Top 路径、单耗时 Top 与诊断分析。

## 打包

```bash
scripts/build-dist.sh                                      # 传统 JAR 分发包
scripts/build-package.sh --type app-image --skip-tests     # 免安装程序（当前平台）
scripts/build-package.sh --type installer --skip-tests     # 安装包
scripts/build-release.sh --only jar,app-image,installer    # 全量契约资产
```

jpackage 不支持交叉构建，一个平台必须在对应机器或 CI 矩阵上构建。详见 [打包与发布](../docs/PACKAGING.md)。

## 下一步

- [用户指南](User-Guide)：路由字段与校验规则
- [架构与机制](Architecture)：两条入口的转发语义差异
- [API 参考](API-Reference)：完整接口速查
