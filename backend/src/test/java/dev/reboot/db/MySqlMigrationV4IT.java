package dev.reboot.db;

import org.flywaydb.core.Flyway;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V4 站点授权迁移的 MySQL 端到端验证（默认跳过；显式执行：
 * {@code RUN_MYSQL_IT=true MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_PASSWORD=... ./mvnw test -Dtest=MySqlMigrationV4IT}）。
 *
 * <p>只验证迁移本身（migration-only），不涉及任何业务授权逻辑：</p>
 * <ol>
 *   <li>全新库：V1→V3→V4 全部成功，site/user_site 表建立、默认站点存在、device.site_id NOT NULL + 索引；</li>
 *   <li>已有 V1-V3 history 的库：增量执行 V4 成功，既有 device 全部回填默认站点。</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class MySqlMigrationV4IT {

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

    /** 镜像 application.yml 的 Flyway 配置（locations / baseline / ignore-migration-patterns）。 */
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
    // 场景 1：全新数据库 → V1/V3/V4 全部成功
    // ==================================================================
    @Test
    void freshDatabase_migratesV1ToV4() throws Exception {
        String db = createScratchDb("reboot_v4_fresh");
        try {
            Flyway fw = prodFlyway(db);
            fw.migrate();

            List<String> applied = appliedVersions(fw);
            assertTrue(applied.containsAll(List.of("1", "3", "4")), "全新库应执行 V1/V3/V4: " + applied);

            // site 表 + 默认站点
            assertTrue(tableExists(db, "site"), "site 表应存在");
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM site"), "应有且仅有默认站点");
            assertTrue(exists(db, "SELECT 1 FROM site WHERE site_code = 'DEFAULT'"), "默认站点 DEFAULT 应存在");

            // user_site 表（空）
            assertTrue(tableExists(db, "user_site"), "user_site 表应存在");
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM user_site"), "user_site 初始应为空");

            // device.site_id NOT NULL + 索引
            assertFalse(columnNullable(db, "device", "site_id"), "device.site_id 应为 NOT NULL");
            assertTrue(indexExists(db, "device", "idx_device_site_id"), "idx_device_site_id 索引应存在");
        } finally {
            dropDb(db);
        }
    }

    // ==================================================================
    // 场景 2：已有 V1-V3 history 的库 → 增量执行 V4 成功，设备回填默认站点
    // ==================================================================
    @Test
    void existingDatabaseWithV1V2V3History_migratesV4() throws Exception {
        String db = createScratchDb("reboot_v4_existing");
        Path oldChain = Files.createTempDirectory("old-chain-v4");
        try {
            // 模拟旧链路：V1 + V2(演示种子内容) + V3 —— 已在旧版本上执行过（history 含 V1/V2/V3）
            copyTo("db/migration/V1__baseline.sql", oldChain.resolve("V1__baseline.sql"));
            Files.write(oldChain.resolve("V2__seed_test_data.sql"), seedProxyForOldChain());
            copyTo("db/migration/V3__operation_log_check_types.sql", oldChain.resolve("V3__operation_log_check_types.sql"));
            Flyway oldChainFlyway = Flyway.configure()
                    .dataSource(dbUrl(db), USER, PASSWORD)
                    .locations("filesystem:" + oldChain)
                    .load();
            oldChainFlyway.migrate();
            assertEquals(50L, scalar(db, "SELECT COUNT(*) FROM device"), "旧链路 seed 应已灌入 50 台设备");

            // 新链路（V1/V3/V4，V2 已退役被 ignore）→ V4 增量执行
            Flyway fw = prodFlyway(db);
            fw.migrate();
            assertTrue(appliedVersions(fw).contains("4"), "V4 应被应用");

            assertTrue(tableExists(db, "site"), "site 表应存在");
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM site"), "默认站点应存在");

            // 既有 50 台设备全部回填 DEFAULT 站点
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM device WHERE site_id IS NULL"),
                    "所有既有设备应回填 site_id");
            assertEquals(50L, scalar(db,
                            "SELECT COUNT(*) FROM device d JOIN site s ON s.id = d.site_id WHERE s.site_code = 'DEFAULT'"),
                    "50 台设备应全部归属 DEFAULT 站点");
        } finally {
            dropDb(db);
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

    private boolean columnNullable(String db, String table, String column) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = '" + db + "' AND TABLE_NAME = '" + table
                + "' AND COLUMN_NAME = '" + column + "' AND IS_NULLABLE = 'YES'") > 0;
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

    private boolean exists(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }
}
