# 快速开始

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
  "localIp": "127.0.0.1",
  "localPort": 18081
}
```

可以访问：

```bash
curl http://127.0.0.1:18081/test/hello
```

目标服务收到的路径仍是：

```text
/test/hello
```

原因：本地端口代理只按 `pathPrefixes` 做入口隔离，不剥离路径前缀。

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
target/web-router-1.0.0-SNAPSHOT.tar.gz
target/dist/web-router-1.0.0-SNAPSHOT.tar.gz
```

发布包内包含 `run.sh`、`stop.sh`、`config/application.yml` 和 `config/routes/`，解压后可直接执行包内 `./run.sh` 后台启动，并通过 `./stop.sh` 停止。
