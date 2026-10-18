package dev.reboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * SSE 首帧调度配置。
 *
 * <p>Controller 返回 {@code SseEmitter} 后，Spring MVC 才会把请求切换到异步模式。
 * 首帧 comment 必须在该切换完成后发送，否则响应头和字节可能一直停留在缓冲区。
 * 使用独立单线程调度器，避免占用业务线程池或登录审计线程池。</p>
 */
@Configuration
@Profile("!test")
public class SseEmitterConfig {

    @Bean(name = "sseFirstFrameScheduler")
    public ThreadPoolTaskScheduler sseFirstFrameScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("sse-first-frame-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
