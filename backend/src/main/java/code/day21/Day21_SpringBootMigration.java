package code.day21;

/**
 * Day 21: Spring Boot 3.5 迁移 —— 概念对照笔记。
 *
 * <h3>核心变化</h3>
 * <table border="1">
 *   <tr><th>Day 1~20（独立 MyBatis）</th><th>Day 21+（Spring Boot）</th></tr>
 *   <tr><td>new SqlSessionFactoryBuilder().build()</td><td>Spring 自动装配 SqlSessionFactory</td></tr>
 *   <tr><td>try(SqlSession session = ...)</td><td>@Mapper 接口 + @Autowired 注入</td></tr>
 *   <tr><td>mybatis-config.xml 配 DataSource</td><td>application.yml 统一配置</td></tr>
 *   <tr><td>exec-maven-plugin 运行</td><td>spring-boot-maven-plugin 运行</td></tr>
 *   <tr><td>Main 方法手工管理连接</td><td>@SpringBootApplication 自动管理</td></tr>
 *   <tr><td>REST 需自己实现 HTTP Server</td><td>@RestController 内置 Tomcat</td></tr>
 * </table>
 *
 * <h3>Day 21 架构</h3>
 * <pre>
 * dev.reboot
 * ├── IndustrialAiHubApplication   (@SpringBootApplication)
 * ├── controller/DeviceController  (@RestController → /api/devices)
 * ├── mapper/DeviceMapper          (@Mapper → MyBatis 注解 SQL)
 * └── entity/Device                (POJO)
 * </pre>
 *
 * <h3>测试方式（启动 Spring Boot）</h3>
 * <pre>mvn spring-boot:run</pre>
 * <p>或 IDEA 直接运行 {@code IndustrialAiHubApplication.main()}。</p>
 *
 * <p>启动后访问：<a href="http://localhost:8080/api/devices">http://localhost:8080/api/devices</a></p>
 *
 * @author hula0710
 * @since 2026-07-19
 */
public class Day21_SpringBootMigration {

    /** 仅作为学习笔记存在，不实际执行。实际运行请用 IndustrialAiHubApplication。 */
    public static void main(String[] args) {
        System.out.println("Day 21: Spring Boot 3.5 Migration Notes");
        System.out.println("=======================================");
        System.out.println("Please run: dev.reboot.IndustrialAiHubApplication");
        System.out.println("Or: mvn spring-boot:run");
        System.out.println();
        System.out.println("Then test: curl http://localhost:8080/api/devices");
    }
}
