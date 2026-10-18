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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V16 活动告警合并迁移的真实 MySQL 端到端验证。
 *
 * <p>显式执行：
 * {@code RUN_MYSQL_IT=true MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_PASSWORD=... ./mvnw test -Dtest=MySqlMigrationV16IT}。</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
class MySqlMigrationV16IT {

    private static final String HOST = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
    private static final String PORT = System.getenv().getOrDefault("MYSQL_PORT", "3307");
    private static final String USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String PASSWORD = resolvePassword();

    private static final List<String> MIGRATIONS_TO_V15 = List.of(
            "V1__baseline.sql",
            "V3__operation_log_check_types.sql",
            "V4__add_site_scoping.sql",
            "V5__add_user_security_status.sql",
            "V6__add_login_audit.sql",
            "V7__alarm_role_audit_fields.sql",
            "V8__update_admin_password.sql",
            "V9__ai_operation_log_types.sql",
            "V10__function_call_operation_type.sql",
            "V11__rag_knowledge_operation_types.sql",
            "V12__mcp_smoke_operation_types.sql",
            "V13__inspection_operation_type.sql",
            "V14__push_sse_operation_types.sql",
            "V15__auto_alarm_operation_type.sql"
    );

    @Test
    void freshDatabase_migratesV1ToV16() throws Exception {
        String db = createScratchDb("reboot_v16_fresh");
        try {
            Flyway flyway = prodFlyway(db);
            flyway.migrate();

            assertTrue(appliedVersions(flyway).contains("16"), "V16 应被应用");
            assertTrue(columnExists(db, "alarm", "repeat_count"), "repeat_count 列应存在");
            assertTrue(columnExists(db, "alarm", "last_triggered_at"), "last_triggered_at 列应存在");
            assertTrue(columnExists(db, "alarm", "active_alarm_key"), "active_alarm_key 生成列应存在");
            assertTrue(indexExists(db, "alarm", "uk_alarm_active_device_type"), "活动告警唯一索引应存在");
        } finally {
            dropDb(db);
        }
    }

    @Test
    void existingActiveDuplicates_areMergedBeforeUniqueIndex() throws Exception {
        String db = createScratchDb("reboot_v16_merge");
        Path chain = prepareV15Chain();
        try {
            Flyway.configure()
                    .dataSource(dbUrl(db), USER, PASSWORD)
                    .locations("filesystem:" + chain)
                    .load()
                    .migrate();
            long deviceId = insertDevice(db, "V16-MERGE");
            execute(db, """
                    INSERT INTO alarm(device_id, alarm_type, alarm_level, alarm_message, status, triggered_at)
                    VALUES
                      (%d, 'OVER_TEMP', 1, 'first', 0, '2026-10-18 10:00:00'),
                      (%d, 'OVER_TEMP', 2, 'second', 1, '2026-10-18 10:01:00'),
                      (%d, 'OVER_TEMP', 3, 'third', 0, '2026-10-18 10:02:00')
                    """.formatted(deviceId, deviceId, deviceId));

            Flyway flyway = prodFlyway(db);
            flyway.migrate();

            assertEquals(1L, scalar(db, """
                    SELECT COUNT(*) FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP' AND status IN (0,1)
                    """.formatted(deviceId)), "迁移后只保留一条活动告警");
            assertEquals(3L, scalar(db, """
                    SELECT repeat_count FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP' AND status IN (0,1)
                    """.formatted(deviceId)), "keep 记录应累计 3 次");
            assertEquals(2L, scalar(db, """
                    SELECT COUNT(*) FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP' AND status=2
                    """.formatted(deviceId)), "另外两条重复记录应保留为已解决审计历史");
        } finally {
            dropDb(db);
            deleteTree(chain);
        }
    }

    @Test
    void generatedColumn_allowsNewCycleAfterResolve() throws Exception {
        String db = createScratchDb("reboot_v16_cycle");
        try {
            prodFlyway(db).migrate();
            long deviceId = insertDevice(db, "V16-CYCLE");
            long firstId = insertAlarm(db, deviceId, "OVER_TEMP", 0);

            execute(db, "UPDATE alarm SET status=2, resolved_at=NOW() WHERE id=" + firstId);
            long secondId = insertAlarm(db, deviceId, "OVER_TEMP", 0);

            assertTrue(secondId > firstId, "解决后再次触发应创建新告警记录");
            assertEquals(1L, scalar(db, """
                    SELECT COUNT(*) FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP' AND status IN (0,1)
                    """.formatted(deviceId)), "新周期只能有一条活动告警");
            assertEquals(2L, scalar(db, """
                    SELECT COUNT(*) FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP'
                    """.formatted(deviceId)), "历史周期与新周期均应保留");
        } finally {
            dropDb(db);
        }
    }

