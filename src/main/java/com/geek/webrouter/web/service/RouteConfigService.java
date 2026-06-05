package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.entity.RouteConfig;
import java.util.List;

/**
 * 路由配置服务接口 — 基于本地 JSON 文件的持久化管理。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public interface RouteConfigService {

    /**
     * 加载全部路由配置。
     *
     * @return 路由配置列表
     */
    List<RouteConfig> listAll();

    /**
     * 根据名称获取路由配置。
     *
     * @param name 路由名称
     * @return 路由配置
     */
    RouteConfig getByName(String name);

    /**
     * 新增路由配置。
     *
     * @param config 路由配置
     * @return 保存后的配置
     */
    RouteConfig create(RouteConfig config);

    /**
     * 更新路由配置。
     *
     * @param name   路由名称
     * @param config 新配置
     * @return 更新后的配置
     */
    RouteConfig update(String name, RouteConfig config);

    /**
     * 删除路由配置。
     *
     * @param name 路由名称
     */
    void delete(String name);

    /**
     * 初始化示例配置（仅在目录为空时生效）。
     */
    void initDefaultConfigs();
}
