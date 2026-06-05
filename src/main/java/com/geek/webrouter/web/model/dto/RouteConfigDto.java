package com.geek.webrouter.web.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * 路由配置 DTO，用于 API 请求/响应。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Data
public class RouteConfigDto {

    @NotBlank(message = "路由名称不能为空")
    private String name;

    @Pattern(regexp = "^/[-a-zA-Z0-9_/]*$", message = "路径前缀格式不正确，如 /api/users")
    private String pathPrefix;

    private List<String> pathPrefixes;

    @NotBlank(message = "目标地址不能为空")
    @Pattern(regexp = "^(https?://)?[-a-zA-Z0-9.]+:\\d{1,5}$",
            message = "目标地址格式不正确，如 192.168.1.100:8080 或 api.example.com:8080")
    private String targetUrl;

    @Pattern(regexp = "^$|^(localhost|([0-9]{1,3}\\.){3}[0-9]{1,3})$",
            message = "本地 IP 格式不正确，如 127.0.0.1")
    private String localIp;

    @Min(value = 1, message = "本地端口范围为 1-65535")
    @Max(value = 65535, message = "本地端口范围为 1-65535")
    private Integer localPort;

    private boolean enabled = false;

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

    @AssertTrue(message = "路径前缀不能为空")
    public boolean hasPathPrefixes() {
        return !effectivePathPrefixes().isEmpty();
    }

    @AssertTrue(message = "路径前缀格式不正确，如 /api/users")
    public boolean isPathPrefixesFormatValid() {
        return effectivePathPrefixes().stream()
                .allMatch(prefix -> prefix.matches("^/[-a-zA-Z0-9_/]*$"));
    }
}
