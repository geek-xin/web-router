package com.geek.webrouter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自动更新配置。
 *
 * <p>默认开启：应用会在后台静默检查并预下载更新包，等到没有在途代理请求时自动完成替换与重启，
 * 用户不需要点任何按钮。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wrouter.update")
public class UpdateProperties {

    /** 是否启用自动更新（后台检查 + 预下载 + 空闲时自动应用）。 */
    private boolean auto = true;

    /** 首次检查前的延迟（秒），避免与应用启动争抢资源。 */
    private long initialDelaySeconds = 120;

    /** 检查间隔（秒）。 */
    private long checkIntervalSeconds = 3600;

    /** 判定「空闲」所需的静默时长（秒）：期间没有任何代理请求才允许重启。 */
    private long quietSeconds = 30;

    /** 是否自动应用（false 时只下载并提示，等待用户手动确认）。 */
    private boolean autoApply = true;
}
