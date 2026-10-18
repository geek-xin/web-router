package com.geek.webrouter.web.controller;

import com.geek.webrouter.common.result.Result;
import com.geek.webrouter.web.model.dto.AppVersionInfo;
import com.geek.webrouter.web.model.dto.AutoUpdateStatus;
import com.geek.webrouter.web.model.dto.ReleaseHistory;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public Mono<Result<UpdateCheckResult>> check() {
        return offload(updateService::check);
    }

    /** 历史版本与修订记录；读取失败时列表为空并把原因写进 message，接口本身始终成功。 */
    @GetMapping("/update/history")
    @ResponseBody
    public Mono<Result<ReleaseHistory>> history() {
        return offload(updateService::history);
    }

    /** 下载更新包并生成更新脚本；不可更新时抛 BusinessException。 */
    @PostMapping("/update/apply")
    @ResponseBody
    public Mono<Result<UpdateApplyResult>> apply(@RequestBody(required = false) UpdateApplyRequest request) {
        return offload(() -> updateService.apply(request));
    }

    /**
     * 把阻塞式 GitHub 调用移出事件循环。
     *
     * <p>本应用是 WebFlux（Reactor Netty），事件循环线程数 = CPU 核数（本机 12）。
     * {@code HttpReleaseClient} 用的是阻塞式 {@code HttpClient.send()}，
     * 若直接在控制器里调用，就会占满事件循环：实测 12 个并发阻塞调用会让
     * 纯内存接口 {@code /admin/api/version} 的响应从 0.9ms 恶化到 537ms，
     * 并且在网络抖动时直接抛 {@code ConnectException}（读取历史版本失败）。
     * 因此统一切到 {@code boundedElastic} 弹性线程池执行，事件循环只负责编排。</p>
     *
     * <p>与 {@code DynamicRouteService} / {@code LocalPortProxyService} 的处理方式一致。</p>
     */
    private static <T> Mono<Result<T>> offload(java.util.concurrent.Callable<T> blockingCall) {
        return Mono.fromCallable(blockingCall)
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
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
