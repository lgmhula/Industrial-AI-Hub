package dev.reboot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * <p>注意：本测试加载完整上下文（含 DataSource），需要环境变量
 * {@code JWT_SECRET}（≥256bit）及可达的 MySQL（默认 127.0.0.1:3307）。</p>
 *
 * @author AI 助手
 * @since 2026-08-03
 */
@SpringBootTest
class ApplicationContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Spring 上下文应成功加载");
    }
}
