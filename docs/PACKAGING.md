# 打包与发布

本文说明 wrouter 的交付形态、发布资产命名契约、本地打包方式、自动更新机制与 CI 发布流程。

约定：下文 `<version>` 指 Maven 版本号（`pom.xml` 的 `<version>`，当前 `1.3.0`），不带 `v` 前缀。
`<platform>` 取 `macos-arm64`、`macos-x64`、`linux-x64`、`linux-arm64`、`windows-x64` 之一。

## 交付形态对照

| 形态 | 产物 | 是否需要 JDK | 升级方式 | 适用场景 |
| --- | --- | --- | --- | --- |
| 安装包 | `wrouter-<version>-<platform>.dmg` / `.deb` / `.rpm` / `.exe` | 否（内置运行时） | 系统包管理器或重新下载安装包 | 桌面/服务器常规分发 |
| 免安装 app-image | `wrouter-<version>-<platform>-app.zip`（Linux 为 `-app.tar.gz`） | 否（内置运行时） | 管理后台自动更新，或手动替换目录 | 不想装包、解压即用 |
| 传统 JAR | `wrouter-<version>-jar.zip` / `.tar.gz` | 是（Java 21） | 管理后台自动更新，或手动替换 jar | 已有 JDK 环境、自定义部署 |

三种形态共用同一套 `config/` 约定，路由配置与升级互不冲突。

## 资产命名契约

命名规则的权威实现在 `src/main/java/com/geek/webrouter/web/support/ReleaseAssets.java`，
打包脚本与 CI 必须与之一致，`scripts/build-release.sh` 会在发布前逐项校验。

| 资产名 | 形态 | 说明 |
| --- | --- | --- |
| `wrouter-<version>-jar.zip` | JAR | 自动更新在 `installMode=jar` 时只选这一项 |
| `wrouter-<version>-jar.tar.gz` | JAR | 与 zip 内容相同，便于 `tar` 环境 |
| `wrouter-<version>-<platform>-app.zip` | app-image | macOS / Windows |
| `wrouter-<version>-<platform>-app.tar.gz` | app-image | 仅 Linux |
| `wrouter-<version>-<platform>.dmg` | 安装包 | macOS |
| `wrouter-<version>-<platform>.deb` / `.rpm` | 安装包 | Linux（两者都会产出） |
| `wrouter-<version>-<platform>.exe` | 安装包 | Windows |
| `SHA256SUMS.txt` | 校验 | GitHub Release 上由 CI 统一生成 |

规则要点：

- 前缀恒为 `wrouter-`；版本号会去掉可能存在的 `v`/`V` 前缀后再拼接。
- app-image 的扩展名由平台决定：平台标识以 `linux` 开头用 `-app.tar.gz`，其余用 `-app.zip`。
- 自动更新只认 `jar` 与 `app-image` 两种 `installMode`；平台标识无法识别时判定为不支持自动更新。
- 选包时先按完整文件名精确匹配，再放宽为「同版本 + 同平台标识 + 同扩展名」，避免误选其他平台。

## 本地构建

三个脚本的分工：`build-dist.sh` 产出 JAR 分发包，`build-package.sh` 按类型产出单个平台产物，
`build-release.sh` 编排全部类型并按契约命名发布到 `target/release/`。

### 只构建 JAR 分发包

```bash
scripts/build-dist.sh --skip-tests --skip-build
```

产物：`target/dist/wrouter-<version>-jar.zip`、`target/wrouter-<version>-jar.zip`（同名 `.tar.gz` 与 `.zip`）。

常用参数：`--with-tests`（跑测试）、`--skip-tests`（默认，不跑测试）、`--skip-build`（复用已有 `target/wrouter-<version>.jar`）。

### 只构建某一平台

jpackage 不能交叉构建，app-image 与安装包必须跑在匹配的机器上：

```bash
# 免安装 app-image
scripts/build-package.sh --type app-image --platform macos-arm64 --skip-tests

# 安装包（macOS 出 dmg，Linux 出 deb+rpm，Windows 出 exe）
scripts/build-package.sh --type installer --platform windows-x64 --skip-tests

# 只包 JAR
scripts/build-package.sh --type jar --skip-tests
```

`--platform` 省略时取当前主机平台（由 `uname` 推断）。跨平台会被脚本拒绝，除非显式设置 `WROUTER_ALLOW_CROSS=1`。
平台不匹配时的错误信息会指明当前主机平台。

