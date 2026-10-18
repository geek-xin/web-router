package com.geek.webrouter.web.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseInfo;
import com.geek.webrouter.web.service.ReleaseClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 基于 JDK {@link HttpClient} 的 GitHub Release 客户端。
 *
 * <p>不带任何第三方依赖；可选通过环境变量 {@code WROUTER_GITHUB_TOKEN} 提升速率限制。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Component
public class HttpReleaseClient implements ReleaseClient {

    /** GitHub REST API 前缀。 */
    private static final String API_BASE = "https://api.github.com/repos/";

    /** 请求超时时间。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** 单次列表请求最多读取的发布条数。 */
    private static final int RELEASES_PER_PAGE = 30;

    /** 网络抖动时的最大尝试次数（含首次）。 */
    private static final int MAX_ATTEMPTS = 3;

    /** 首次重试前的等待时间（毫秒），之后按倍数退避。 */
    private static final long RETRY_BASE_DELAY_MILLIS = 300L;

    /** 可选令牌环境变量名。 */
    private static final String TOKEN_ENV = "WROUTER_GITHUB_TOKEN";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public HttpReleaseClient(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    HttpReleaseClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<ReleaseInfo> listReleases(String repository) throws Exception {
        return withRetry("读取发布列表", () -> {
            HttpResponse<String> response = httpClient.send(
                    request(API_BASE + repository + "/releases?per_page=" + RELEASES_PER_PAGE).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(failureMessage(response));
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<ReleaseInfo> releases = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    releases.add(toRelease(node));
                }
            }
            return releases;
        });
    }

    /**
     * 对**瞬时**网络故障做有限次退避重试。
     *
     * <p>GitHub 是外部依赖，偶发 {@code ConnectException} / 超时属于正常现象；
     * 一次失败就放弃，会让「历史版本」「检查更新」在用户侧表现为随机不可用。
     * 这里最多尝试 {@value #MAX_ATTEMPTS} 次、退避 300ms → 600ms，成本很低。</p>
     *
     * <p>只重试 {@link IOException} 这类连接层故障：HTTP 状态码错误（403/404 等）
     * 是确定性结果，重试没有意义，直接抛出以免浪费 GitHub 配额。</p>
     */
    <T> T withRetry(String action, Callable<T> call) throws Exception {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.call();
            } catch (IOException e) {
                last = e;
                log.warn("{}失败（第 {}/{} 次尝试）: {}", action, attempt, MAX_ATTEMPTS, describe(e));
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MILLIS * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    /**
     * 把非 200 响应翻译成**可操作**的说明。
     *
     * <p>GitHub 未认证限流为每小时 60 次，用完后返回 403。只报
     * 「GitHub 返回状态码 403（IllegalStateException）」对用户毫无帮助——
     * 既不知道是限流、也不知道什么时候恢复。这里读 {@code x-ratelimit-reset}
     * 给出恢复时间，并说明如何提升限额。</p>
     */
    static String failureMessage(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 403 || status == 429) {
            String remaining = header(response, "x-ratelimit-remaining");
            String reset = header(response, "x-ratelimit-reset");
            StringBuilder text = new StringBuilder("GitHub API 限流（未认证每小时 60 次）");
            if ("0".equals(remaining)) {
                text.append("，当前配额已用完");
            }
            if (reset != null) {
                try {
                    long epochSeconds = Long.parseLong(reset.trim());
                    String at = java.time.ZonedDateTime
                            .ofInstant(java.time.Instant.ofEpochSecond(epochSeconds), java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    text.append("，将于 ").append(at).append(" 恢复");
                } catch (NumberFormatException ignored) {
                    // 头部格式异常时不显示时间，不影响其它信息
                }
            }
            text.append("。可设置环境变量 WROUTER_GITHUB_TOKEN 提升限额至每小时 5000 次");
            return text.toString();
        }
        if (status == 404) {
            return "GitHub 仓库不存在或不可访问（状态码 404），请检查 app.repository 配置";
        }
        return "GitHub 返回状态码 " + status;
    }

    /** 读取响应头；不存在时返回 null。 */
    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    /** 把异常链拼成可读文本：{@code ConnectException} 自带的 message 常为 null。 */
    static String describe(Throwable error) {
        StringBuilder text = new StringBuilder(error.getClass().getSimpleName());
        Throwable cause = error.getCause();
        int depth = 0;
        while (cause != null && depth++ < 4) {
            text.append(" <- ").append(cause.getClass().getSimpleName());
            cause = cause.getCause();
        }
        return text.toString();
    }

    @Override
    public InputStream openAsset(String assetUrl) throws Exception {
        HttpResponse<InputStream> response = httpClient.send(
                request(assetUrl).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("下载更新包失败，GitHub 返回状态码 " + response.statusCode());
        }
        return response.body();
    }

    private HttpRequest.Builder request(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "wrouter");
        String token = System.getenv(TOKEN_ENV);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private ReleaseInfo toRelease(JsonNode node) {
        List<ReleaseAsset> assets = new ArrayList<>();
        JsonNode assetsNode = node.path("assets");
        if (assetsNode.isArray()) {
            for (JsonNode asset : assetsNode) {
                assets.add(new ReleaseAsset(
                        asset.path("name").asText(""),
                        asset.path("browser_download_url").asText(""),
                        asset.path("size").asLong(0),
                        asset.path("digest").asText("")));
            }
        }
        return new ReleaseInfo(
                node.path("tag_name").asText(""),
                node.path("name").asText(""),
                node.path("body").asText(""),
                node.path("published_at").asText(""),
                node.path("html_url").asText(""),
                node.path("prerelease").asBoolean(false),
                node.path("draft").asBoolean(false),
                assets);
    }
}
