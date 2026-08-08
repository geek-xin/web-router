package com.geek.webrouter.web.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 路由配置导入请求。
 */
@Data
public class RouteConfigImportRequest {

    private int version = 1;

    @Valid
    @NotNull(message = "导入文件缺少 routes")
    private List<RouteConfigDto> routes;
}
