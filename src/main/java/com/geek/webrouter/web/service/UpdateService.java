package com.geek.webrouter.web.service;

import com.geek.webrouter.web.model.dto.UpdateApplyRequest;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import com.geek.webrouter.web.model.dto.UpdateCheckResult;

/**
 * 自动更新服务。
 *
 * <p>分两阶段，便于「无感更新」：</p>
 * <ol>
 *   <li>{@link #stage()} — 下载并校验更新包、生成更新脚本，**不退出进程**；</li>
 *   <li>{@link #requestExit()} — 在安全时机（无在途请求）安排退出，由脚本完成替换与重启。</li>
 * </ol>
 *
 * <p>{@link #apply(UpdateApplyRequest)} 是二者的组合，保留给手动「一键更新」。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
public interface UpdateService {

    /** 检查更新；任何异常都写进 message，不抛出。 */
    UpdateCheckResult check();

    /**
     * 下载更新包、校验摘要并生成更新脚本，但不退出进程。
     *
     * @return 已就绪的更新信息；无可用更新时返回 null
     */
    UpdateApplyResult stage();

    /** 安排进程退出以完成替换与重启。 */
    void requestExit();

    /** 下载更新包、校验摘要并生成更新脚本；不可更新时抛 BusinessException。 */
    UpdateApplyResult apply(UpdateApplyRequest request);
}
