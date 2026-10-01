package com.geek.webrouter.web.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.webrouter.web.model.dto.ReleaseAsset;
import com.geek.webrouter.web.model.dto.ReleaseInfo;
import com.geek.webrouter.web.service.ReleaseClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
        HttpResponse<String> response = httpClient.send(
                request(API_BASE + repository + "/releases?per_page=" + RELEASES_PER_PAGE).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub 返回状态码 " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        List<ReleaseInfo> releases = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                releases.add(toRelease(node));
            }
        }
        return releases;
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
