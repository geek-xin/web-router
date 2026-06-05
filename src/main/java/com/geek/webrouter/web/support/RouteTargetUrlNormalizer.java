package com.geek.webrouter.web.support;

/**
 * Normalizes route target addresses before persisting them.
 */
public final class RouteTargetUrlNormalizer {

    private RouteTargetUrlNormalizer() {
    }

    public static String normalize(String targetUrl) {
        String trimmed = targetUrl == null ? "" : targetUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "http://" + trimmed;
    }
}
