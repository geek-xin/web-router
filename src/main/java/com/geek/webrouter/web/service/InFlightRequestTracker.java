package com.geek.webrouter.web.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在途代理请求计数 — 判断「现在是否可以安全重启」。
 *
 * <p>Gateway 转发与本地端口代理在请求进入时 {@link #begin()}，结束时 {@link #end()}。
 * 自动更新只在计数归零后的一段时间内才执行替换与重启，避免掐断正在进行的代理请求。</p>
 *
 * <p>用 {@link AtomicInteger} 而非普通 int：两个代理路径分别在不同线程（Reactor 事件循环 /
 * Netty worker）上增减，且 {@code doFinally} 可能重复触发。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Service
public class InFlightRequestTracker {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong lastActivityMillis = new AtomicLong(System.currentTimeMillis());

    /** 请求开始，返回自增后的在途数。 */
    public int begin() {
        lastActivityMillis.set(System.currentTimeMillis());
        return inFlight.incrementAndGet();
    }

    /** 请求结束；计数不会降到 0 以下（防止 doFinally 重复调用把计数带负）。 */
    public int end() {
        lastActivityMillis.set(System.currentTimeMillis());
        while (true) {
            int current = inFlight.get();
            if (current <= 0) {
                inFlight.compareAndSet(current, 0);
                return 0;
            }
            if (inFlight.compareAndSet(current, current - 1)) {
                return current - 1;
            }
        }
    }

    /** 当前在途请求数。 */
    public int inFlightCount() {
        return Math.max(0, inFlight.get());
    }

    /** 距离最后一次代理活动经过的毫秒数。 */
    public long idleMillis() {
        return Math.max(0, System.currentTimeMillis() - lastActivityMillis.get());
    }

    /**
     * 是否处于可安全重启的空闲窗口。
     *
     * @param quietMillis 要求的最小静默时长（毫秒），0 表示只看在途计数
     */
    public boolean isIdle(long quietMillis) {
        return inFlightCount() == 0 && idleMillis() >= Math.max(0, quietMillis);
    }
}