### 一次产出完整发布目录

```bash
scripts/build-release.sh --platform macos-arm64 --only jar,app-image,installer --skip-tests
```

- `--only` 可取 `jar`、`app-image`、`installer` 的任意子集，默认三者全做；执行顺序固定为 jar → app-image → installer，第一次 Maven 编译后其余类型复用构建输出。
- `--version` 覆盖版本号，`--release-dir` 覆盖输出目录（默认 `target/release`）。
- 输出 `target/release/`：契约命名的资产 + `SHA256SUMS.txt` + `release-manifest.txt`。
- 任一资产名不符合契约即失败退出；Linux 上没有 rpmbuild 时缺 `.rpm` 只告警，Windows 缺安装包才算失败（可用 `WROUTER_REQUIRE_INSTALLER=1` 收紧）。

### 打包相关环境变量

| 变量 | 作用 |
| --- | --- |
| `WROUTER_VERSION_OVERRIDE` | 覆盖从构建产物读到的版本号 |
| `WROUTER_RELEASE_DIR` | 契约资产输出目录，默认 `target/release` |
| `WROUTER_MAVEN_EXTRA_ARGS` | 追加 Maven 参数，如 `-Dmaven.test.skip=true` |
| `WROUTER_JPACKAGE_MODULES` | jlink 模块白名单，裁剪内置运行时；不设则打包完整 JDK（解压后约 147 MB） |
| `WROUTER_ALLOW_CROSS=1` | 允许跨平台打包（通常不可用） |
| `WROUTER_WIN_UPGRADE_UUID` | 覆盖 Windows 安装包的升级 UUID |

## 应用根目录

应用根目录决定配置与更新文件的位置，解析顺序（`AppHomeResolver`）：

1. 系统属性 `wrouter.home`——app-image 的启动参数即 `-Dwrouter.home=$APPDIR`；
2. 环境变量 `WROUTER_HOME`；
3. 当前工作目录（`user.dir`）。

三者都不可用时回落到相对路径 `.`，保持「相对启动目录」的默认行为。

| 路径 | 内容 |
| --- | --- |
| `<home>/config/routes/<id>.json` | 每条路由一个文件，由管理后台写入 |
| `<home>/config/application.yml` | 后端主配置（端口默认 `9999`、Gateway 超时、Actuator） |
| `<home>/updates/` | 自动更新工作目录：更新包、脚本、日志、备份 |

管理后台地址：`http://127.0.0.1:9999/admin`。

## 自动更新

### 流程

| 步骤 | 接口 | 行为 |
| --- | --- | --- |
| 1 | `GET /admin/api/version` | 读 classpath 的 `version.properties`，返回当前版本、平台、安装形态、更新通道、仓库 |
| 2 | `GET /admin/api/update/check` | 查 GitHub Release 列表，按通道过滤草稿/预发布，比对版本，按命名契约选包；**任何异常都写进 `message`，接口本身始终成功** |
| 3 | `POST /admin/api/update/apply` | 下载 → 校验 SHA-256 → 生成平台更新脚本到 `updates/` → 应用约 2 秒后退出 |

第 3 步不可更新时抛 `BusinessException`，请求体可带 `{"version": "..."}` 指定目标版本。
下载文件名会做路径穿越校验；发布方未提供摘要时跳过 SHA-256 比对（仅记日志），摘要不一致则删除已下载文件并报错。

更新脚本（`UpdateScriptGenerator`）：类 Unix 为 `updates/apply-update.sh`，Windows 为 `updates/apply-update.bat`。
脚本行为：等待旧进程退出（最长 30 秒）→ 备份旧文件到 `updates/backup-<当前版本>/` → 解压并替换（自动识别单层目录）→ 重启应用；
任一步失败都会回滚并把过程写入 `updates/update.log`。脚本可重复执行。

重启方式按形态区分：jar 形态优先用 `run.sh`/`run.bat`，否则直接 `java -jar`；app-image 形态执行
macOS 的 `wrouter.app/Contents/MacOS/wrouter`、或 Linux/Windows 的 `wrouter/bin/wrouter`、`wrouter/wrouter`、`wrouter\wrouter.exe`。

### 用户操作路径

