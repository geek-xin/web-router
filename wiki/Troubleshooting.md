# 常见问题

## 管理后台打不开

确认应用是否启动：

```bash
curl http://localhost:8090/actuator/health
```

确认访问地址：

```text
http://localhost:8090/admin
```

默认端口来自：

```text
src/main/resources/application.yml
```

## 新增路由后没有生效

检查：

1. 路由是否 `enabled=true`。
2. `pathPrefixes` 是否以 `/` 开头。
3. `targetUrl` 是否可访问。
4. 创建或更新接口是否返回 `success=true`。
5. 写操作后是否触发了动态路由刷新。
6. 如果使用本地端口代理，确认访问路径命中该路由 `pathPrefixes`。

写操作成功后，后台应刷新 Gateway 路由和本地端口代理。

## Gateway 路径前缀访问不到

确认 Gateway 转发会执行 `StripPrefix`。

例如：

```text
pathPrefixes = ["/test"]
```

请求：

```text
http://localhost:8090/test/hello
```

目标服务收到的路径是：

```text
/hello
```

如果目标服务实际需要 `/test/hello`，可以使用本地端口代理，或调整目标服务路径。

## 本地端口代理访问不到

检查：

1. 路由是否 `enabled=true`。
2. 是否配置了 `localPort`。
3. `localIp:localPort` 是否被其他进程占用。
4. 访问路径是否命中当前路由的 `pathPrefixes`。
5. `localIp` 是否是当前机器可监听地址。

本地端口代理只允许匹配当前路由 `pathPrefixes` 的请求进入；不匹配会返回 `404`。

## Gateway 和本地端口代理路径不一致

这是预期行为。

- Gateway 会按路径层级 `StripPrefix`。
- 本地端口代理只做入口隔离，不剥离前缀。

例如请求路径：

```text
/test/hello
```

Gateway 转发路径：

```text
/hello
```

本地端口代理转发路径：

```text
/test/hello
```

## 修改 pathPrefixes 后浏览器仍显示旧页面

本地端口代理刷新只影响后续 HTTP 请求。

如果目标系统页面已经加载，但没有重新发起网络请求，`web-router` 无法主动改变页面内已有状态。

建议：

- 强制刷新浏览器。
- 关闭旧标签页后重新打开。
- 查看后台日志确认请求是否重新到达代理。

可关注日志：

- `本地端口代理转发请求`
- `本地端口代理拒绝未配置前缀请求`

## API 返回 HTTP 200 但 success=false

这是项目约定。

业务异常和参数校验错误通常返回 HTTP 200，但响应体为：

```json
{
  "success": false,
  "code": 400,
  "message": "错误信息",
  "data": null
}
```

前端会根据 `success` 字段判断业务是否成功。

## 请求日志为空

检查：

1. 是否真的经过 Gateway 或本地端口代理访问。
2. 路由是否启用。
3. 请求路径是否命中路由前缀。
4. 应用是否刚刚重启。

请求日志保存在内存中，应用重启后会清空。

## 本地端口代理返回 502

本地端口代理失败时会返回：

```text
HTTP 502
Proxy request failed
```

常见原因：

- 目标服务没有启动。
- `targetUrl` 填写错误。
- 网络连接被拒绝。
- 目标服务提前关闭连接。

## 发布包运行失败

如果使用 `scripts/build-dist.sh` 生成发布包，解压后应通过包内 `run.sh` 启动：

```bash
./run.sh
```

确认：

- 本机安装 JDK 21。
- 当前目录有写入 `config/routes` 的权限。
- 端口 `8090` 和各路由 `localPort` 未被占用。

