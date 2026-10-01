package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.AppVersionInfo;

/**
 * 应用版本信息服务。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public interface AppVersionService {

    /** 读取当前应用版本信息。 */
    AppVersionInfo currentVersion();
}
