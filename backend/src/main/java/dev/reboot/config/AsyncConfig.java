package dev.reboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行配置（P1-02-A-5）—— 登录审计专用线程池。
 *
 * <p>审计写入为异步（{@code @Async("loginAuditExecutor")}），
 * 高并发登录/攻击流量下不阻塞认证主流程；审计失败不影响登录结果。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 登录审计异步线程池（独立隔离，避免与业务线程池互相影响）。 */
    @Bean(name = "loginAuditExecutor")
    public Executor loginAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("login-audit-");
        executor.initialize();
        return executor;
    }
}