1. 打开管理后台，进入版本对话框；页面会调用 `/admin/api/version` 展示当前版本。
2. 点击「检查更新」，前端请求 `/admin/api/update/check`。发现新版本时展示版本号、发布日期、Release 说明与匹配到的更新包名。
3. 点击「立即更新」，前端 `POST /admin/api/update/apply`（带目标版本）。成功后界面提示应用将在约 2 秒后退出。
4. 应用退出，由生成的脚本完成替换与重启；重新打开页面即为新版本。
5. 若重启后行为异常，查 `<home>/updates/update.log`；旧版本文件在 `<home>/updates/backup-<旧版本>/`。

不满足条件时前端不展示更新入口，`check` 的 `message` 会说明原因：安装形态/平台不支持、未配置更新仓库、
网络不可达、已是最新、目标版本没有匹配当前平台的更新包。

可选：设置环境变量 `WROUTER_GITHUB_TOKEN` 提升 GitHub API 速率限制。

## CI 发布流程

工作流：`.github/workflows/release.yml`。

### 触发条件

- `push` 到形如 `v*` 的 tag（例如 `v1.3.0`）；
- `workflow_dispatch` 手动触发，可填 `version`（默认取 `pom.xml`）与 `prerelease` 开关。

发布 job 只在「tag 以 `v` 开头」或「手动触发」时运行。

### 构建矩阵

| runner | platform | 构建类型 |
| --- | --- | --- |
| `macos-14` | `macos-arm64` | `jar,app-image,installer` |
| `macos-15-intel` | `macos-x64` | `app-image,installer` |
| `ubuntu-latest` | `linux-x64` | `app-image,installer` |
| `windows-latest` | `windows-x64` | `app-image,installer` |

JAR 分发包与平台无关，只在 `macos-14` 构建一次，其余矩阵项只构建本平台资产。矩阵 `fail-fast: false`，
单个平台失败不影响其他平台。任意矩阵项都不构建 `linux-arm64`，该平台需要自行本地构建。

每个矩阵项的步骤：checkout → JDK 21（temurin，缓存 maven）→ Node.js 22（缓存 npm）→ 解析版本 →
在 `frontend/` 执行 `npm ci && npm run build` → （仅 Linux）安装 `fakeroot`、`rpm` →
`scripts/build-release.sh --platform <p> --only <types> --skip-tests` → 上传 `target/release/*` 为 artifact（保留 7 天）。

### 从 tag 到产物清单

1. 打 tag 并推送：`git tag v1.3.0 && git push origin v1.3.0`。
2. 四个矩阵项并行产出各平台资产。
3. `release` job 汇总全部 artifact，重新生成 `SHA256SUMS.txt`（排除自身与 manifest，按文件名排序）。
4. 生成 Release Notes：下载对照表、校验命令、未签名提示指引、最近 40 条提交。
5. 通过 `softprops/action-gh-release@v2` 创建/更新 tag（`v<version>`）对应的 GitHub Release，附上全部资产。

一次 `macos-arm64` 发布的典型清单：

```
wrouter-1.3.0-jar.zip
wrouter-1.3.0-jar.tar.gz
wrouter-1.3.0-macos-arm64-app.zip
wrouter-1.3.0-macos-arm64.dmg
SHA256SUMS.txt
```

校验下载内容：`shasum -a 256 -c SHA256SUMS.txt`。

## 三种方式的使用与升级

### 安装包（dmg / deb / rpm / exe）

- **macOS**：打开 dmg，把应用程序拖入「应用程序」。启动参数已内置 `-Dwrouter.home=$APPDIR`，配置在应用目录内。
- **Linux**：`sudo dpkg -i wrouter-<version>-linux-x64.deb` 或 `sudo rpm -i wrouter-<version>-linux-x64.rpm`。
- **Windows**：运行 exe，安装向导支持选择目录、创建菜单项与快捷方式；升级 UUID 固定，可覆盖安装。

升级：重新下载对应安装包覆盖安装。应用根目录内的 `config/` 不会被覆盖。

### 免安装 app-image

```bash
unzip wrouter-<version>-macos-arm64-app.zip    # Linux: tar -xzf wrouter-<version>-linux-x64-app.tar.gz
cd wrouter-<version>-macos-arm64
```

- macOS：`wrouter-<version>-macos-arm64.app`，双击或执行 `./wrouter-<version>-macos-arm64.app/Contents/MacOS/wrouter`。
- Windows：`wrouter\wrouter.exe`。
- Linux：`wrouter/bin/wrouter`。