    @Test
    void generatedColumn_rejectsSecondActiveRow() throws Exception {
        String db = createScratchDb("reboot_v16_unique");
        try {
            prodFlyway(db).migrate();
            long deviceId = insertDevice(db, "V16-UNIQUE");
            insertAlarm(db, deviceId, "OVER_TEMP", 0);

            SQLException error = assertThrows(SQLException.class,
                    () -> insertAlarm(db, deviceId, "OVER_TEMP", 0),
                    "同设备同类型第二条活动告警应被唯一索引拒绝");

            assertTrue(error.getMessage().contains("uk_alarm_active_device_type")
                            || error.getMessage().contains("Duplicate entry"),
                    "异常应来自活动告警唯一索引: " + error.getMessage());
        } finally {
            dropDb(db);
        }
    }

    @Test
    void concurrentUpsert_keepsSingleActiveAlarm() throws Exception {
        String db = createScratchDb("reboot_v16_concurrent");
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            prodFlyway(db).migrate();
            long deviceId = insertDevice(db, "V16-CONCURRENT");
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 48; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    execute(db, upsertSql(deviceId));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            assertEquals(1L, scalar(db, """
                    SELECT COUNT(*) FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP' AND status IN (0,1)
                    """.formatted(deviceId)), "并发 upsert 后只能有一条活动告警");
            assertEquals(48L, scalar(db, """
                    SELECT repeat_count FROM alarm
                    WHERE device_id=%d AND alarm_type='OVER_TEMP' AND status IN (0,1)
                    """.formatted(deviceId)), "每次并发触发都应累加 repeat_count");
        } finally {
            pool.shutdownNow();
            dropDb(db);
        }
    }

    // ---- helpers ----

    private static String resolvePassword() {
        String password = System.getenv("MYSQL_PASSWORD");
        if (password == null || password.isBlank()) {
            password = System.getenv("MYSQL_ROOT_PASSWORD");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "RUN_MYSQL_IT=true 需要提供 MYSQL_PASSWORD 或 MYSQL_ROOT_PASSWORD 环境变量");
        }
        return password;
    }

    private String serverUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
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
            st.executeUpdate("CREATE DATABASE `" + name
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
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

    private Path prepareV15Chain() throws Exception {
        Path chain = Files.createTempDirectory("old-chain-v16");
        for (String migration : MIGRATIONS_TO_V15) {
            Files.copy(new ClassPathResource("db/migration/" + migration).getInputStream(),
                    chain.resolve(migration));
        }
        return chain;
    }

    private static void deleteTree(Path root) throws Exception {
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private long insertDevice(String db, String code) throws SQLException {
        execute(db, """
                INSERT INTO device(device_name, device_code, device_type, status)
                VALUES ('V16 test device', '%s', 'SENSOR', 1)
                """.formatted(code));
        return scalar(db, "SELECT id FROM device WHERE device_code='" + code + "'");
    }

    private long insertAlarm(String db, long deviceId, String type, int status) throws SQLException {
        execute(db, """
                INSERT INTO alarm(device_id, alarm_type, alarm_level, alarm_message, status, triggered_at)
                VALUES (%d, '%s', 2, 'test', %d, NOW())
                """.formatted(deviceId, type, status));
        return scalar(db, """
                SELECT id FROM alarm
                WHERE device_id=%d AND alarm_type='%s'
                ORDER BY id DESC LIMIT 1
                """.formatted(deviceId, type));
    }

    private String upsertSql(long deviceId) {
        return """
                INSERT INTO alarm(device_id, alarm_type, alarm_level, alarm_message,
                                  status, triggered_at, last_triggered_at, repeat_count)
                VALUES (%d, 'OVER_TEMP', 2, 'concurrent', 0, NOW(), NOW(), 1)
                ON DUPLICATE KEY UPDATE
                    alarm_level=VALUES(alarm_level),
                    alarm_message=VALUES(alarm_message),
                    repeat_count=repeat_count+1,
                    last_triggered_at=NOW(),
                    updated_at=NOW()
                """.formatted(deviceId);
    }

    private void execute(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private long scalar(String db, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(dbUrl(db), USER, PASSWORD);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查询应有结果: " + sql);
            return rs.getLong(1);
        }
    }

    private boolean columnExists(String db, String table, String column) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA='" + db + "' AND TABLE_NAME='" + table
                + "' AND COLUMN_NAME='" + column + "'") > 0;
    }

    private boolean indexExists(String db, String table, String index) throws SQLException {
        return scalar(db, "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA='" + db + "' AND TABLE_NAME='" + table
                + "' AND INDEX_NAME='" + index + "'") > 0;
    }

    private List<String> appliedVersions(Flyway flyway) {
        List<String> versions = new ArrayList<>();
        for (org.flywaydb.core.api.MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() != null) {
                versions.add(info.getVersion().getVersion());
            }
        }
        return versions;
    }
}
