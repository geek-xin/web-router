package com.geek.webrouter.web.model.dto;

import com.geek.webrouter.web.model.entity.RouteConfig;

import java.util.List;

/**
 * 路由配置导入响应。
 */
public record RouteConfigImportResponse(int importedCount, List<RouteConfig> routes) {
}
