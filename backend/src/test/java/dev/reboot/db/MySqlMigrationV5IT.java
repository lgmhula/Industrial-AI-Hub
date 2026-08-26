package dev.reboot.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.StreamUtils;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5 用户安全状态迁移的 MySQL 端到端验证（默认跳过；显式执行：
 * {@code RUN_MYSQL_IT=true MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_PASSWORD=... ./mvnw test -Dtest=MySqlMigrationV5IT}）。
 *
 * <p>只验证迁移本身：</p>
 * <ol>
 *   <li>全新库：V1→V5 全部成功，user 新增 failed_attempts/locked_until/password_changed_at，
 *       默认值正确（0 / NULL / NULL）；</li>
 *   <li>已有 V1-V4 history 的库：增量执行 V5 成功，存量用户默认值回填。</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class MySqlMigrationV5IT {

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
    // 场景 1：全新数据库 → V1..V5 全部成功，默认值正确
    // ==================================================================
    @Test
    void freshDatabase_migratesV1ToV5() throws Exception {
        String db = createScratchDb("reboot_v5_fresh");
        try {
            Flyway fw = prodFlyway(db);
            fw.migrate();
            List<String> applied = appliedVersions(fw);
            assertTrue(applied.containsAll(List.of("1", "3", "4", "5")), "全新库应执行 V1/V3/V4/V5: " + applied);

            // user 安全状态列存在且默认值正确
            assertFalse(columnNullable(db, "user", "failed_attempts"), "failed_attempts 应为 NOT NULL");
            assertTrue(columnNullable(db, "user", "locked_until"), "locked_until 应为 NULL 可空");
            assertTrue(columnNullable(db, "user", "password_changed_at"), "password_changed_at 应为 NULL 可空");

            // 插入用户 → 默认 failed_attempts=0, locked_until=NULL
            try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
                 Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO `user` (username, password, status) VALUES ('fresh_user', 'x', 1)");
            }
            assertEquals(0L, scalar(db,
                    "SELECT failed_attempts FROM `user` WHERE username = 'fresh_user'"), "默认失败次数应为 0");
            assertTrue(exists(db, "SELECT 1 FROM `user` WHERE username = 'fresh_user' AND locked_until IS NULL"),
                    "默认 locked_until 应为 NULL");
            assertTrue(exists(db, "SELECT 1 FROM `user` WHERE username = 'fresh_user' AND password_changed_at IS NULL"),
                    "默认 password_changed_at 应为 NULL");
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 2：已有 V1-V4 history 的库 → 增量执行 V5，存量用户默认值回填
    // ==================================================================
    @Test
    void existingDatabaseWithV1ToV4History_migratesV5() throws Exception {
        String db = createScratchDb("reboot_v5_existing");
        Path oldChain = Files.createTempDirectory("old-chain-v5");
        try {
            // 旧链路 = 当前链去掉 V5（V1 + V3 + V4；V2 已退役）
            copyTo("db/migration/V1__baseline.sql", oldChain.resolve("V1__baseline.sql"));
            copyTo("db/migration/V3__operation_log_check_types.sql", oldChain.resolve("V3__operation_log_check_types.sql"));
            copyTo("db/migration/V4__add_site_scoping.sql", oldChain.resolve("V4__add_site_scoping.sql"));
            Flyway oldChainFlyway = Flyway.configure()
                    .dataSource(dbUrl(db), USER, PASSWORD)
                    .locations("filesystem:" + oldChain)
                    .load();
            oldChainFlyway.migrate();

            // 旧库无新列；插入存量用户
            try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
                 Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO `user` (username, password, status) VALUES ('legacy_user', 'x', 1)");
            }
            assertFalse(columnExists(db, "user", "failed_attempts"), "旧库不应有 failed_attempts 列");

            // 新链路 → V5 增量应用
            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedVersions(fw).contains("5"), "V5 应被应用");

            assertTrue(columnExists(db, "user", "failed_attempts"), "failed_attempts 列应存在");
            assertEquals(0L, scalar(db,
                    "SELECT failed_attempts FROM `user` WHERE username = 'legacy_user'"), "存量用户默认失败次数应为 0");
            assertTrue(exists(db, "SELECT 1 FROM `user` WHERE username = 'legacy_user' AND locked_until IS NULL"),
                    "存量用户默认 locked_until 应为 NULL");
        } finally {
            dropDb(db);
            try (var walk = Files.walk(oldChain)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ---- helpers ----

    private static void copyTo(String classpath, Path target) throws Exception {
        Files.copy(new ClassPathResource(classpath).getInputStream(), target);
    }

    private List<String> appliedVersions(Flyway fw) {
        List<String> versions = new ArrayList<>();
        for (org.flywaydb.core.api.MigrationInfo info : fw.info().applied()) {
            if (info.getVersion() != null) {
                versions.add(info.getVersion().getVersion());
            }
        }
        return versions;
    }

    private boolean columnExists(String db, String table, String column) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table
                + "' AND COLUMN_NAME = '" + column + "'") > 0;
    }

    private boolean columnNullable(String db, String table, String column) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table
                + "' AND COLUMN_NAME = '" + column + "' AND IS_NULLABLE = 'YES'") > 0;
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
