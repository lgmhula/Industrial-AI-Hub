package dev.reboot.db;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 契约测试（源码级）：演示/测试种子数据不得进入生产 Flyway 迁移链（ADR 0019）。
 *
 * <p>为什么是源码级：正式迁移 V1/V3 使用 MySQL 专有语法（如 V1 各表同名索引、
 * V3 的 {@code ALTER TABLE ... DROP CHECK}），H2 无法执行整条链路，
 * 运行时验证由 {@link MySqlSeedIsolationIT}（RUN_MYSQL_IT=true 时对真实 MySQL 执行）承担。
 * 本测试在任何机器/CI 上零外部依赖运行（ADR 0018），锁定以下契约：</p>
 * <ol>
 *   <li>db/migration 只允许存在正式迁移（V1 baseline + V3 check 扩展），不得再有种子迁移；</li>
 *   <li>迁移文件内容不得包含演示数据标记（operator01 / TEMP-001 等）；</li>
 *   <li>演示种子唯一事实源位于 db/seed/dev/，在 Flyway locations 之外，不会被自动扫描；</li>
 *   <li>application.yml Flyway 配置：locations 仅 classpath:db/migration、baseline-version=2、
 *       ignore-migration-patterns 容忍已退役的 V2（missing）；prod/dev profile 不覆盖 Flyway 配置。</li>
 * </ol>
 */
class FlywayProductionSeedIsolationTest {

    private static final List<String> DEMO_MARKERS = Arrays.asList(
            "operator01", "TEMP-001", "13800001001", "Test123456"
    );

    /** 契约 1：迁移目录只允许正式迁移（V1/V3/V4；演示种子已移出，V2 退役）。 */
    @Test
    void migrationDirectory_containsOnlyFormalMigrations() throws IOException {
        Path migrationDir = new ClassPathResource("db/migration").getFile().toPath();
        try (Stream<Path> files = Files.list(migrationDir)) {
            List<String> names = files
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
            assertEquals(
                    List.of("V1__baseline.sql",
                            "V3__operation_log_check_types.sql",
                            "V4__add_site_scoping.sql"),
                    names,
                    "db/migration 只允许正式迁移；演示种子已移出（V2__seed_test_data.sql 退役）"
            );
        }
    }

    /** 契约 2：正式迁移文件内容不得包含演示数据标记。 */
    @Test
    void migrationFiles_containNoDemoSeedMarkers() throws IOException {
        Path migrationDir = new ClassPathResource("db/migration").getFile().toPath();
        try (Stream<Path> files = Files.list(migrationDir)) {
            List<Path> sqlFiles = files.filter(Files::isRegularFile).sorted().toList();
            assertFalse(sqlFiles.isEmpty(), "迁移目录不应为空");
            for (Path f : sqlFiles) {
                String content = Files.readString(f, StandardCharsets.UTF_8);
                for (String marker : DEMO_MARKERS) {
                    assertFalse(
                            content.contains(marker),
                            () -> "迁移文件 " + f.getFileName() + " 不应包含演示数据标记: " + marker
                    );
                }
            }
        }
    }

    /** 契约 3：演示种子文件位于 Flyway locations 之外（db/seed/dev/），且迁移目录内无 seed 文件。 */
    @Test
    void seedFile_livesOutsideFlywayMigrationLocation() throws IOException {
        ClassPathResource seed = new ClassPathResource("db/seed/dev/seed_demo_data.sql");
        assertTrue(seed.exists(), "演示种子唯一事实源应存在: db/seed/dev/seed_demo_data.sql");

        Path migrationDir = new ClassPathResource("db/migration").getFile().toPath();
        try (Stream<Path> files = Files.list(migrationDir)) {
            boolean hasSeedInMigrationDir = files
                    .map(p -> p.getFileName().toString())
                    .anyMatch(name -> name.toLowerCase().contains("seed"));
            assertFalse(hasSeedInMigrationDir, "迁移目录不得包含任何 seed 文件（会被 Flyway 自动扫描）");
        }
    }

    /** 契约 4：application.yml Flyway 配置——locations 不含 seed 目录、容忍已退役 V2（missing）。 */
    @Test
    void flywayConfig_isolatesDemoSeed() throws IOException {
        // YamlPropertySourceLoader 输出扁平化点号键（spring.flyway.*）
        PropertySource<?> ps = loadYaml("application.yml");
        assertEquals("classpath:db/migration", ps.getProperty("spring.flyway.locations"),
                "Flyway locations 只能指向正式迁移目录");
        assertEquals("2", String.valueOf(ps.getProperty("spring.flyway.baseline-version")),
                "既有库基线版本保持 2（V2 退役后等价于跳过 V1 重放）");
        assertEquals(Boolean.TRUE, ps.getProperty("spring.flyway.baseline-on-migrate"));
        assertTrue(String.valueOf(ps.getProperty("spring.flyway.ignore-migration-patterns")).contains("missing"),
                "必须配置 ignore-migration-patterns 容忍已退役 V2（missing），兼容已执行过旧 V2 的既有库");

        // prod / dev profile 不得另行覆盖 Flyway 配置（避免任何 profile 把 seed 目录引入迁移链）
        for (String profileYml : List.of("application-prod.yml", "application-dev.yml")) {
            String content = readText(profileYml);
            assertFalse(
                    content.toLowerCase().contains("flyway"),
                    () -> profileYml + " 不应覆盖 Flyway 配置（seed 隔离只由 application.yml 统一定义）"
            );
        }
    }

    private PropertySource<?> loadYaml(String location) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(location, new ClassPathResource(location));
        assertFalse(sources.isEmpty(), "YAML 应为非空: " + location);
        return sources.get(0);
    }

    private String readText(String location) throws IOException {
        return StreamUtils.copyToString(
                new EncodedResource(new ClassPathResource(location), StandardCharsets.UTF_8).getInputStream(),
                StandardCharsets.UTF_8
        );
    }
}
