# Route Log Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-route real-time proxy log panels and remove the standalone global log section from the admin page.

**Architecture:** Keep the existing Gateway log filter as the recorder. Add route-scoped snapshot and stream methods to the log service/controller, then move the UI from a page-level log board into a modal opened from each route card.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring WebFlux/SSE, Thymeleaf, vanilla JavaScript, CSS.

---

### Task 1: Route-Scoped Log Backend

**Files:**
- Modify: `src/test/java/com/geek/webrouter/web/service/ProxyRequestLogServiceTest.java`
- Modify: `src/main/java/com/geek/webrouter/web/service/ProxyRequestLogService.java`
- Modify: `src/test/java/com/geek/webrouter/web/controller/RouteConfigControllerTest.java`
- Modify: `src/main/java/com/geek/webrouter/web/controller/ProxyRequestLogController.java`

- [ ] Write a failing service test for `snapshot("route-a")` that excludes logs for other routes.
- [ ] Run `mvn -Dtest=ProxyRequestLogServiceTest test` and verify the new test fails because the method is missing.
- [ ] Implement `snapshot(String routeId)` and `stream(String routeId)` in `ProxyRequestLogService`.
- [ ] Run `mvn -Dtest=ProxyRequestLogServiceTest test` and verify it passes.
- [ ] Write a failing controller test for `GET /admin/api/proxy-logs/routes/{routeId}`.
- [ ] Run `mvn -Dtest=RouteConfigControllerTest test` and verify the new test fails because the endpoint is missing.
- [ ] Add route-scoped snapshot and stream endpoints to `ProxyRequestLogController`.
- [ ] Run `mvn -Dtest=RouteConfigControllerTest test` and verify it passes.

### Task 2: Route Log Modal UI

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/css/style.css`

- [ ] Remove the standalone `<section class="log-panel">` from `index.html`.
- [ ] Add a `日志` button to each route card.
- [ ] Add a route log modal with status, totals, IP stats, and log table.
- [ ] Replace global log JavaScript state with route-modal state and close the active `EventSource` when the modal closes.
- [ ] Add CSS for the modal log layout using existing table and status styles.
- [ ] Run `mvn test` for backend regression verification.
