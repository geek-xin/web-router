package com.geek.webrouter.web.service;

import com.geek.webrouter.config.UpdateProperties;
import com.geek.webrouter.web.model.dto.UpdateApplyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 无感自动更新调度器。
 *
 * <p>把「手动点按钮」变成后台流程：</p>
 * <ol>
 *   <li>启动后延迟一段时间，之后按固定间隔静默检查更新；</li>
 *   <li>发现新版本就**预下载并校验**，此时用户完全无感，服务照常代理；</li>
 *   <li>持续等待，直到 {@link InFlightRequestTracker} 报告「没有在途请求且静默足够久」，
 *       才真正退出进程交给更新脚本替换与重启。</li>
 * </ol>
 *
 * <p>这样重启只会落在流量空档，不会掐断正在进行的代理请求。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@Service
public class AutoUpdateService {

    private final UpdateService updateService;
    private final InFlightRequestTracker inFlightTracker;
    private final UpdateProperties properties;

    /** 已暂存待应用的更新；非空表示「包已就绪，等空闲」。 */
    private final AtomicReference<UpdateApplyResult> pending = new AtomicReference<>();

    /** 防止检查任务重入（下载可能耗时较久）。 */
    private final AtomicBoolean checking = new AtomicBoolean();

    /** 是否已经安排退出，避免重复触发。 */
    private final AtomicBoolean exitScheduled = new AtomicBoolean();

    @Autowired
    public AutoUpdateService(UpdateService updateService, InFlightRequestTracker inFlightTracker,
                             UpdateProperties properties) {
        this.updateService = updateService;
        this.inFlightTracker = inFlightTracker;
        this.properties = properties;
    }

    /** 启动完成后打印一次策略，便于排障。 */
    @EventListener(ApplicationReadyEvent.class)
    public void announcePolicy() {
        if (!properties.isAuto()) {
            log.info("自动更新已关闭（wrouter.update.auto=false），仅支持手动更新");
            return;
        }
        log.info("自动更新已启用：每 {} 秒检查一次，静默 {} 秒且无在途请求时自动应用{}",
                properties.getCheckIntervalSeconds(), properties.getQuietSeconds(),
                properties.isAutoApply() ? "" : "（当前为仅下载模式）");
    }

    /**
     * 周期性检查并暂存更新。
     *
     * <p>首次执行延迟 {@code initialDelaySeconds}，避免与应用启动争抢资源。</p>
     */
    @Scheduled(initialDelayString = "${wrouter.update.initial-delay-seconds:120}000",
            fixedDelayString = "${wrouter.update.check-interval-seconds:3600}000")
    public void checkAndStage() {
        if (!properties.isAuto()) {
            return;
        }
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            UpdateApplyResult staged = updateService.stage();
            if (staged != null) {
                UpdateApplyResult previous = pending.getAndSet(staged);
                if (previous == null || !staged.version().equals(previous.version())) {
                    log.info("已暂存新版本 {}，等待空闲窗口自动应用", staged.version());
                }
            }
        } catch (RuntimeException e) {
            // 自动流程绝不能把异常抛给调度线程：下一轮会重试
            log.warn("自动检查更新失败，将在下一轮重试: {}", e.getMessage());
        } finally {
            checking.set(false);
        }
    }

    /**
     * 高频检查是否满足应用条件。
     *
     * <p>间隔固定为 5 秒，比检查间隔密得多，这样流量一空下来就能立刻应用。</p>
     */
    @Scheduled(fixedDelayString = "5000")
    public void applyWhenIdle() {
        if (!properties.isAuto() || !properties.isAutoApply()) {
            return;
        }
        UpdateApplyResult staged = pending.get();
        if (staged == null || exitScheduled.get()) {
            return;
        }
        long quietMillis = Math.max(0, properties.getQuietSeconds()) * 1000L;
        if (!inFlightTracker.isIdle(quietMillis)) {
            return;
        }
        if (!exitScheduled.compareAndSet(false, true)) {
            return;
        }
        log.info("进入空闲窗口（在途请求 0，静默 {} ms），开始应用版本 {} 并重启",
                inFlightTracker.idleMillis(), staged.version());
        updateService.requestExit();
    }

    /** 已暂存待应用的更新；无更新时为 null。 */
    public UpdateApplyResult pendingUpdate() {
        return pending.get();
    }

    /** 当前在途代理请求数。 */
    public int inFlightCount() {
        return inFlightTracker.inFlightCount();
    }

    /** 距离最后一次代理活动的毫秒数。 */
    public long idleMillis() {
        return inFlightTracker.idleMillis();
    }

    /** 是否处于可应用更新的空闲窗口。 */
    public boolean isIdle() {
        return inFlightTracker.isIdle(Math.max(0, properties.getQuietSeconds()) * 1000L);
    }

    /** 自动更新是否启用。 */
    public boolean isAutoEnabled() {
        return properties.isAuto();
    }

    /** 是否允许自动应用（false 表示只预下载，等用户手动确认）。 */
    public boolean isAutoApplyEnabled() {
        return properties.isAutoApply();
    }

    /** 判定空闲所需的静默秒数。 */
    public long quietSeconds() {
        return Math.max(0, properties.getQuietSeconds());
    }
}
