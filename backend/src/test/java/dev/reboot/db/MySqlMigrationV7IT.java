package dev.reboot.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
 * V7 告警审计字段 + 唯一约束修复 + 角色管理字段迁移的 MySQL 端到端验证（Testcontainers）。
 *
 * <p>使用 Testcontainers 自动拉起 MySQL 8.4 容器，无需手动启动 MySQL。
 * Docker 不可用时自动跳过（{@code disabledWithoutDocker = true}）。</p>
 *
 * <p>验证内容：</p>
 * <ol>
 *   <li>全新库：V1→V7 全部成功，alarm 审计字段 / device 复合唯一约束 / role 管理字段就绪；</li>
 *   <li>已有 V1-V6 history 的库：增量执行 V7 成功（append-only，存量无影响）。</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationV7IT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("reboot_v7_it")
            .withUsername("root")
            .withPassword("test-password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    private static final List<String> CREATED_DBS = new ArrayList<>();

    private String serverUrl() {
        String url = MYSQL.getJdbcUrl();
        int idx = url.lastIndexOf('/');
        return url.substring(0, idx + 1) + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private String dbUrl(String db) {
        return MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + db);
    }

    private String getUser() {
        return MYSQL.getUsername();
    }

    private String getPassword() {
        return MYSQL.getPassword();
    }

    private Flyway prodFlyway(String db) {
        return Flyway.configure()
                .dataSource(dbUrl(db), getUser(), getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("2")
                .ignoreMigrationPatterns("*:missing")
                .load();
    }

    private String createScratchDb(String prefix) throws SQLException {
        String name = prefix + "_" + System.nanoTime();
        try (Connection c = DriverManager.getConnection(serverUrl(), getUser(), getPassword());
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        CREATED_DBS.add(name);
        return name;
    }

    private void dropDb(String db) {
        try (Connection c = DriverManager.getConnection(serverUrl(), getUser(), getPassword());
             Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + db + "`");
        } catch (SQLException e) {
            throw new RuntimeException("清理临时库失败: " + db, e);
        }
    }

    // ==================================================================
    // 场景 1：全新数据库 → V1..V7 全部成功，V7 变更结构正确
    // ==================================================================
    @Test
    void freshDatabase_migratesV1ToV7() throws Exception {
        String db = createScratchDb("reboot_v7_fresh");
        try {
            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedVersions(fw).containsAll(List.of("1", "3", "4", "5", "6", "7")),
                    "全新库应执行 V1/V3/V4/V5/V6/V7");

            // --- alarm 审计字段 ---
            for (String col : new String[]{"acknowledged_at", "acknowledged_by", "resolved_by", "updated_at"}) {
                assertTrue(columnExists(db, "alarm", col), "alarm 缺少列: " + col);
            }
            assertTrue(indexExists(db, "alarm", "idx_alarm_acknowledged_by"), "idx_alarm_acknowledged_by 索引应存在");

            // --- device 复合唯一约束 ---
            assertFalse(indexExists(db, "device", "uk_device_code"), "旧索引 uk_device_code 应已删除");
            assertTrue(indexExists(db, "device", "uk_device_code_deleted"), "新索引 uk_device_code_deleted 应存在");

            // --- role 管理字段 ---
            for (String col : new String[]{"status", "is_deleted", "updated_at"}) {
                assertTrue(columnExists(db, "role", col), "role 缺少列: " + col);
            }
            assertFalse(indexExists(db, "role", "uk_role_code"), "旧索引 uk_role_code 应已删除");
            assertTrue(indexExists(db, "role", "uk_role_code_deleted"), "新索引 uk_role_code_deleted 应存在");
            assertTrue(indexExists(db, "role", "idx_role_status"), "idx_role_status 索引应存在");

            // 默认角色仍保留且状态为启用
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM role WHERE status = 1"), "默认角色应为启用状态");
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 2：已有 V1-V6 history 的库 → 增量执行 V7
    // ==================================================================
    @Test
    void existingDatabaseWithV1ToV6History_migratesV7() throws Exception {
        String db = createScratchDb("reboot_v7_existing");
        Path oldChain = Files.createTempDirectory("old-chain-v7");
        try {
            copyTo("db/migration/V1__baseline.sql", oldChain.resolve("V1__baseline.sql"));
            copyTo("db/migration/V3__operation_log_check_types.sql", oldChain.resolve("V3__operation_log_check_types.sql"));
            copyTo("db/migration/V4__add_site_scoping.sql", oldChain.resolve("V4__add_site_scoping.sql"));
            copyTo("db/migration/V5__add_user_security_status.sql", oldChain.resolve("V5__add_user_security_status.sql"));
            copyTo("db/migration/V6__add_login_audit.sql", oldChain.resolve("V6__add_login_audit.sql"));
            Flyway oldChainFlyway = Flyway.configure()
                    .dataSource(dbUrl(db), getUser(), getPassword())
                    .locations("filesystem:" + oldChain)
                    .load();
            oldChainFlyway.migrate();
            assertFalse(columnExists(db, "alarm", "acknowledged_at"), "V6 后 alarm 不应有 acknowledged_at");

            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedVersions(fw).contains("7"), "V7 应被应用");
            assertTrue(columnExists(db, "alarm", "acknowledged_at"), "V7 后 alarm 应有 acknowledged_at");
            assertTrue(indexExists(db, "device", "uk_device_code_deleted"), "V7 后复合唯一约束应存在");
            assertTrue(indexExists(db, "role", "idx_role_status"), "V7 后 role status 索引应存在");
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

    private boolean indexExists(String db, String table, String index) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table
                + "' AND INDEX_NAME = '" + index + "'") > 0;
    }

    private long scalar(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), getUser(), getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查询应有结果: " + sql);
            return rs.getLong(1);
        }
    }
}
