package com.geek.webrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Web 路由代理启动类。
 *
 * <p>开启定时任务：自动更新调度器依赖它周期性检查更新，并在空闲窗口应用。</p>
 *
 * @author geek
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
