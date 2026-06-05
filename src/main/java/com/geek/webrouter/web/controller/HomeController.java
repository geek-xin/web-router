package com.geek.webrouter.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Entry page routing.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public Mono<Void> index(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create("/admin"));
        return exchange.getResponse().setComplete();
    }
}
