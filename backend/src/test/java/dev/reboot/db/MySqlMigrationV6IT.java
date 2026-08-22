package dev.reboot.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V6 登录审计迁移的 MySQL 端到端验证（默认跳过；显式执行：
 * {@code RUN_MYSQL_IT=true MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_PASSWORD=... ./mvnw test -Dtest=MySqlMigrationV6IT}）。
 *
 * <p>只验证迁移本身：</p>
 * <ol>
 *   <li>全新库：V1→V6 全部成功，login_audit 表/字段/索引存在；</li>
 *   <li>已有 V1-V5 history 的库：增量执行 V6 成功（append-only，存量无影响）。</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class MySqlMigrationV6IT {

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
    // 场景 1：全新数据库 → V1..V6 全部成功，login_audit 结构与索引正确
    // ==================================================================
    @Test
    void freshDatabase_migratesV1ToV6() throws Exception {
        String db = createScratchDb("reboot_v6_fresh");
        try {
            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedVersions(fw).containsAll(List.of("1", "3", "4", "5", "6")),
                    "全新库应执行 V1/V3/V4/V5/V6");

            assertTrue(tableExists(db, "login_audit"), "login_audit 表应存在");
            for (String col : new String[]{"id", "user_id", "username", "success", "ip_address",
                    "user_agent", "reason", "created_at"}) {
                assertTrue(columnExists(db, "login_audit", col), "login_audit 缺少列: " + col);
            }
            assertTrue(indexExists(db, "login_audit", "idx_login_audit_user_time"), "用户时间索引应存在");
            assertTrue(indexExists(db, "login_audit", "idx_login_audit_created_time"), "创建时间索引应存在");
            assertFalse(columnExists(db, "login_audit", "password"), "禁止 password 列");
            assertFalse(columnExists(db, "login_audit", "token"), "禁止 token 列");
            assertFalse(columnExists(db, "login_audit", "secret"), "禁止 secret 列");
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 2：已有 V1-V5 history 的库 → 增量执行 V6
    // ==================================================================
    @Test
    void existingDatabaseWithV1ToV5History_migratesV6() throws Exception {
        String db = createScratchDb("reboot_v6_existing");
        Path oldChain = Files.createTempDirectory("old-chain-v6");
        try {
            copyTo("db/migration/V1__baseline.sql", oldChain.resolve("V1__baseline.sql"));
            copyTo("db/migration/V3__operation_log_check_types.sql", oldChain.resolve("V3__operation_log_check_types.sql"));
            copyTo("db/migration/V4__add_site_scoping.sql", oldChain.resolve("V4__add_site_scoping.sql"));
            copyTo("db/migration/V5__add_user_security_status.sql", oldChain.resolve("V5__add_user_security_status.sql"));
            Flyway oldChainFlyway = Flyway.configure()
                    .dataSource(dbUrl(db), USER, PASSWORD)
                    .locations("filesystem:" + oldChain)
                    .load();
            oldChainFlyway.migrate();
            assertFalse(tableExists(db, "login_audit"), "旧库不应有 login_audit 表");

            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedVersions(fw).contains("6"), "V6 应被应用");
            assertTrue(tableExists(db, "login_audit"), "V6 后 login_audit 表应存在");
            assertTrue(indexExists(db, "login_audit", "idx_login_audit_created_time"), "索引应存在");
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

    private boolean tableExists(String db, String table) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table + "'") > 0;
    }

    private boolean columnExists(String db, String table, String column) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table
                + "' AND COLUMN_NAME = '" + column + "'") > 0;
    }

    private boolean indexExists(String db, String table, String index) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table
                + "' AND INDEX_NAME = '" + index + "'") > 0;
    }

    private long scalar(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查询应有结果: " + sql);
            return rs.getLong(1);
        }
    }
}
