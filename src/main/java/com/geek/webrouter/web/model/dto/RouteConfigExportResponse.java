package com.geek.webrouter.web.model.dto;

import com.geek.webrouter.web.model.entity.RouteConfig;

import java.time.Instant;
import java.util.List;

/**
 * 路由配置导出响应。
 */
public record RouteConfigExportResponse(int version, Instant exportedAt, List<RouteConfig> routes) {
}
