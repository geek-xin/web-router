# Multiple Prefixes Per Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support multiple path prefixes for one target URL while storing them in one route JSON file.

**Architecture:** Add `pathPrefixes` alongside legacy `pathPrefix`, normalize to an effective prefix list, validate uniqueness across all files, and register one Gateway route definition per prefix. Keep UI and raw JSON editing compatible with both old and new JSON shapes.

**Tech Stack:** Spring Boot 3.5, Spring Cloud Gateway, Thymeleaf, vanilla JavaScript, JUnit 5.

---

### Task 1: Tests first

**Files:**
- Create: `src/test/java/com/geek/webrouter/web/model/entity/RouteConfigTest.java`
- Modify: `src/test/java/com/geek/webrouter/web/service/ProxyRequestLogServiceTest.java`

- [ ] Add tests proving legacy `pathPrefix` and new `pathPrefixes` both produce the effective prefix list.
- [ ] Add a test proving route log snapshots aggregate derived route ids such as `route-a__1` into base route `route-a`.
- [ ] Run targeted tests and confirm failures before production code.

### Task 2: Backend model, validation, and route registration

**Files:**
- Modify: `src/main/java/com/geek/webrouter/web/model/entity/RouteConfig.java`
- Modify: `src/main/java/com/geek/webrouter/web/model/dto/RouteConfigDto.java`
- Modify: `src/main/java/com/geek/webrouter/web/controller/RouteConfigController.java`
- Modify: `src/main/java/com/geek/webrouter/web/service/impl/RouteConfigServiceImpl.java`
- Modify: `src/main/java/com/geek/webrouter/config/DynamicRouteService.java`
- Modify: `src/main/java/com/geek/webrouter/web/service/ProxyRequestLogService.java`

- [ ] Add `pathPrefixes`, `effectivePathPrefixes()`, and compatibility behavior for `pathPrefix`.
- [ ] Convert incoming DTOs into normalized prefix lists.
- [ ] Validate all prefixes, duplicate prefixes inside a file, and duplicate prefixes across files.
- [ ] Register route definitions for each prefix; use base route id for first prefix and derived ids for additional prefixes.
- [ ] Aggregate logs by base route id.

### Task 3: Frontend display and form handling

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/css/style.css`

- [ ] Replace single prefix input with multi-line prefix input.
- [ ] Render all prefixes as tags on route cards.
- [ ] Submit `pathPrefixes` while also preserving first prefix as `pathPrefix` for compatibility.
- [ ] Make route-card action buttons fill grid cells so `删除` matches other button widths.

### Task 4: Verification

- [ ] Run targeted Maven tests for changed backend behavior.
- [ ] Run broader `mvn test` and report any unrelated known failures if present.
- [ ] Inspect diff for accidental unrelated changes.
