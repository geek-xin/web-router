package com.geek.webrouter.config;

import java.net.URI;

final class ProxyAccessAddressFormatter {

    private ProxyAccessAddressFormatter() {
    }

    static String hostPort(URI uri) {
        if (uri == null) {
            return "-";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return uri.toString();
        }
        int port = uri.getPort();
        if (port < 0) {
            port = defaultPort(uri.getScheme());
        }
        return port < 0 ? host : host + ":" + port;
    }

    static String accessAddress(URI baseUri, String requestUri) {
        return accessAddress(hostPort(baseUri), requestUri);
    }

    static String accessAddress(String baseAddress, String requestUri) {
        if (baseAddress == null || baseAddress.isBlank() || "-".equals(baseAddress)) {
            return "-";
        }
        return baseAddress + normalizedRequestUri(requestUri);
    }

    static String requestUri(URI uri) {
        if (uri == null) {
            return "/";
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + query;
    }

    private static String normalizedRequestUri(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        String value = requestUri.trim();
        if (value.startsWith("?")) {
            return "/" + value;
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static int defaultPort(String scheme) {
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }
}
