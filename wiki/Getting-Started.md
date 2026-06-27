# 快速开始

当前版本：`1.1.0`。

## 环境要求

- JDK 21
- Maven 3.9+
- Git

## 启动应用

在项目根目录执行：

```bash
mvn test
mvn spring-boot:run
```

默认服务地址：

```text
http://127.0.0.1:8090
```

启动后访问：

- 管理后台：<http://localhost:8090/admin>
- 健康检查：<http://localhost:8090/actuator/health>
- 应用信息：<http://localhost:8090/actuator/info>

`GET /` 会自动重定向到 `/admin`。

## 创建第一条路由

可以在管理后台新增路由，也可以直接调用 API：

```bash
curl -X POST http://localhost:8090/admin/api/routes \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试服务",
    "pathPrefixes": ["/test"],
    "targetUrl": "localhost:8081",
    "accessPageBaseUrl": "localhost:8082",
    "accessPage": "/test/hello",
    "localIp": "127.0.0.1",
    "localPort": 18081,
    "enabled": true
  }'
```

保存时，`targetUrl` 会自动归一化为：

```text
http://localhost:8081
```

## 验证 Gateway 转发

假设目标服务运行在 `http://localhost:8081`，路由前缀为 `/test`：

```bash
curl http://localhost:8090/test/hello
```

目标服务收到的路径是：

```text
/hello
```

原因：Gateway 会按路径层级执行 `StripPrefix`。

## 验证本地端口代理

如果路由配置了：

```json
{
  "targetUrl": "http://localhost:8081",
  "accessPageBaseUrl": "http://localhost:8082",
  "pathPrefixes": ["/test"],
  "localIp": "127.0.0.1",
  "localPort": 18081
}
```

可以访问：

```bash
curl http://127.0.0.1:18081/test/hello
```

命中路径前缀时，上游请求地址是：

```text
http://localhost:8082/test/hello
```

原因：本地端口代理不剥离路径前缀，并会在命中前缀时选择 `accessPageBaseUrl`。

## 查看请求日志

在管理后台点击路由的日志入口，或调用：

```bash
curl http://localhost:8090/admin/api/proxy-logs
curl -N http://localhost:8090/admin/api/proxy-logs/stream
```

## 打包分发

```bash
scripts/build-dist.sh --with-tests
```

输出：

```text
target/web-router-1.1.0.tar.gz
target/dist/web-router-1.1.0.tar.gz
```

发布包内包含 `run.sh`、`stop.sh`、`run.bat`、`stop.bat`、`config/application.yml` 和 `config/routes/`。Linux/macOS 可执行 `./run.sh` 后台启动并通过 `./stop.sh` 停止；Windows 可执行 `run.bat` 启动并通过 `stop.bat` 停止。
