package com.geek.webrouter.config;

import com.geek.webrouter.web.model.dto.ProxyRequestLogEntry;
import com.geek.webrouter.web.service.ProxyRequestLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
@RequiredArgsConstructor
public class ProxyRequestLogFilter implements GlobalFilter, Ordered {

    private static final int MAX_DETAIL_CHARS = 4096;

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
        String requestParams = exchange.getRequest().getURI().getRawQuery();
        String clientIp = clientIp(exchange);
        String accessAddress = ProxyAccessAddressFormatter.hostPort(route.getUri());
        StringBuilder responseBody = new StringBuilder();
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return super.writeWith(Flux.from(body)
                        .doOnNext(buffer -> appendPreview(responseBody, buffer, getHeaders().getContentType())));
            }
        };
        ServerWebExchange decoratedExchange = exchange.mutate().response(responseDecorator).build();

        return chain.filter(decoratedExchange)
                .doFinally(signalType -> logService.record(new ProxyRequestLogEntry(
                        null,
                        routeId,
                        method,
                        path,
                        clientIp,
                        status(exchange),
                        (System.nanoTime() - start) / 1_000_000,
                        requestParams,
                        "",
                        responseBody.toString(),
                        accessAddress
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

    private void appendPreview(StringBuilder target, DataBuffer dataBuffer, MediaType contentType) {
        if (target.length() >= MAX_DETAIL_CHARS) {
            return;
        }
        if (!isTextualResponse(contentType)) {
            if (target.isEmpty()) {
                target.append("[非文本响应]");
            }
            return;
        }
        ByteBuffer buffer = dataBuffer.asByteBuffer().asReadOnlyBuffer();
        String chunk = StandardCharsets.UTF_8.decode(buffer).toString();
        int remaining = MAX_DETAIL_CHARS - target.length();
        target.append(chunk, 0, Math.min(remaining, chunk.length()));
        if (chunk.length() > remaining) {
            target.append("\n[已截断]");
        }
    }

    private boolean isTextualResponse(MediaType contentType) {
        return contentType == null
                || "text".equals(contentType.getType())
                || contentType.includes(MediaType.APPLICATION_JSON)
                || contentType.includes(MediaType.APPLICATION_XML)
                || contentType.toString().contains("javascript")
                || contentType.toString().contains("form");
    }
}
