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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * 为单个路由启动独立的本地 IP/端口监听，按路径前缀决定转发到代理地址或默认地址。
 */
@Slf4j
@Service
public class LocalPortProxyService {

    private static final int MAX_DETAIL_CHARS = 4096;

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
    private final Map<String, LocalProxyServer> servers = new ConcurrentHashMap<>();
    private final Object refreshMonitor = new Object();
    private long refreshVersion = 0;

    @Autowired
    public LocalPortProxyService(ProxyRequestLogService logService) {
        this.logService = logService;
        this.serverFactory = (config, handler) -> HttpServer.create()
                .host(config.effectiveLocalIp())
                .port(config.getLocalPort())
                .handle(handler::apply)
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
                Map<String, RouteConfig> nextConfigsByBinding = localBindingConfigs(enabledConfigs);
                Set<String> bindingsToStop = new HashSet<>(servers.keySet());
                nextConfigsByBinding.forEach((binding, config) -> {
                    LocalProxyServer existingServer = servers.get(binding);
                    if (existingServer == null) {
                        start(binding, config);
                    } else {
                        existingServer.update(config);
                    }
                    bindingsToStop.remove(binding);
                });
                bindingsToStop.forEach(this::stop);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @PreDestroy
    public void stopAll() {
        servers.forEach((binding, server) -> {
            try {
                server.disposeNow();
                log.info("已停止本地端口代理: {} {}", server.routeId(), server.address());
            } catch (Exception e) {
                log.warn("停止本地端口代理失败: {} — {}", server.routeId(), e.getMessage());
            }
        });
        servers.clear();
    }

    private Map<String, RouteConfig> localBindingConfigs(List<RouteConfig> enabledConfigs) {
        Map<String, RouteConfig> configsByBinding = new HashMap<>();
        enabledConfigs.stream()
                .filter(RouteConfig::hasLocalBinding)
                .forEach(config -> {
                    String binding = localBinding(config);
                    RouteConfig previous = configsByBinding.putIfAbsent(binding, config);
                    if (previous != null) {
                        throw new BusinessException(ErrorCodeEnum.DUPLICATE_LOCAL_BINDING,
                                "本地监听地址已被 [" + previous.getName() + "] 使用: " + binding);
                    }
                });
        return configsByBinding;
    }

    private void start(String binding, RouteConfig config) {
        try {
            LocalProxyServer server = LocalProxyServer.start(config, serverFactory, this::proxy);
            servers.put(binding, server);
            log.info("已启动本地端口代理: {} {}:{} -> {}",
                    config.getId(), config.effectiveLocalIp(), config.getLocalPort(),
                    proxyTargetUrl(config));
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST,
                    "启动本地端口代理失败: " + config.effectiveLocalIp() + ":" + config.getLocalPort()
                            + " — " + e.getMessage());
        }
    }

    private void stop(String binding) {
        LocalProxyServer server = servers.remove(binding);
        if (server == null) {
            return;
        }
        try {
            server.disposeNow();
            log.info("已停止本地端口代理: {} {}", server.routeId(), server.address());
        } catch (Exception e) {
            log.warn("停止本地端口代理失败: {} — {}", server.routeId(), e.getMessage());
        }
    }

