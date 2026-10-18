package com.geek.webrouter.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GitHub 客户端的重试契约：瞬时连接故障要重试，确定性错误不重试。
 */
class HttpReleaseClientTest {

    /** 连续失败指定次数后成功的调用，用于验证重试次数。 */
    private static <T> T retryProbe(HttpReleaseClient client, AtomicInteger calls, int failTimes,
                                    T successValue) throws Exception {
        return client.withRetry("测试", () -> {
            if (calls.incrementAndGet() <= failTimes) {
                throw new ConnectException();
            }
            return successValue;
        });
    }

    private static HttpReleaseClient client() {
        return new HttpReleaseClient(new ObjectMapper(), HttpClient.newHttpClient());
    }

    @Test
    void retriesTransientConnectFailuresAndEventuallySucceeds() throws Exception {
        HttpReleaseClient client = client();
        AtomicInteger calls = new AtomicInteger();

        String result = retryProbe(client, calls, 2, "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxAttemptsAndPropagatesTheFailure() {
        HttpReleaseClient client = client();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retryProbe(client, calls, 99, "never"))
                .isInstanceOf(ConnectException.class);
        // 最多尝试 3 次，不会无限重试
        assertThat(calls.get()).isEqualTo(3);
    }

    /** HTTP 状态码错误是确定性结果，重试只会浪费 GitHub 配额。 */
    @Test
    void doesNotRetryDeterministicErrors() {
        HttpReleaseClient client = client();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> client.withRetry("测试", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("GitHub 返回状态码 403");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    /**
     * 关键：{@code listReleases()} 本身必须走重试路径，而不只是 {@code withRetry} 单独可用。
     * 否则「读取历史版本失败」仍会在用户侧随机出现。
     */
    @Test
    void listReleasesRetriesThroughItsPublicEntryPoint() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException())
                .thenThrow(new ConnectException())
                .thenReturn(jsonResponse("[]"));
        HttpReleaseClient client = new HttpReleaseClient(new ObjectMapper(), http);

        assertThat(client.listReleases("geek-xin/web-router")).isEmpty();
        // 前两次失败被重试，第三次成功 => 共调用 3 次
        verify(http, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /** 三次都失败时抛出，且不再继续尝试。 */
    @Test
    void listReleasesGivesUpAfterThreeFailedAttempts() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new ConnectException());
        HttpReleaseClient client = new HttpReleaseClient(new ObjectMapper(), http);

        assertThatThrownBy(() -> client.listReleases("geek-xin/web-router"))
                .isInstanceOf(ConnectException.class);
        verify(http, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }


    /**
     * 403 = GitHub API 限流耗尽（未认证每小时 60 次）。
     * 必须给出**可操作**的说明（何时恢复 + 如何提升限额），
     * 而不是只报「GitHub 返回状态码 403（IllegalStateException）」。
     */
    @Test
    void explainsRateLimitExhaustionInsteadOfBareStatusCode() throws Exception {
        HttpResponse<String> response = rateLimitedResponse();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        HttpReleaseClient client = new HttpReleaseClient(new ObjectMapper(), http);

        assertThatThrownBy(() -> client.listReleases("geek-xin/web-router"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("限流")
                .hasMessageContaining("WROUTER_GITHUB_TOKEN")
                .hasMessageContaining("恢复");
    }

    /** 403 是确定性结果，重试只会继续消耗配额。 */
    @Test
    void doesNotRetryRateLimitedRequests() throws Exception {
        HttpResponse<String> response = rateLimitedResponse();
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        HttpReleaseClient client = new HttpReleaseClient(new ObjectMapper(), http);

        assertThatThrownBy(() -> client.listReleases("geek-xin/web-router"))
                .isInstanceOf(IllegalStateException.class);
        // 只调用一次，不重试（否则继续烧配额）
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /** 404 同样给可操作提示，而不是裸状态码。 */
    @Test
    void explainsNotFoundWithRepositoryHint() throws Exception {
        HttpResponse<String> response = statusResponse(404, headers(java.util.Map.of()));
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        HttpReleaseClient client = new HttpReleaseClient(new ObjectMapper(), http);

        assertThatThrownBy(() -> client.listReleases("geek-xin/web-router"))
                .hasMessageContaining("app.repository");
    }

    /** 构造一个带限流响应头的 403。 */
    private static HttpResponse<String> rateLimitedResponse() {
        long resetAt = System.currentTimeMillis() / 1000 + 600;
        return statusResponse(403, headers(java.util.Map.of(
                "x-ratelimit-remaining", java.util.List.of("0"),
                "x-ratelimit-reset", java.util.List.of(String.valueOf(resetAt)))));
    }

    private static java.net.http.HttpHeaders headers(java.util.Map<String, java.util.List<String>> values) {
        return java.net.http.HttpHeaders.of(values, (a, b) -> true);
    }

    /** 构造指定状态码与响应头的响应；必须在 mock 之后单独 stub，避免嵌套 stubbing。 */
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> statusResponse(int status, java.net.http.HttpHeaders headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{}");
        when(response.headers()).thenReturn(headers);
        return response;
    }
    @SuppressWarnings("unchecked")
    private static HttpResponse<String> jsonResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void succeedsOnFirstAttemptWithoutRetrying() throws Exception {
        HttpReleaseClient client = client();
        AtomicInteger calls = new AtomicInteger();

        assertThat(retryProbe(client, calls, 0, "first")).isEqualTo("first");
        assertThat(calls.get()).isEqualTo(1);
    }

    /** ConnectException 的 message 常为 null，描述必须回退到异常链。 */
    @Test
    void describesNullMessagedFailuresViaTheirCauseChain() {
        Exception wrapped = new IOException("连接失败", new ConnectException());

        String described = HttpReleaseClient.describe(wrapped);

        assertThat(described).contains("IOException").contains("ConnectException");
    }

    @Test
    void describesPlainExceptionsByType() {
        assertThat(HttpReleaseClient.describe(new ConnectException())).isEqualTo("ConnectException");
    }
}
