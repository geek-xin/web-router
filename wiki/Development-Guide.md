# 开发指南

本文面向维护者，说明核心文件、修改约定、测试验证和发布方式。

当前版本：`1.1.0`。

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 运行框架 | Spring Boot 3.5.2 |
| 路由代理 | Spring Cloud Gateway 2024.0.1 |
| 本地端口代理 | Reactor Netty |
| 页面模板 | Thymeleaf |
| 配置存储 | 本地 JSON 文件 |
| 构建工具 | Maven |
| Java 版本 | JDK 21 |

## 关键文件

| 文件 | 说明 |
| --- | --- |
| `src/main/java/com/geek/webrouter/Application.java` | 应用入口 |
| `src/main/java/com/geek/webrouter/config/DynamicRouteService.java` | 动态 Gateway 路由注册与刷新 |
| `src/main/java/com/geek/webrouter/config/LocalPortProxyService.java` | 每路由本地端口代理 |
| `src/main/java/com/geek/webrouter/config/ProxyRequestLogFilter.java` | Gateway 请求日志过滤器 |
| `src/main/java/com/geek/webrouter/web/service/impl/RouteConfigServiceImpl.java` | 路由 JSON 文件读写、校验、冲突检测 |
| `src/main/java/com/geek/webrouter/web/service/ProxyRequestLogService.java` | 内存请求统计与 SSE 日志流 |
| `src/main/java/com/geek/webrouter/web/controller/RouteConfigController.java` | 管理页面与路由配置 REST API |
| `src/main/java/com/geek/webrouter/web/controller/ProxyRequestLogController.java` | 请求日志 REST/SSE API |
| `src/main/java/com/geek/webrouter/web/support/RouteTargetUrlNormalizer.java` | 目标地址协议归一化 |
| `src/main/resources/templates/index.html` | 管理后台 Thymeleaf 挂载页 |
| `frontend/src/App.tsx` | React 管理后台入口组件 |
| `frontend/src/features/*` | 管理后台业务组件、工具与类型 |
| `frontend/src/styles.css` | 管理后台样式入口 |
| `src/main/resources/static/admin/assets/app.js` | Vite 构建后的管理后台脚本 |
| `src/main/resources/static/admin/assets/app.css` | Vite 构建后的管理后台样式 |
| `src/main/resources/application.yml` | 端口、Gateway、Thymeleaf、Actuator 配置 |
| `scripts/build-dist.sh` | 发布包构建脚本 |

## 修改动态路由

优先查看 `DynamicRouteService`。

注意事项：

- 写操作后必须刷新动态路由。
- 多路径前缀会产生派生 Gateway routeId。
- 日志统计通常需要归并派生 routeId。
- 修改 Path 或 StripPrefix 规则时，需要验证 Gateway 转发路径。
- 刷新应避免并发交错造成旧路由回写。

## 修改本地端口代理

优先查看 `LocalPortProxyService`。

注意事项：

- 仅 `enabled=true` 且设置 `localPort` 的路由会启动本地代理。
- 本地监听必须按当前路由 `pathPrefixes` 做入口隔离。
- 本地端口代理保留原始 URI，不能误加 Gateway 的 `StripPrefix` 语义。
- 刷新时尽量更新同绑定内存快照，避免无谓重启监听。
- 修改 Header、缓存或连接处理时，需要验证浏览器刷新和连接复用场景。

## 修改配置存储

优先查看 `RouteConfigServiceImpl`。

注意事项：

- 保持 `id` 文件名机制，读取时文件名 ID 是权威值。
- 不要绕过 `resolveFilePath()` 直接拼接文件路径。
- 保持旧字段 `pathPrefix` 兼容。
- 写回 JSON 时保持 `pathPrefix` 与 `pathPrefixes[0]` 同步。
- `listAll()` 按文件最后修改时间倒序返回。
- 新增校验规则后同步更新用户文档和 API 文档。

## 新增路由字段

新增字段时通常需要同步：

- `RouteConfig`
- `RouteConfigDto`
- `RouteConfigServiceImpl` 读写、校验、冲突检测
- `frontend/src/features/routes/*` 表单、列表、复制、编辑、JSON 预览逻辑
- `frontend/src/styles.css` 必要展示样式
- `src/main/resources/templates/index.html` 挂载页和元数据
- `README.md`、`USAGE.md`、`wiki/*`
- 旧 JSON 兼容逻辑和测试

## 修改前端

当前管理后台源码位于 `frontend/`，通过 Vite 构建到 Spring Boot 静态目录。修改前端时同步检查：

- `frontend/src/App.tsx`
- `frontend/src/features/**`
- `frontend/src/styles.css`
- `src/main/resources/templates/index.html`
- `src/main/resources/static/admin/assets/app.js`
- `src/main/resources/static/admin/assets/app.css`

如果改动管理后台行为，先运行 `npm run build` 更新静态资源，再用 `mvn test` 覆盖 Thymeleaf 渲染和服务层契约；必要时再手动启动访问 `/admin` 验证。

## 修改请求日志

请求日志同时来自两条路径：

- Gateway：`ProxyRequestLogFilter`
- 本地端口代理：`LocalPortProxyService`

修改统计口径时，需要保证两条路径一致，并继续处理多前缀派生 routeId 的归并。

## 异常响应约定

- 成功响应：`Result.success(data)`。
- 业务异常：抛 `BusinessException`，返回 HTTP 200，响应体 `success=false`。
- 参数校验异常：返回 HTTP 200，响应体 `success=false`、`code=400`。
- 未捕获异常：返回 HTTP 500。

前端依赖“业务错误 HTTP 200 + 响应体 `success=false`”的约定。

## 验证命令

运行测试：

```bash
mvn test
```

手动启动：

```bash
mvn spring-boot:run
```

构建发布包：

```bash
scripts/build-dist.sh --with-tests
```

默认跳过测试的快速打包：

```bash
scripts/build-dist.sh
```
