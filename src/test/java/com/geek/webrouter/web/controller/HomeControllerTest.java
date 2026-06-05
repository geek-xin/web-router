package com.geek.webrouter.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class HomeControllerTest {

    @Test
    void rootRedirectsToAdminPage() {
        WebTestClient.bindToController(new HomeController())
                .build()
                .get()
                .uri("/")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/admin");
    }
}
