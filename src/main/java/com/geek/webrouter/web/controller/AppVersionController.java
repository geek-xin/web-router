package com.geek.webrouter.web.controller;

import com.geek.webrouter.common.result.Result;
import com.geek.webrouter.web.model.dto.AppVersionInfo;
import com.geek.webrouter.web.model.dto.AutoUpdateStatus;
import com.geek.webrouter.web.model.dto.UpdateApplyRequest;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import com.geek.webrouter.web.model.dto.UpdateCheckResult;
import com.geek.webrouter.web.service.AppVersionService;
import com.geek.webrouter.web.service.AutoUpdateService;
import com.geek.webrouter.web.service.UpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 版本信息与自动更新控制器。
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Controller
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionService appVersionService;
    private final UpdateService updateService;
    private final AutoUpdateService autoUpdateService;

    /** 当前应用版本信息。 */
    @GetMapping("/version")
    @ResponseBody
    public Result<AppVersionInfo> version() {
        return Result.success(appVersionService.currentVersion());
    }

    /** 检查更新；任何异常都会写进 message，接口本身始终 success=true。 */
    @GetMapping("/update/check")
    @ResponseBody
    public Result<UpdateCheckResult> check() {
        return Result.success(updateService.check());
    }

    /** 下载更新包并生成更新脚本；不可更新时抛 BusinessException。 */
    @PostMapping("/update/apply")
    @ResponseBody
    public Result<UpdateApplyResult> apply(@RequestBody(required = false) UpdateApplyRequest request) {
        return Result.success(updateService.apply(request));
    }

    /** 自动更新运行状态：开关、在途请求数、空闲判定与待应用版本。 */
    @GetMapping("/update/auto")
    @ResponseBody
    public Result<AutoUpdateStatus> autoStatus() {
        UpdateApplyResult pending = autoUpdateService.pendingUpdate();
        return Result.success(new AutoUpdateStatus(
                autoUpdateService.isAutoEnabled(),
                autoUpdateService.isAutoApplyEnabled(),
                autoUpdateService.quietSeconds(),
                autoUpdateService.inFlightCount(),
                autoUpdateService.idleMillis(),
                autoUpdateService.isIdle(),
                pending == null ? "" : pending.version(),
                pending == null ? "" : fileName(pending.archivePath())));
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