    private Publisher<Void> proxy(RouteConfig config, HttpServerRequest request, HttpServerResponse response) {
        long start = System.nanoTime();
        prepareLocalProxyResponse(response);
        String targetBaseUrl = targetBaseUrl(config, request.uri());
        String targetUri = buildTargetUri(targetBaseUrl, request.uri());
        String requestParams = requestParams(request.uri());
        String accessAddress = accessAddress(targetBaseUrl);
        StringBuilder requestBody = new StringBuilder();
        StringBuilder responseBody = new StringBuilder();
        String requestContentType = request.requestHeaders().get(HttpHeaderNames.CONTENT_TYPE);
        log.info("本地端口代理转发请求: {} {} -> {}", config.getId(), request.uri(), targetUri);
        return httpClient
                .headers(headers -> copyRequestHeaders(request.requestHeaders(), headers, URI.create(targetUri)))
                .request(request.method())
                .uri(targetUri)
                .send(request.receive()
                        .retain()
                        .doOnNext(buffer -> appendPreview(requestBody, buffer, requestContentType)))
                .response((clientResponse, content) -> {
                    int status = clientResponse.status().code();
                    String responseContentType = clientResponse.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE);
                    response.status(clientResponse.status());
                    copyResponseHeaders(clientResponse.responseHeaders(), response.responseHeaders());
                    return response.send(content
                                    .retain()
                                    .doOnNext(buffer -> appendPreview(responseBody, buffer, responseContentType)))
                            .then()
                            .doFinally(signalType -> recordLog(config, request, status, start,
                                    requestParams, requestBody.toString(), responseBody.toString(), accessAddress));
                })
                .onErrorResume(error -> {
                    log.warn("本地端口代理请求失败: {} {} -> {} — {}",
                            config.getId(), request.uri(), targetUri, error.getMessage());
                    response.status(502);
                    return response.sendString(Mono.just("Proxy request failed"))
                            .then()
                            .doFinally(signalType -> recordLog(config, request, 502, start,
                                    requestParams, requestBody.toString(), "Proxy request failed", accessAddress));
                });
    }

    private void recordLog(RouteConfig config, HttpServerRequest request, int status, long start) {
        recordLog(config, request, status, start, requestParams(request.uri()), "", "",
                accessAddress(targetBaseUrl(config, request.uri())));
    }

    private void recordLog(RouteConfig config,
                           HttpServerRequest request,
                           int status,
                           long start,
                           String requestParams,
                           String requestBody,
                           String responseBody,
                           String accessAddress) {
        logService.record(new ProxyRequestLogEntry(
                null,
                config.getId(),
                request.method().name(),
                request.path(),
                clientIp(request),
                status,
                (System.nanoTime() - start) / 1_000_000,
                requestParams,
                requestBody,
                responseBody,
                accessAddress
        ));
    }


    private void prepareLocalProxyResponse(HttpServerResponse response) {
        response.responseHeaders().set(HttpHeaderNames.CONNECTION, "close");
        response.responseHeaders().set(HttpHeaderNames.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.responseHeaders().set(HttpHeaderNames.PRAGMA, "no-cache");
        response.responseHeaders().set(HttpHeaderNames.EXPIRES, "0");
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
        return buildTargetUri(targetBaseUrl(config, requestUri), requestUri);
    }

    private String targetBaseUrl(RouteConfig config, String requestUri) {
        if (matchesConfiguredPrefix(config, requestPath(requestUri)) && config.getAccessPageBaseUrl() != null) {
            return config.getAccessPageBaseUrl();
        }
        return config.getTargetUrl();
    }

    private String proxyTargetUrl(RouteConfig config) {
        if (config.getAccessPageBaseUrl() == null) {
            return config.getTargetUrl();
        }
        return config.getAccessPageBaseUrl() + " (prefix) / " + config.getTargetUrl() + " (default)";
    }

    private boolean matchesConfiguredPrefix(RouteConfig config, String path) {
        List<String> prefixes = config.effectivePathPrefixes();
        if (prefixes.isEmpty()) {
            return false;
        }
        String normalizedPath = path == null || path.isBlank() ? "/" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        final String requestPath = normalizedPath;
        return prefixes.stream().anyMatch(prefix -> matchesPrefix(prefix, requestPath));
    }

    private boolean matchesPrefix(String prefix, String requestPath) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        if ("/".equals(prefix)) {
            return true;
        }
        return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
    }

    private String requestPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        int queryStart = requestUri.indexOf('?');
        return queryStart < 0 ? requestUri : requestUri.substring(0, queryStart);
    }

    private String buildTargetUri(String targetUrl, String requestUri) {
        String base = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;
        String suffix = requestUri == null || requestUri.isBlank() ? "/" : requestUri;
        return base + (suffix.startsWith("/") ? suffix : "/" + suffix);
    }

    private String requestParams(String requestUri) {
        if (requestUri == null) {
            return "";
        }
        int queryStart = requestUri.indexOf('?');
        if (queryStart < 0 || queryStart == requestUri.length() - 1) {
            return "";
        }
        return requestUri.substring(queryStart + 1);
    }

    private void appendPreview(StringBuilder target, io.netty.buffer.ByteBuf buffer, String contentType) {
        if (target.length() >= MAX_DETAIL_CHARS) {
            return;
        }
        if (!isTextualContent(contentType)) {
            if (target.isEmpty()) {
                target.append("[非文本内容]");
            }
            return;
        }
        ByteBuffer byteBuffer = buffer.nioBuffer().asReadOnlyBuffer();
        String chunk = StandardCharsets.UTF_8.decode(byteBuffer).toString();
        int remaining = MAX_DETAIL_CHARS - target.length();
        target.append(chunk, 0, Math.min(remaining, chunk.length()));
        if (chunk.length() > remaining) {
            target.append("\n[已截断]");
        }
    }

    private boolean isTextualContent(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.toLowerCase();
        return normalized.contains("text")
                || normalized.contains("json")
                || normalized.contains("xml")
                || normalized.contains("javascript")
                || normalized.contains("form");
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

    private String localBinding(RouteConfig config) {
        return config.effectiveLocalIp() + ":" + config.getLocalPort();
    }

    private String accessAddress(String targetBaseUrl) {
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            return "-";
        }
        return ProxyAccessAddressFormatter.hostPort(URI.create(targetBaseUrl));
    }

    @FunctionalInterface
    interface LocalProxyServerFactory {
        DisposableServer start(RouteConfig config,
                               BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> handler);
    }

    @FunctionalInterface
    private interface LocalProxyRequestHandler {
        Publisher<Void> handle(RouteConfig config, HttpServerRequest request, HttpServerResponse response);
    }

    private static class LocalProxyServer {
        private final AtomicReference<RouteConfig> configRef;
        private final DisposableServer server;

        private LocalProxyServer(AtomicReference<RouteConfig> configRef, DisposableServer server) {
            this.configRef = configRef;
            this.server = server;
        }

        private static LocalProxyServer start(RouteConfig config,
                                             LocalProxyServerFactory serverFactory,
                                             LocalProxyRequestHandler proxyHandler) {
            AtomicReference<RouteConfig> configRef = new AtomicReference<>(config);
            DisposableServer server = serverFactory.start(config,
                    (request, response) -> proxyHandler.handle(configRef.get(), request, response));
            return new LocalProxyServer(configRef, server);
        }

        private void update(RouteConfig config) {
            configRef.set(config);
        }

        private String routeId() {
            return configRef.get().getId();
        }

        private SocketAddress address() {
            return server.address();
        }

        private void disposeNow() {
            server.disposeNow();
        }
    }

    private String hostHeader(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }
}
