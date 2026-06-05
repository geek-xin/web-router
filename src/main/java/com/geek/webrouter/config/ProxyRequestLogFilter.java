package com.geek.webrouter.config;

import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
@RequiredArgsConstructor
public class ProxyRequestLogFilter implements GlobalFilter, Ordered {

    private final ProxyRequestLogService logService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        long start = System.nanoTime();
        String routeId = route.getId();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        String clientIp = clientIp(exchange);

        return chain.filter(exchange)
                .doFinally(signalType -> logService.record(new ProxyRequestLogEntry(
                        null,
                        routeId,
                        method,
                        path,
                        clientIp,
                        status(exchange),
                        (System.nanoTime() - start) / 1_000_000
                )));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private int status(ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        return statusCode == null ? 0 : statusCode.value();
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "-";
        }
        if (remoteAddress.getAddress().isLoopbackAddress()) {
            return "127.0.0.1";
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
