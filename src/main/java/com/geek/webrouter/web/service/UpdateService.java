package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.UpdateApplyRequest;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import com.geek.webrouter.web.model.dto.UpdateCheckResult;

/**
 * 自动更新服务。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public interface UpdateService {

    /** 检查更新；任何异常都写进 message，不抛出。 */
    UpdateCheckResult check();

    /** 下载更新包、校验摘要并生成更新脚本；不可更新时抛 BusinessException。 */
    UpdateApplyResult apply(UpdateApplyRequest request);
}
