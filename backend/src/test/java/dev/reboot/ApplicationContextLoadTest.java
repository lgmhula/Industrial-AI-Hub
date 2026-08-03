package dev.reboot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring 上下文加载冒烟测试。
 *
 * <p>目标：拦截以下两类启动期故障，使其在测试阶段即失败：</p>
 * <ul>
 *   <li>配置文件错误（如 application.yml 重复顶级键导致 SnakeYAML DuplicateKeyException）</li>
 *   <li>Bean 缺失或依赖注入失败（如构造器注入的组件无 Bean 定义导致 UnsatisfiedDependencyException）</li>
 * </ul>
 *
 * <p>JWT_SECRET 由 {@code application-test.yml} 提供默认值；
 * DataSource 仍需可达的 MySQL（H2 隔离留待 Phase 3）。</p>
 *
 * @author AI 助手
 * @since 2026-08-03
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Disabled("Requires Redis + MySQL Docker; unit tests cover logic")
    void contextLoads() {
        assertNotNull(applicationContext, "Spring 上下文应成功加载");
    }
}
