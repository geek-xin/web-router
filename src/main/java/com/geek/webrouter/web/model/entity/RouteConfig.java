package com.geek.webrouter.web.model.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 路由配置实体 — 一个 Web 服务的代理规则。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteConfig {

    /** 内部路由 ID，用作 Gateway 路由 ID 和配置文件名（不含扩展名）。 */
    private String id;

    /** 路由展示名称，可包含中文和符号。 */
    @NotBlank(message = "路由名称不能为空")
    private String name;

    /** 兼容旧配置的单个路径前缀，优先与 pathPrefixes 的第一项保持一致。 */
    private String pathPrefix;

    /** 匹配的路径前缀列表，如 /api/users、/admin。 */
    @Builder.Default
    private List<String> pathPrefixes = new ArrayList<>();

    /** 目标服务地址，如 http://192.168.1.100:8080。 */
    @NotBlank(message = "目标地址不能为空")
    private String targetUrl;

    /** 可选访问页，如 /portal/login.html 或 http://127.0.0.1:9191/portal/login.html。 */
    private String accessPage;

    /** 本地监听 IP，如 127.0.0.1。 */
    private String localIp;

    /** 本地监听端口；为空表示不启用本地端口代理。 */
    private Integer localPort;

    /** 是否启用。 */
    private boolean enabled;

    /**
     * 返回兼容旧 JSON 的有效路径前缀列表。
     */
    public List<String> effectivePathPrefixes() {
        List<String> source = pathPrefixes == null || pathPrefixes.isEmpty()
                ? (pathPrefix == null ? List.of() : List.of(pathPrefix))
                : pathPrefixes;
        return source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(prefix -> !prefix.isBlank())
                .toList();
    }

    /**
     * 同步设置新字段和旧字段，保证写回 JSON 时兼容旧调用方。
     */
    public void setEffectivePathPrefixes(List<String> prefixes) {
        List<String> normalized = prefixes == null
                ? List.of()
                : prefixes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(prefix -> !prefix.isBlank())
                .toList();
        this.pathPrefixes = new ArrayList<>(normalized);
        this.pathPrefix = normalized.isEmpty() ? null : normalized.getFirst();
    }

    public boolean hasLocalBinding() {
        return localPort != null;
    }

    public String effectiveLocalIp() {
        return localIp == null || localIp.isBlank() ? "127.0.0.1" : localIp.trim();
    }
}
