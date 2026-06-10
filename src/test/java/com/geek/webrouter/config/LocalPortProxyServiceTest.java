package com.geek.webrouter.config;

import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPortProxyServiceTest {

    @Test
    void staleRefreshDoesNotRestartLocalProxyAfterRouteIsDisabled() {
        List<FakeServer> startedServers = new ArrayList<>();
        LocalPortProxyService service = new LocalPortProxyService(
                new ProxyRequestLogService(),
                (config, handler) -> {
                    FakeServer server = new FakeServer(config.getLocalPort());
                    startedServers.add(server);
                    return server;
                }
        );
        RouteConfig enabledConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefix("/a")
                .targetUrl("http://127.0.0.1:8080")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(true)
                .build();

        Mono<Void> staleEnableRefresh = service.refreshAll(List.of(enabledConfig));

        service.refreshAll(List.of()).block(Duration.ofSeconds(3));
        staleEnableRefresh.block(Duration.ofSeconds(3));

        assertThat(startedServers).isEmpty();
    }

    @Test
    void refreshDisposesExistingLocalProxyWhenRouteBecomesDisabled() {
        List<FakeServer> startedServers = new ArrayList<>();
        LocalPortProxyService service = new LocalPortProxyService(
                new ProxyRequestLogService(),
                (config, handler) -> {
                    FakeServer server = new FakeServer(config.getLocalPort());
                    startedServers.add(server);
                    return server;
                }
        );
        RouteConfig enabledConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefix("/a")
                .targetUrl("http://127.0.0.1:8080")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(true)
                .build();

        service.refreshAll(List.of(enabledConfig)).block(Duration.ofSeconds(3));
        service.refreshAll(List.of()).block(Duration.ofSeconds(3));

        assertThat(startedServers).hasSize(1);
        assertThat(startedServers.getFirst().disposed).isTrue();
    }

    @Test
    void refreshUpdatesExistingLocalBindingWithoutRestartingServer() {
        List<FakeServer> startedServers = new ArrayList<>();
        LocalPortProxyService service = new LocalPortProxyService(
                new ProxyRequestLogService(),
                (config, handler) -> {
                    FakeServer server = new FakeServer(config.getLocalPort());
                    startedServers.add(server);
                    return server;
                }
        );
        RouteConfig firstConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/a"))
                .targetUrl("http://127.0.0.1:8080")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(true)
                .build();
        RouteConfig updatedConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/a", "/reportManage"))
                .targetUrl("http://127.0.0.1:8081")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(true)
                .build();

        service.refreshAll(List.of(firstConfig)).block(Duration.ofSeconds(3));
        service.refreshAll(List.of(updatedConfig)).block(Duration.ofSeconds(3));

        assertThat(startedServers).hasSize(1);
        assertThat(startedServers.getFirst().disposed).isFalse();
    }

    @Test
    void enableDisableEnableControlsRealLocalListenerLifecycle() throws IOException, InterruptedException {
        DisposableServer targetServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response.sendString(Mono.just("proxied " + request.uri())))
                .bindNow();
        LocalPortProxyService service = new LocalPortProxyService(new ProxyRequestLogService());
        int localPort = freePort();
        RouteConfig enabledConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefix("/a")
                .targetUrl("http://127.0.0.1:" + targetServer.port())
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();

        try {
            service.refreshAll(List.of(enabledConfig)).block(Duration.ofSeconds(3));
            assertThat(getBody(localPort, "/a/ping?x=1")).isEqualTo("proxied /a/ping?x=1");

            service.refreshAll(List.of()).block(Duration.ofSeconds(3));
            assertThatThrownBy(() -> getBody(localPort, "/a/ping?x=2"))
                    .isInstanceOf(IOException.class);

            service.refreshAll(List.of(enabledConfig)).block(Duration.ofSeconds(3));
            assertThat(getBody(localPort, "/a/ping?x=3")).isEqualTo("proxied /a/ping?x=3");
        } finally {
            service.stopAll();
            targetServer.disposeNow();
        }
    }

    @Test
    void localProxyChoosesProxyAddressForMatchedPrefixesAndDefaultAddressForOthers() {
        LocalPortProxyService service = new LocalPortProxyService(
                new ProxyRequestLogService(),
                (config, handler) -> new FakeServer(config.getLocalPort())
        );
        RouteConfig config = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/iotmgr", "/portal"))
                .targetUrl("http://127.0.0.1:8080")
                .accessPageBaseUrl("http://127.0.0.1:9090")
                .localIp("127.0.0.1")
                .localPort(18080)
                .enabled(true)
                .build();

        assertThat(service.targetUri(config, "/iotmgr/api/page?page=1"))
                .isEqualTo("http://127.0.0.1:9090/iotmgr/api/page?page=1");
        assertThat(service.targetUri(config, "/portal"))
                .isEqualTo("http://127.0.0.1:9090/portal");
        assertThat(service.targetUri(config, "/reportManage/api/page?page=1"))
                .isEqualTo("http://127.0.0.1:8080/reportManage/api/page?page=1");
    }

    @Test
    void localProxyForwardsRequestsOutsideConfiguredPrefixesToTarget() throws IOException, InterruptedException {
        AtomicInteger defaultRequests = new AtomicInteger();
        DisposableServer defaultServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    defaultRequests.incrementAndGet();
                    return response.sendString(Mono.just("default " + request.uri()));
                })
                .bindNow();
        AtomicInteger proxyRequests = new AtomicInteger();
        DisposableServer proxyServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    proxyRequests.incrementAndGet();
                    return response.sendString(Mono.just("proxy " + request.uri()));
                })
                .bindNow();
        ProxyRequestLogService logService = new ProxyRequestLogService();
        LocalPortProxyService service = new LocalPortProxyService(logService);
        int localPort = freePort();
        RouteConfig config = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/iotmgr", "/portal"))
                .targetUrl("http://127.0.0.1:" + defaultServer.port())
                .accessPageBaseUrl("http://127.0.0.1:" + proxyServer.port())
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();

        try {
            service.refreshAll(List.of(config)).block(Duration.ofSeconds(3));

            HttpResponse<String> response = getResponse(localPort, "/reportManage/api/configuration?show=1");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Connection")).hasValue("close");
            assertThat(response.headers().firstValue("Cache-Control"))
                    .hasValue("no-store, no-cache, must-revalidate, max-age=0");
            assertThat(response.headers().firstValue("Pragma")).hasValue("no-cache");
            assertThat(response.headers().firstValue("Expires")).hasValue("0");
            assertThat(response.body()).isEqualTo("default /reportManage/api/configuration?show=1");
            assertThat(defaultRequests).hasValue(1);
            assertThat(proxyRequests).hasValue(0);
            assertThat(logService.snapshot().totalRequests()).isEqualTo(1);
            assertThat(logService.snapshot().recentLogs().getFirst().accessAddress())
                    .isEqualTo("127.0.0.1:" + localPort);
        } finally {
            service.stopAll();
            defaultServer.disposeNow();
            proxyServer.disposeNow();
        }
    }

    @Test
    void localProxyForwardsConfiguredReportManagePrefix() throws IOException, InterruptedException {
        AtomicInteger defaultRequests = new AtomicInteger();
        DisposableServer defaultServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    defaultRequests.incrementAndGet();
                    return response.sendString(Mono.just("default " + request.uri()));
                })
                .bindNow();
        AtomicInteger proxyRequests = new AtomicInteger();
        DisposableServer proxyServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    proxyRequests.incrementAndGet();
                    return response.sendString(Mono.just("proxy " + request.uri()));
                })
                .bindNow();
        LocalPortProxyService service = new LocalPortProxyService(new ProxyRequestLogService());
        int localPort = freePort();
        RouteConfig config = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/iotmgr", "/sysmgr", "/idc-ui", "/portal", "/door", "/reportManage"))
                .targetUrl("http://127.0.0.1:" + defaultServer.port())
                .accessPageBaseUrl("http://127.0.0.1:" + proxyServer.port())
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();

        try {
            service.refreshAll(List.of(config)).block(Duration.ofSeconds(3));

            HttpResponse<String> response = getResponse(localPort, "/reportManage/api/configuration/");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Connection")).hasValue("close");
            assertThat(response.headers().firstValue("Cache-Control"))
                    .hasValue("no-store, no-cache, must-revalidate, max-age=0");
            assertThat(response.headers().firstValue("Pragma")).hasValue("no-cache");
            assertThat(response.headers().firstValue("Expires")).hasValue("0");
            assertThat(response.body()).isEqualTo("proxy /reportManage/api/configuration/");
            assertThat(defaultRequests).hasValue(0);
            assertThat(proxyRequests).hasValue(1);
        } finally {
            service.stopAll();
            defaultServer.disposeNow();
            proxyServer.disposeNow();
        }
    }

    @Test
    void nextRequestKeepsForwardingAllLocalPathsWhenPrefixesChangeWithoutRestartingListener() throws IOException, InterruptedException {
        AtomicInteger targetRequests = new AtomicInteger();
        DisposableServer targetServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    targetRequests.incrementAndGet();
                    return response.sendString(Mono.just("proxied " + request.uri()));
                })
                .bindNow();
        LocalPortProxyService service = new LocalPortProxyService(new ProxyRequestLogService());
        int localPort = freePort();
        RouteConfig firstConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/iotmgr", "/portal"))
                .targetUrl("http://127.0.0.1:" + targetServer.port())
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();
        RouteConfig addedPrefixConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/iotmgr", "/portal", "/reportManage"))
                .targetUrl("http://127.0.0.1:" + targetServer.port())
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();
        RouteConfig removedPrefixConfig = RouteConfig.builder()
                .id("route-a")
                .name("Route A")
                .pathPrefixes(List.of("/iotmgr", "/portal"))
                .targetUrl("http://127.0.0.1:" + targetServer.port())
                .localIp("127.0.0.1")
                .localPort(localPort)
                .enabled(true)
                .build();

        try {
            service.refreshAll(List.of(firstConfig)).block(Duration.ofSeconds(3));
            HttpResponse<String> beforePrefixAddedResponse = getResponse(localPort, "/reportManage/api/configuration");

            service.refreshAll(List.of(addedPrefixConfig)).block(Duration.ofSeconds(3));
            HttpResponse<String> afterPrefixAddedResponse = getResponse(localPort, "/reportManage/api/configuration");

            service.refreshAll(List.of(removedPrefixConfig)).block(Duration.ofSeconds(3));
            HttpResponse<String> afterPrefixRemovedResponse = getResponse(localPort, "/reportManage/api/configuration");

            assertThat(beforePrefixAddedResponse.statusCode()).isEqualTo(200);
            assertThat(beforePrefixAddedResponse.body()).isEqualTo("proxied /reportManage/api/configuration");
            assertThat(afterPrefixAddedResponse.statusCode()).isEqualTo(200);
            assertThat(afterPrefixAddedResponse.body()).isEqualTo("proxied /reportManage/api/configuration");
            assertThat(afterPrefixRemovedResponse.statusCode()).isEqualTo(200);
            assertThat(afterPrefixRemovedResponse.body()).isEqualTo("proxied /reportManage/api/configuration");
            assertThat(targetRequests).hasValue(3);
        } finally {
            service.stopAll();
            targetServer.disposeNow();
        }
    }

    private int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法分配测试端口", e);
        }
    }

    private String getBody(int port, String path) throws IOException, InterruptedException {
        return getResponse(port, path).body();
    }

    private HttpResponse<String> getResponse(int port, String path) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class FakeServer implements DisposableServer {
        private final int port;
        private boolean disposed;

        private FakeServer(int port) {
            this.port = port;
        }

        @Override
        public SocketAddress address() {
            return new InetSocketAddress("127.0.0.1", port);
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public Channel channel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disposeNow() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
