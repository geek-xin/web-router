package com.geek.webrouter.web.controller;

import com.geek.webrouter.common.constants.CommonConstants;
import com.geek.webrouter.common.enums.ErrorCodeEnum;
import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.common.result.Result;
import com.geek.webrouter.config.DynamicRouteService;
import com.geek.webrouter.web.model.dto.RouteConfigDto;
import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.RouteConfigService;
import com.geek.webrouter.web.support.RouteTargetUrlNormalizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 路由配置管理控制器 — REST API + 管理页面。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class RouteConfigController {

    private final RouteConfigService routeConfigService;
    private final DynamicRouteService dynamicRouteService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("routesConfigDir", Paths.get(CommonConstants.ROUTES_CONFIG_DIR)
                .toAbsolutePath()
                .normalize()
                .toString());
        return "index";
    }

    @GetMapping("/api/routes")
    @ResponseBody
    public Result<List<RouteConfig>> listAll() {
        return Result.success(routeConfigService.listAll());
    }

    @GetMapping("/api/routes/{name}")
    @ResponseBody
    public Result<RouteConfig> getByName(@PathVariable String name) {
        return Result.success(routeConfigService.getByName(name));
    }

    /** 获取路由配置的原始 JSON 文件内容。 */
    @GetMapping("/api/routes/{name}/raw")
    @ResponseBody
    public Result<Map<String, Object>> getRaw(@PathVariable String name) {
        RouteConfig config = routeConfigService.getByName(name);
        Path filePath = Paths.get(CommonConstants.ROUTES_CONFIG_DIR,
                config.getId() + CommonConstants.CONFIG_FILE_EXTENSION);
        try {
            String content = Files.readString(filePath);
            String fileName = filePath.toString();
            return Result.success(Map.of("fileName", fileName, "content", content));
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
        }
    }

    @PostMapping("/api/routes")
    @ResponseBody
    public Mono<Result<RouteConfig>> create(@Valid @RequestBody RouteConfigDto dto) {
        RouteConfig config = toEntity(dto);
        RouteConfig saved = routeConfigService.create(config);
        return dynamicRouteService.refreshAll()
                .thenReturn(Result.success(saved));
    }

    @PutMapping("/api/routes/{name}")
    @ResponseBody
    public Mono<Result<RouteConfig>> update(@PathVariable String name, @Valid @RequestBody RouteConfigDto dto) {
        RouteConfig config = toEntity(dto);
        RouteConfig updated = routeConfigService.update(name, config);
        return dynamicRouteService.refreshAll()
                .thenReturn(Result.success(updated));
    }

    @DeleteMapping("/api/routes/{name}")
    @ResponseBody
    public Mono<Result<Void>> delete(@PathVariable String name) {
        routeConfigService.delete(name);
        return dynamicRouteService.refreshAll()
                .thenReturn(Result.success());
    }

    private RouteConfig toEntity(RouteConfigDto dto) {
        RouteConfig config = RouteConfig.builder()
                .name(dto.getName().trim())
                .targetUrl(RouteTargetUrlNormalizer.normalize(dto.getTargetUrl()))
                .accessPageBaseUrl(normalizeOptionalUrl(dto.getAccessPageBaseUrl()))
                .accessPage(dto.getAccessPage())
                .localIp(dto.getLocalIp())
                .localPort(dto.getLocalPort())
                .enabled(dto.isEnabled())
                .build();
        config.setEffectivePathPrefixes(dto.effectivePathPrefixes());
        return config;
    }

    private String normalizeOptionalUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return RouteTargetUrlNormalizer.normalize(value);
    }
}