配置目录在应用包内（`Contents/app/config` 或 `lib/app/config`），随包分发 `config/README.md` 说明。
升级：管理后台自动更新（推荐），或解压新包替换 `wrouter`/`wrouter.app` 目录，保留其中的 `config/`。

### 传统 JAR

```bash
unzip wrouter-<version>-jar.zip
cd wrouter-<version>
./run.sh          # Windows: run.bat
./stop.sh         # Windows: stop.bat
```

- `run.sh`/`run.bat`：后台启动 `java -jar wrouter-<version>.jar`，写 `wrouter.pid`，日志在 `logs/wrouter.out`、`logs/wrouter.err`；启动等待秒数可用 `WROUTER_START_WAIT_SECONDS` 调整（默认 5）。
- `stop.sh`/`stop.bat`：按 pid 文件停止，校验进程命令行匹配后才杀；未在 30 秒内退出则强制结束。
- 包内已含 `config/application.yml` 与 `config/routes/`。

升级：管理后台自动更新，或替换 `wrouter-<version>.jar` 后重启。脚本与 `config/` 无需改动。

## 未签名应用的首次打开提示

产物没有代码签名与公证，首次运行会被系统拦截，这是预期行为。

| 系统 | 提示 | 处理 |
| --- | --- | --- |
| macOS | 「无法打开，因为 Apple 无法检查其是否包含恶意软件」或「已损坏」 | 右键（或 Control+点击）应用 → 「打开」→ 在弹窗中再次点「打开」；或在「系统设置 → 隐私与安全性」中点「仍要打开」。命令行可执行 `xattr -dr com.apple.quarantine wrouter-<version>-macos-arm64.app` 后再打开 |
| Windows | 「Windows 已保护你的电脑」（SmartScreen） | 点「更多信息」→「仍要运行」 |
| Linux | deb/rpm 无签名告警 | 用 `apt install ./` 或 `dnf install` 本地安装可减少提示 |

## 排障

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `jpackage cannot cross-build` | 目标平台与主机不符 | 在匹配的 runner/机器上构建，或设 `WROUTER_ALLOW_CROSS=1`（通常无效） |
| `asset name violates the update contract` | 资产名不符合 `ReleaseAssets` 规则 | 检查版本号是否含非法字符；改动命名需同步 `ReleaseAssets.java` |
| `missing expected asset: wrouter-<version>-jar.zip` | `build-dist.sh` 未产出 zip | 确认 `zip` / `tar` 命令在 PATH 中 |
| `required command not found: jpackage` | 未装 JDK 21 | 安装带 jpackage 的 JDK 21 |
| `Option [--linux-package-name] is not valid with type [app-image]` 或 `Option [--win-menu] is not valid with type [app-image]` | 把安装包专用参数传给了 `--type app-image` | 已修复：安装包专用参数由 `installer_only_args()` 单独维护，只传给 `build_installer` |
| `missing expected asset: wrouter-<version>-<platform>-app.tar.gz` | `--only` 只选了部分类型，但契约校验按所选类型逐项要求 | 补齐 `--only` 中的类型，或确认该平台确实能产出该资产 |
| 检查更新报「当前安装形态不支持自动更新」 | `installMode` 非 `jar`/`app-image`，或平台标识无法识别 | 用契约命名的产物；`platform` 必须是五个受支持标识之一 |
| 检查更新报「没有匹配当前平台的更新包」 | Release 缺少该形态该平台的资产 | 补齐资产或改用其他形态下载 |
| 检查更新报「无法连接 GitHub」 | 网络不可达或 API 限流 | 检查网络；设置 `WROUTER_GITHUB_TOKEN` |
| 摘要校验失败 | 下载文件与 Release 摘要不一致 | 应用已删除该文件；重试或手动下载 |
| 更新后版本没变 | 脚本未执行或应用未退出 | 查 `<home>/updates/update.log`；确认 `<home>/updates/` 可写 |
| 更新后启动失败 | 资产结构不符或重启失败 | 脚本已自动回滚；备份在 `<home>/updates/backup-<旧版本>/` |
| app-image 体积约 147 MB（解压后） | 默认打包完整 JDK | 构建时设 `WROUTER_JPACKAGE_MODULES` 裁剪运行时；模块缺失会在运行期报错，需实测 |
| 页面打不开 | 端口或根目录不对 | 默认端口 `9999`；确认 `wrouter.home`/`WROUTER_HOME`/`user.dir` 指向预期目录 |
