# 快速开始

## 环境要求

- JDK 21
- Maven 3.9+

## 启动

```bash
mvn test            # 可选：跑一遍测试
mvn spring-boot:run
```

启动后访问 <http://localhost:9999/admin>，`GET /` 会自动重定向到 `/admin`。

## 创建第一条路由

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

`targetUrl` 与 `accessPageBaseUrl` 可省略协议，保存时自动补 `http://`。保存成功后 Gateway 路由与本地端口代理立即刷新。

## 验证转发

假设目标服务在 `http://localhost:8081`，`pathPrefixes=["/test"]`：

| 入口 | 请求 | 上游收到 | 原因 |
| --- | --- | --- | --- |
| Gateway `:9999` | `http://localhost:9999/test/hello` | `http://localhost:8081/hello` | 按层级 `StripPrefix` |
| 本地端口 `:18081` | `http://127.0.0.1:18081/test/hello` | `http://localhost:8082/test/hello` | 保留原始 URI，命中前缀走代理地址 |
| 本地端口 `:18081` | `http://127.0.0.1:18081/other` | `http://localhost:8081/other` | 未命中前缀走默认地址 |

## 查看请求日志

```bash
curl http://localhost:9999/admin/api/proxy-logs/metrics
curl -N http://localhost:9999/admin/api/proxy-logs/stream
```

## 打包

```bash
scripts/build-dist.sh              # 传统 JAR 分发包
scripts/build-release.sh          # 全量资产（安装包 / app-image / JAR）
```

产物命名与安装方式详见 [打包与发布](../docs/PACKAGING.md)。

## 下一步

- [用户指南](User-Guide)：路由字段与校验规则。
- [架构与机制](Architecture)：转发语义差异。
- [API 参考](API-Reference)：完整接口速查。
