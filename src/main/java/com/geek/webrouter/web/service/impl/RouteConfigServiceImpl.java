package com.geek.webrouter.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.webrouter.common.constants.CommonConstants;
import com.geek.webrouter.common.enums.ErrorCodeEnum;
import com.geek.webrouter.common.exception.BusinessException;
import com.geek.webrouter.web.model.entity.RouteConfig;
import com.geek.webrouter.web.service.RouteConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 路由配置服务实现 — 每个 Web 服务一个 JSON 文件。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Service
public class RouteConfigServiceImpl implements RouteConfigService {

    private static final DateTimeFormatter ROUTE_ID_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private record RouteConfigWithModifiedTime(RouteConfig config, FileTime modifiedTime) {
    }

    private final ObjectMapper objectMapper;
    private final Path configDir;
    private final Object fileMonitor = new Object();

    @Autowired
    public RouteConfigServiceImpl(ObjectMapper objectMapper) {
        this(objectMapper, Paths.get(CommonConstants.ROUTES_CONFIG_DIR));
    }

    RouteConfigServiceImpl(ObjectMapper objectMapper, Path configDir) {
        this.objectMapper = objectMapper;
        this.configDir = configDir;
    }

    @PostConstruct
    @Override
    public void initDefaultConfigs() {
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            log.error("初始化配置目录失败", e);
        }
    }

    @Override
    public List<RouteConfig> listAll() {
        List<RouteConfigWithModifiedTime> configs = new ArrayList<>();
        try {
            Files.createDirectories(configDir);
            try (Stream<Path> files = Files.list(configDir)) {
                files.filter(p -> p.toString().endsWith(CommonConstants.CONFIG_FILE_EXTENSION))
                        .forEach(p -> {
                            try {
                                RouteConfig config = objectMapper.readValue(p.toFile(), RouteConfig.class);
                                ensureId(config, p);
                                ensureEffectivePathPrefixes(config);
                                configs.add(new RouteConfigWithModifiedTime(config, Files.getLastModifiedTime(p)));
                            } catch (IOException e) {
                                log.warn("读取配置文件失败: {}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
        }
        configs.sort(Comparator.comparing(RouteConfigWithModifiedTime::modifiedTime).reversed());
        return configs.stream()
                .map(RouteConfigWithModifiedTime::config)
                .toList();
    }

    @Override
    public RouteConfig getByName(String name) {
        Path filePath = resolveFilePath(name);
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "路由配置不存在: " + name);
        }
        try {
            RouteConfig config = objectMapper.readValue(filePath.toFile(), RouteConfig.class);
            ensureId(config, filePath);
            ensureEffectivePathPrefixes(config);
            return config;
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
        }
    }

    @Override
    public RouteConfig create(RouteConfig config) {
        synchronized (fileMonitor) {
            validateDisplayName(config.getName());
            normalizeAndValidatePathPrefixes(config);
            normalizeAndValidateLocalBinding(config);
            normalizeAccessPage(config);
            validateProxyAddressConfigured(config);
            String id = nextRouteId();
            config.setId(id);
            Path filePath = resolveFilePath(id);
            if (Files.exists(filePath)) {
                throw new BusinessException(ErrorCodeEnum.DUPLICATE_NAME, "路由 ID 已存在: " + id);
            }
            checkNameConflict(id, config.getName());
            checkLocalBindingConflict(id, config);

            try {
                Files.createDirectories(configDir);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), config);
                log.info("路由配置已创建: {} ({})", config.getName(), id);
                return config;
            } catch (IOException e) {
                throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
            }
        }
    }

    @Override
    public RouteConfig update(String name, RouteConfig config) {
        synchronized (fileMonitor) {
            Path filePath = resolveFilePath(name);
            if (!Files.exists(filePath)) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "路由配置不存在: " + name);
            }
            RouteConfig currentConfig;
            FileTime originalModifiedTime;
            try {
                currentConfig = objectMapper.readValue(filePath.toFile(), RouteConfig.class);
                ensureId(currentConfig, filePath);
                ensureEffectivePathPrefixes(currentConfig);
                originalModifiedTime = Files.getLastModifiedTime(filePath);
            } catch (IOException e) {
                throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
            }

            validateDisplayName(config.getName());
            normalizeAndValidatePathPrefixes(config);
            normalizeAndValidateLocalBinding(config);
            normalizeAccessPage(config);
            validateProxyAddressConfigured(config);
            config.setId(name);
            checkNameConflict(name, config.getName());
            checkLocalBindingConflict(name, config);

            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), config);
                if (hasSameEditableContent(currentConfig, config)) {
                    Files.setLastModifiedTime(filePath, originalModifiedTime);
                }
                log.info("路由配置已更新: {} ({})", config.getName(), name);
                return config;
            } catch (IOException e) {
                throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
            }
        }
    }

    @Override
    public void delete(String name) {
        synchronized (fileMonitor) {
            Path filePath = resolveFilePath(name);
            try {
                if (!Files.deleteIfExists(filePath)) {
                    throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "路由配置不存在: " + name);
                }
                log.info("路由配置已删除: {}", name);
            } catch (IOException e) {
                throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, e.getMessage());
            }
        }
    }

    /**
     * 检查本地监听地址是否与其他路由重复（排除自身）。
     */
    private void checkLocalBindingConflict(String excludeId, RouteConfig config) {
        if (!config.hasLocalBinding()) {
            return;
        }
        String binding = localBinding(config);
        Optional<RouteConfig> conflict = listAll().stream()
                .filter(RouteConfig::hasLocalBinding)
                .filter(c -> !routeId(c).equals(excludeId) && Objects.equals(localBinding(c), binding))
                .findFirst();
        if (conflict.isPresent()) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_LOCAL_BINDING,
                    "本地监听地址已被 [" + conflict.get().getName() + "] 使用: " + binding);
        }
    }

    /**
     * 检查展示名称是否与其他路由冲突（排除自身）。
     */
    private void checkNameConflict(String excludeId, String name) {
        Optional<RouteConfig> conflict = listAll().stream()
                .filter(c -> !routeId(c).equals(excludeId) && c.getName().equals(name))
                .findFirst();
        if (conflict.isPresent()) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_NAME, "路由名称已存在: " + name);
        }
    }

    /**
     * 解析配置文件名对应的文件路径。
     */
    private Path resolveFilePath(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "路由 ID 格式不正确: " + name);
        }
        return configDir.resolve(name + CommonConstants.CONFIG_FILE_EXTENSION);
    }


    private boolean hasSameEditableContent(RouteConfig currentConfig, RouteConfig nextConfig) {
        return Objects.equals(currentConfig.getName(), nextConfig.getName())
                && Objects.equals(currentConfig.getTargetUrl(), nextConfig.getTargetUrl())
                && Objects.equals(currentConfig.getAccessPageBaseUrl(), nextConfig.getAccessPageBaseUrl())
                && Objects.equals(currentConfig.getAccessPage(), nextConfig.getAccessPage())
                && Objects.equals(currentConfig.effectiveLocalIp(), nextConfig.effectiveLocalIp())
                && Objects.equals(currentConfig.getLocalPort(), nextConfig.getLocalPort())
                && Objects.equals(currentConfig.effectivePathPrefixes(), nextConfig.effectivePathPrefixes());
    }

    private void normalizeAndValidatePathPrefixes(RouteConfig config) {
        List<String> prefixes = config.effectivePathPrefixes();
        if (prefixes.isEmpty()) {
            config.setEffectivePathPrefixes(List.of());
            return;
        }
        Set<String> uniquePrefixes = new LinkedHashSet<>();
        for (String prefix : prefixes) {
            prefix = normalizePathPrefix(prefix);
            if (!prefix.matches("^/[-a-zA-Z0-9_/]*$")) {
                throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "路径前缀格式不正确，如 /api/users");
            }
            if (!uniquePrefixes.add(prefix)) {
                throw new BusinessException(ErrorCodeEnum.DUPLICATE_PREFIX, "同一路由内路径前缀重复: " + prefix);
            }
        }
        config.setEffectivePathPrefixes(new ArrayList<>(uniquePrefixes));
    }

    private void ensureEffectivePathPrefixes(RouteConfig config) {
        config.setEffectivePathPrefixes(config.effectivePathPrefixes());
    }

    private void normalizeAndValidateLocalBinding(RouteConfig config) {
        String rawLocalIp = config.getLocalIp();
        String trimmedLocalIp = rawLocalIp == null ? "" : rawLocalIp.trim();
        if (config.getLocalPort() == null) {
            if (trimmedLocalIp.isBlank()) {
                config.setLocalIp(null);
                return;
            }
            if (!isLocalIpFormatValid(trimmedLocalIp)) {
                throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "本地 IP 格式不正确，如 127.0.0.1");
            }
            config.setLocalIp(trimmedLocalIp);
            return;
        }
        int port = config.getLocalPort();
        if (port < 1 || port > 65535) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "本地端口范围为 1-65535");
        }
        String localIp = config.effectiveLocalIp();
        if (!isLocalIpFormatValid(localIp)) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "本地 IP 格式不正确，如 127.0.0.1");
        }
        config.setLocalIp(localIp);
    }

    private void normalizeAccessPage(RouteConfig config) {
        String accessPageBaseUrl = config.getAccessPageBaseUrl();
        if (accessPageBaseUrl == null || accessPageBaseUrl.trim().isEmpty()) {
            config.setAccessPageBaseUrl(null);
        } else {
            config.setAccessPageBaseUrl(accessPageBaseUrl.trim());
        }

        String accessPage = config.getAccessPage();
        if (accessPage == null || accessPage.trim().isEmpty()) {
            config.setAccessPage(null);
            return;
        }
        config.setAccessPage(accessPage.trim());
    }

    private void validateProxyAddressConfigured(RouteConfig config) {
        if (!config.effectivePathPrefixes().isEmpty() && config.getAccessPageBaseUrl() == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "配置路径前缀时代理地址不能为空");
        }
    }

    private String normalizePathPrefix(String prefix) {
        String normalized = prefix.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isLocalIpFormatValid(String localIp) {
        if ("localhost".equals(localIp)) {
            return true;
        }
        String[] parts = localIp.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private String localBinding(RouteConfig config) {
        return config.effectiveLocalIp() + ":" + config.getLocalPort();
    }

    private void validateDisplayName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "路由名称不能为空");
        }
        if (name.trim().length() > 50) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "路由名称不能超过 50 个字");
        }
    }

    private void ensureId(RouteConfig config, Path filePath) {
        config.setId(fileNameWithoutExtension(filePath));
    }

    private String fileNameWithoutExtension(Path filePath) {
        String fileName = filePath.getFileName().toString();
        if (fileName.endsWith(CommonConstants.CONFIG_FILE_EXTENSION)) {
            return fileName.substring(0, fileName.length() - CommonConstants.CONFIG_FILE_EXTENSION.length());
        }
        return fileName;
    }

    private String routeId(RouteConfig config) {
        return config.getId() == null || config.getId().isBlank() ? config.getName() : config.getId();
    }

    private String nextRouteId() {
        for (int i = 0; i < 5; i++) {
            String id = "route-" + LocalDateTime.now().format(ROUTE_ID_TIME_FORMAT)
                    + "-" + UUID.randomUUID().toString().substring(0, 6);
            if (!Files.exists(resolveFilePath(id))) {
                return id;
            }
        }
        throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "生成路由 ID 失败");
    }
}
