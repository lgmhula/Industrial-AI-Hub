package dev.reboot.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 端到端验证（默认跳过；显式执行需：
 * {@code RUN_MYSQL_IT=true MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_PASSWORD=... ./mvnw test -Dtest=MySqlSeedIsolationIT}）。
 *
 * <p>覆盖 ADR 0019「演示种子与生产 Flyway 隔离」要求的四种数据库场景：</p>
 * <ol>
 *   <li>全新生产库：Flyway 只执行 V1+V3，无任何 Demo 用户/设备/告警；</li>
 *   <li>全新开发库：正式迁移后显式执行 dev seed，Demo 数据生成且幂等；</li>
 *   <li>已有开发库（无 flyway 历史）：baseline@2 跳过 V1，业务数据保留，不产生 Demo；</li>
 *   <li>已有执行过旧 V2 的库：V2 文件退役后，靠 ignore-migration-patterns 平滑升级，
 *       Demo 数据保留、不重复、不删除（证明该配置的必要性）。</li>
 * </ol>
 *
 * <p>每个场景使用独立的临时库（reboot_it_*），结束后自动删除，不影响开发库。</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class MySqlSeedIsolationIT {

    private static final String HOST = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
    private static final String PORT = System.getenv().getOrDefault("MYSQL_PORT", "3307");
    private static final String USER = System.getenv().getOrDefault("MYSQL_USER", "root");

    private static final String PASSWORD = resolvePassword();

    private static String resolvePassword() {
        String p = System.getenv("MYSQL_PASSWORD");
        if (p == null || p.isBlank()) {
            p = System.getenv("MYSQL_ROOT_PASSWORD");
        }
        if (p == null || p.isBlank()) {
            throw new IllegalStateException(
                    "RUN_MYSQL_IT=true 需要提供 MYSQL_PASSWORD 或 MYSQL_ROOT_PASSWORD 环境变量");
        }
        return p;
    }

    private static final List<String> CREATED_DBS = new ArrayList<>();

    private String serverUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private String dbUrl(String db) {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    /** 镜像 application.yml 的 Flyway 生产配置（locations / baseline / ignore-migration-patterns）。 */
    private Flyway prodFlyway(String db) {
        return Flyway.configure()
                .dataSource(dbUrl(db), USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("2")
                .ignoreMigrationPatterns("*:missing")
                .load();
    }

    private String createScratchDb(String prefix) throws SQLException {
        String name = prefix + "_" + System.nanoTime();
        try (Connection c = DriverManager.getConnection(serverUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        CREATED_DBS.add(name);
        return name;
    }

    private void dropDb(String db) {
        try (Connection c = DriverManager.getConnection(serverUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + db + "`");
        } catch (SQLException e) {
            throw new RuntimeException("清理临时库失败: " + db, e);
        }
    }

    // ==================================================================
    // 场景 1：全新生产数据库
    // ==================================================================
    @Test
    void freshProductionDatabase_migratesWithoutDemoData() throws Exception {
        String db = createScratchDb("reboot_it_prod");
        try {
            Flyway fw = prodFlyway(db);
            fw.migrate();

            assertEquals(List.of("1", "3", "4", "5", "6"), appliedVersions(fw),
                    "全新库只执行 V1 + V3 + V4 + V5 + V6（V2 已退役；V6 登录审计）");
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `user`"), "只有 admin，无 Demo 用户");
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM device"), "无 Demo 设备");
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM alarm"), "无 Demo 告警");
            assertFalse(exists(db, "SELECT 1 FROM `user` WHERE username = 'operator01'"), "不得出现 Demo 用户");
            assertFalse(exists(db, "SELECT 1 FROM device WHERE device_code = 'TEMP-001'"), "不得出现 Demo 设备");

            // 必需系统初始化仍保留（V1：角色 + admin）
            assertEquals(3L, scalar(db, "SELECT COUNT(*) FROM role"), "默认角色仍在");
            assertTrue(exists(db, "SELECT 1 FROM `user` WHERE username = 'admin'"), "admin 账户仍在");
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 2：全新开发数据库（迁移 + 显式 dev seed，幂等）
    // ==================================================================
    @Test
    void freshDevelopmentDatabase_seedLoadsAndIsIdempotent() throws Exception {
        String db = createScratchDb("reboot_it_dev");
        try {
            prodFlyway(db).migrate();
            try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD)) {
                ScriptUtils.executeSqlScript(c, utf8("db/seed/dev/seed_demo_data.sql"));
                long[] first = counts(db);
                assertEquals(21L, first[0]);
                assertEquals(50L, first[1]);
                assertEquals(12L, first[2]);
                assertEquals(78L, first[3]);
                assertEquals(7L, first[4]);

                // Test C：幂等
                ScriptUtils.executeSqlScript(c, utf8("db/seed/dev/seed_demo_data.sql"));
                assertArrayEquals(first, counts(db), "seed 二次执行不得产生重复数据");

                // P1-01：站点成员幂等分配（20 个演示用户 → 默认站点）
                assertEquals(20L, scalar(db, "SELECT COUNT(*) FROM user_site"), "user_site 应为 20（不重复）");
                assertTrue(exists(db, """
                        SELECT 1 FROM user_site us
                        JOIN `user` u ON u.id = us.user_id
                        JOIN role r ON r.id = us.role_id
                        JOIN site s ON s.id = us.site_id
                        WHERE u.username = 'operator01' AND r.role_code = 'OPERATOR' AND s.site_code = 'DEFAULT'"""),
                        "operator01 应为默认站点 OPERATOR");
                assertTrue(exists(db, """
                        SELECT 1 FROM user_site us
                        JOIN `user` u ON u.id = us.user_id
                        JOIN role r ON r.id = us.role_id
                        JOIN site s ON s.id = us.site_id
                        WHERE u.username = 'viewer01' AND r.role_code = 'VIEWER' AND s.site_code = 'DEFAULT'"""),
                        "viewer01 应为默认站点 VIEWER");
            }
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 3：已有开发数据库（非空、无 flyway 历史 → baseline@2，跳过 V1）
    // ==================================================================
    @Test
    void existingDatabaseWithoutHistory_baselinesCleanly() throws Exception {
        String db = createScratchDb("reboot_it_existing");
        try {
            // 模拟既有库：schema 已存在（V1 内容），含业务数据，无 flyway_schema_history
            try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD)) {
                ScriptUtils.executeSqlScript(c, utf8("db/migration/V1__baseline.sql"));
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("""
                            INSERT INTO `user` (`username`, `password`, `email`, `status`)
                            VALUES ('legacy_user', 'x', 'legacy@dev.local', 1)""");
                }
            }

            Flyway fw = prodFlyway(db);
            fw.migrate();

            List<String> applied = appliedEntries(fw);
            assertTrue(applied.contains("BASELINE@2"), "既有库应基线到 2（跳过 V1/V2 重放）: " + applied);
            assertTrue(applied.contains("SQL@3"), "V3 应被应用: " + applied);
            assertFalse(applied.contains("SQL@2"), "不得应用 V2（已退役）: " + applied);

            // 既有数据保留、不产生 Demo、无重复
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM `user`"), "admin + legacy_user");
            assertTrue(exists(db, "SELECT 1 FROM `user` WHERE username = 'legacy_user'"), "既有用户保留");
            assertFalse(exists(db, "SELECT 1 FROM `user` WHERE username = 'operator01'"), "不得灌入 Demo 用户");
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM device"), "不得灌入 Demo 设备");
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 4：已执行过旧 V2 的库（V2 文件退役后平滑升级）
    // ==================================================================
    @Test
    void existingDatabaseWithOldV2History_upgradesCleanly() throws Exception {
        String db = createScratchDb("reboot_it_legacy");
        Path oldChain = Files.createTempDirectory("old-flyway-chain");
        try {
            // 构造旧链路：V1 + V2(演示种子内容) + V3 —— 模拟旧版本（V2 在迁移链内）执行过的库
            copyTo("db/migration/V1__baseline.sql", oldChain.resolve("V1__baseline.sql"));
            Files.write(oldChain.resolve("V2__seed_test_data.sql"), seedProxyForOldChain());
            copyTo("db/migration/V3__operation_log_check_types.sql", oldChain.resolve("V3__operation_log_check_types.sql"));
            Flyway oldChainFlyway = Flyway.configure()
                    .dataSource(dbUrl(db), USER, PASSWORD)
                    .locations("filesystem:" + oldChain)
                    .load();
            oldChainFlyway.migrate();
            assertEquals(21L, scalar(db, "SELECT COUNT(*) FROM `user`"), "旧链路已灌入 admin + 20 个 Demo 用户");

            // 若无 ignore-migration-patterns，V2 文件缺失会使 validate 失败 —— 证明该配置的必要性
            Flyway strict = Flyway.configure()
                    .dataSource(dbUrl(db), USER, PASSWORD)
                    .locations("classpath:db/migration")
                    .load();
            assertThrows(FlywayValidateException.class, strict::validate,
                    "无 ignore-migration-patterns 时应因 V2 missing 校验失败");

            // 新链路（生产配置）：迁移成功，Demo 数据保留、不重复、不删除
            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedEntries(fw).contains("SQL@2"), "V2 历史行保留（不再执行，仅被容忍）");
            assertEquals(21L, scalar(db, "SELECT COUNT(*) FROM `user`"), "Demo 用户不重复");
            assertEquals(50L, scalar(db, "SELECT COUNT(*) FROM device"), "Demo 设备不重复");
            assertEquals(12L, scalar(db, "SELECT COUNT(*) FROM alarm"), "Demo 告警不重复");
            assertTrue(exists(db, "SELECT 1 FROM `user` WHERE username = 'operator01'"), "既有 Demo 数据保留");
        } finally {
            dropDb(db);
            // 清理临时目录
            try (var walk = Files.walk(oldChain)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ---- helpers ----

    private static EncodedResource utf8(String location) {
        return new EncodedResource(new ClassPathResource(location), StandardCharsets.UTF_8);
    }

    /** 旧链 V2 代理：取当前 seed 但截断 user_site 段（历史 V2 无站点段；user_site 由 V4 建立）。 */
    private static byte[] seedProxyForOldChain() throws Exception {
        String content = org.springframework.util.StreamUtils.copyToString(
                new EncodedResource(new ClassPathResource("db/seed/dev/seed_demo_data.sql"),
                        StandardCharsets.UTF_8).getInputStream(), StandardCharsets.UTF_8);
        int idx = content.indexOf("7. 站点成员分配");
        if (idx > 0) {
            content = content.substring(0, idx);
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static void copyTo(String classpath, Path target) throws Exception {
        Files.copy(new ClassPathResource(classpath).getInputStream(), target);
    }

    private List<String> appliedVersions(Flyway fw) {
        List<String> versions = new ArrayList<>();
        for (MigrationInfo info : fw.info().applied()) {
            if (info.getVersion() != null) {
                versions.add(info.getVersion().getVersion());
            }
        }
        return versions;
    }

    private List<String> appliedEntries(Flyway fw) {
        List<String> entries = new ArrayList<>();
        for (MigrationInfo info : fw.info().applied()) {
            entries.add(info.getType().name() + "@" + (info.getVersion() == null ? "?" : info.getVersion().getVersion()));
        }
        return entries;
    }

    private long[] counts(String db) throws SQLException {
        return new long[]{
                scalar(db, "SELECT COUNT(*) FROM `user`"),
                scalar(db, "SELECT COUNT(*) FROM device"),
                scalar(db, "SELECT COUNT(*) FROM alarm"),
                scalar(db, "SELECT COUNT(*) FROM device_data"),
                scalar(db, "SELECT COUNT(*) FROM operation_log")
        };
    }

    private long scalar(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查询应有结果: " + sql);
            return rs.getLong(1);
        }
    }

    private boolean exists(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }
}
