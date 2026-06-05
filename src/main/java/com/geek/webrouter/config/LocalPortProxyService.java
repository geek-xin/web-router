package com.geek.webrouter.config;

import com.geek.webrouter.common.enums.ErrorCodeEnum;
import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为单个路由启动独立的本地 IP/端口监听，并把请求转发到该路由目标地址。
 */
@Slf4j
@Service
public class LocalPortProxyService {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private final ProxyRequestLogService logService;
    private final LocalProxyServerFactory serverFactory;

    private final HttpClient httpClient = HttpClient.create();
    private final Map<String, DisposableServer> servers = new ConcurrentHashMap<>();
    private final Object refreshMonitor = new Object();
    private long refreshVersion = 0;

    @Autowired
    public LocalPortProxyService(ProxyRequestLogService logService) {
        this.logService = logService;
        this.serverFactory = config -> HttpServer.create()
                .host(config.effectiveLocalIp())
                .port(config.getLocalPort())
                .handle((request, response) -> proxy(config, request, response))
                .bindNow();
    }

    LocalPortProxyService(ProxyRequestLogService logService, LocalProxyServerFactory serverFactory) {
        this.logService = logService;
        this.serverFactory = serverFactory;
    }

    public Mono<Void> refreshAll(List<RouteConfig> enabledConfigs) {
        long version;
        synchronized (refreshMonitor) {
            refreshVersion += 1;
            version = refreshVersion;
        }
        return Mono.fromRunnable(() -> {
            synchronized (refreshMonitor) {
                if (version != refreshVersion) {
                    return;
                }
                stopAll();
                if (version != refreshVersion) {
                    return;
                }
                enabledConfigs.stream()
                        .filter(RouteConfig::hasLocalBinding)
                        .forEach(this::start);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @PreDestroy
    public void stopAll() {
        servers.forEach((routeId, server) -> {
            try {
                server.disposeNow();
                log.info("已停止本地端口代理: {} {}", routeId, server.address());
            } catch (Exception e) {
                log.warn("停止本地端口代理失败: {} — {}", routeId, e.getMessage());
            }
        });
        servers.clear();
    }

    private void start(RouteConfig config) {
        try {
            DisposableServer server = serverFactory.start(config);
            servers.put(config.getId(), server);
            log.info("已启动本地端口代理: {} {}:{} -> {}",
                    config.getId(), config.effectiveLocalIp(), config.getLocalPort(), config.getTargetUrl());
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST,
                    "启动本地端口代理失败: " + config.effectiveLocalIp() + ":" + config.getLocalPort()
                            + " — " + e.getMessage());
        }
    }

    private Publisher<Void> proxy(RouteConfig config, HttpServerRequest request, HttpServerResponse response) {
        long start = System.nanoTime();
        prepareLocalProxyResponse(response);
        if (!matchesConfiguredPrefix(config, request.path())) {
            log.info("本地端口代理拒绝未配置前缀请求: {} {}", config.getId(), request.uri());
            response.status(404);
            return response.sendString(Mono.just("No matching local route prefix")).then();
        }
        String targetUri = targetUri(config, request.uri());
        log.info("本地端口代理转发请求: {} {} -> {}", config.getId(), request.uri(), targetUri);
        return httpClient
                .headers(headers -> copyRequestHeaders(request.requestHeaders(), headers, URI.create(targetUri)))
                .request(request.method())
                .uri(targetUri)
                .send(request.receive().retain())
                .response((clientResponse, content) -> {
                    int status = clientResponse.status().code();
                    response.status(clientResponse.status());
                    copyResponseHeaders(clientResponse.responseHeaders(), response.responseHeaders());
                    return response.send(content.retain())
                            .then()
                            .doFinally(signalType -> recordLog(config, request, status, start));
                })
                .onErrorResume(error -> {
                    log.warn("本地端口代理请求失败: {} {} -> {} — {}",
                            config.getId(), request.uri(), targetUri, error.getMessage());
                    response.status(502);
                    return response.sendString(Mono.just("Proxy request failed"))
                            .then()
                            .doFinally(signalType -> recordLog(config, request, 502, start));
                });
    }

    private void recordLog(RouteConfig config, HttpServerRequest request, int status, long start) {
        logService.record(new ProxyRequestLogEntry(
                null,
                config.getId(),
                request.method().name(),
                request.path(),
                clientIp(request),
                status,
                (System.nanoTime() - start) / 1_000_000
        ));
    }

    private void prepareLocalProxyResponse(HttpServerResponse response) {
        response.responseHeaders().set(HttpHeaderNames.CONNECTION, "close");
        response.responseHeaders().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.responseHeaders().set(HttpHeaderNames.PRAGMA, "no-cache");
        response.responseHeaders().set(HttpHeaderNames.EXPIRES, "0");
    }

    private boolean matchesConfiguredPrefix(RouteConfig config, String requestPath) {
        String path = normalizeRequestPath(requestPath);
        return config.effectivePathPrefixes().stream()
                .anyMatch(prefix -> matchesPrefix(path, prefix));
    }

    private boolean matchesPrefix(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        String normalizedPrefix = normalizePrefix(prefix);
        if ("/".equals(normalizedPrefix)) {
            return true;
        }
        return path.equals(normalizedPrefix) || path.startsWith(normalizedPrefix + "/");
    }

    private String normalizeRequestPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "/";
        }
        String normalized = requestPath.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizePrefix(String prefix) {
        String normalized = prefix.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String clientIp(HttpServerRequest request) {
        String forwardedFor = request.requestHeaders().get("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.remoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "-";
        }
        if (remoteAddress.getAddress().isLoopbackAddress()) {
            return "127.0.0.1";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    String targetUri(RouteConfig config, String requestUri) {
        return buildTargetUri(config.getTargetUrl(), requestUri);
    }

    private String buildTargetUri(String targetUrl, String requestUri) {
        String base = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;
        String suffix = requestUri == null || requestUri.isBlank() ? "/" : requestUri;
        return base + (suffix.startsWith("/") ? suffix : "/" + suffix);
    }

    private void copyRequestHeaders(HttpHeaders source, HttpHeaders target, URI targetUri) {
        copyHeaders(source, target);
        target.set(HttpHeaderNames.HOST, hostHeader(targetUri));
    }

    private void copyResponseHeaders(HttpHeaders source, HttpHeaders target) {
        copyHeaders(source, target);
    }

    private void copyHeaders(HttpHeaders source, HttpHeaders target) {
        Set<String> connectionHeaders = connectionHeaders(source);
        source.forEach(entry -> {
            String name = entry.getKey();
            String lowerName = name.toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lowerName) && !connectionHeaders.contains(lowerName)) {
                target.add(name, entry.getValue());
            }
        });
    }

    private Set<String> connectionHeaders(HttpHeaders headers) {
        Set<String> names = new HashSet<>();
        headers.getAll(HttpHeaderNames.CONNECTION).forEach(value -> {
            for (String name : value.split(",")) {
                if (!name.isBlank()) {
                    names.add(name.trim().toLowerCase());
                }
            }
        });
        return names;
    }

    @FunctionalInterface
    interface LocalProxyServerFactory {
        DisposableServer start(RouteConfig config);
    }

    private String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }
}
