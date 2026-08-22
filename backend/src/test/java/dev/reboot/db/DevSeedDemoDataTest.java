package dev.reboot.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dev Demo Seed 测试（H2，零外部依赖，ADR 0018）。
 *
 * <p>验证 db/seed/dev/seed_demo_data.sql（ADR 0019 的 dev-only seed）：</p>
 * <ul>
 *   <li>Test B：seed 可执行，关键演示数据存在（operator01 用户、TEMP-001 设备、告警/采集/日志）；</li>
 *   <li>Test C：重复执行安全（幂等，不产生重复数据）；</li>
 *   <li>附加：不触碰开发者自建用户（既有数据保护）。</li>
 * </ul>
 *
 * <p>H2 无法执行正式迁移 V1/V3（MySQL 专有语法，见 {@link FlywayProductionSeedIsolationTest}），
 * 因此本测试用 src/test/resources/db/h2/schema-h2.sql 夹具（镜像 V1 列/约束）建表。</p>
 */
class DevSeedDemoDataTest {

    private static String h2Url() {
        return "jdbc:h2:mem:devseed_%d;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=FALSE"
                .formatted(System.nanoTime());
    }

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        // 每个测试方法独立内存库（nanoTime 后缀），避免 H2 同名库跨方法串扰
        conn = DriverManager.getConnection(h2Url(), "sa", "");
        // 镜像 V1 的夹具 schema（含必需角色 + admin）
        ScriptUtils.executeSqlScript(conn, utf8("db/h2/schema-h2.sql"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }

    /** Test B：seed 执行成功，关键演示数据存在且数量符合 seed 契约。 */
    @Test
    void devSeed_loadsKeyDemoData() throws Exception {
        runSeed();

        assertEquals(21L, scalar("SELECT COUNT(*) FROM `user`"), "admin + 20 个演示用户");
        assertEquals(50L, scalar("SELECT COUNT(*) FROM device"), "50 台演示设备");
        assertEquals(12L, scalar("SELECT COUNT(*) FROM alarm"), "12 条演示告警");
        assertEquals(7L, scalar("SELECT COUNT(*) FROM operation_log"), "7 条演示操作日志");
        assertEquals(78L, scalar("SELECT COUNT(*) FROM device_data"), "78 条演示采集数据");

        assertTrue(exists("SELECT 1 FROM `user` WHERE username = 'operator01'"), "关键用户 operator01 应存在");
        assertTrue(exists("SELECT 1 FROM `user` WHERE username = 'viewer02'"), "关键用户 viewer02 应存在");
        assertTrue(exists("SELECT 1 FROM device WHERE device_code = 'TEMP-001'"), "关键设备 TEMP-001 应存在");
        assertTrue(exists("SELECT 1 FROM device WHERE device_code = 'ROBOT-W-001'"), "关键设备 ROBOT-W-001 应存在");
        assertTrue(exists("SELECT 1 FROM alarm WHERE alarm_type = 'OVER_TEMP'"), "关键告警 OVER_TEMP 应存在");

        // 角色分配正确：operator01 → OPERATOR，viewer01 → VIEWER
        assertTrue(exists("""
                SELECT 1 FROM user_role ur
                JOIN `user` u ON u.id = ur.user_id
                JOIN role r ON r.id = ur.role_id
                WHERE u.username = 'operator01' AND r.role_code = 'OPERATOR'"""), "operator01 应被分配 OPERATOR");
        assertTrue(exists("""
                SELECT 1 FROM user_role ur
                JOIN `user` u ON u.id = ur.user_id
                JOIN role r ON r.id = ur.role_id
                WHERE u.username = 'user05' AND r.role_code = 'VIEWER'"""), "user05 应被分配 VIEWER");

        // P1-01 站点成员：20 个演示用户归属默认站点（2 OPERATOR + 18 VIEWER）
        assertEquals(20L, scalar("SELECT COUNT(*) FROM user_site"), "20 个演示用户应分配默认站点");
        assertTrue(exists("""
                SELECT 1 FROM user_site us
                JOIN `user` u ON u.id = us.user_id
                JOIN role r ON r.id = us.role_id
                JOIN site s ON s.id = us.site_id
                WHERE u.username = 'operator01' AND r.role_code = 'OPERATOR' AND s.site_code = 'DEFAULT'"""),
                "operator01 应为默认站点 OPERATOR");
        assertTrue(exists("""
                SELECT 1 FROM user_site us
                JOIN `user` u ON u.id = us.user_id
                JOIN role r ON r.id = us.role_id
                JOIN site s ON s.id = us.site_id
                WHERE u.username = 'viewer01' AND r.role_code = 'VIEWER' AND s.site_code = 'DEFAULT'"""),
                "viewer01 应为默认站点 VIEWER");
    }

    /** Test C：seed 重复执行——数量不增长，且用户/设备/告警不重复。 */
    @Test
    void devSeed_isIdempotent() throws Exception {
        runSeed();
        long[] afterFirst = counts();
        runSeed();
        long[] afterSecond = counts();

        org.junit.jupiter.api.Assertions.assertArrayEquals(
                afterFirst, afterSecond,
                "seed 二次执行不得产生重复数据（幂等契约）"
        );
        // 语义化兜底：关键计数明确
        assertEquals(21L, afterSecond[0], "用户数应为 21（admin + 20）");
        assertEquals(50L, afterSecond[1], "设备数应为 50");
        assertEquals(12L, afterSecond[2], "告警数应为 12");
    }

    /** 附加：seed 不触碰开发者自建用户（既有库数据保护）。 */
    @Test
    void devSeed_doesNotTouchDeveloperOwnedUsers() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO `user` (`username`, `password`, `email`, `status`)
                    VALUES ('my_custom_user', 'x', 'custom@dev.local', 1)""");
        }
        runSeed();

        assertTrue(exists("SELECT 1 FROM `user` WHERE username = 'my_custom_user'"), "开发者自建用户不应被 seed 影响");
        assertEquals(22L, scalar("SELECT COUNT(*) FROM `user`"), "既有用户 + 20 个演示用户，不重复");
    }

    // ---- helpers ----

    private static EncodedResource utf8(String location) {
        return new EncodedResource(new ClassPathResource(location), StandardCharsets.UTF_8);
    }

    private void runSeed() throws Exception {
        ScriptUtils.executeSqlScript(conn, utf8("db/seed/dev/seed_demo_data.sql"));
    }

    private long[] counts() throws SQLException {
        return new long[]{
                scalar("SELECT COUNT(*) FROM `user`"),
                scalar("SELECT COUNT(*) FROM device"),
                scalar("SELECT COUNT(*) FROM alarm"),
                scalar("SELECT COUNT(*) FROM device_data"),
                scalar("SELECT COUNT(*) FROM operation_log")
        };
    }

    private long scalar(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "查询应有结果: " + sql);
            return rs.getLong(1);
        }
    }

    private boolean exists(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }
}
