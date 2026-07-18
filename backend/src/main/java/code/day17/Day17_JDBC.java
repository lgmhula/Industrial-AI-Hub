package code.day17;

import java.sql.*;

/**
 * Day 17: JDBC 原生连接 —— CRUD + PreparedStatement + 事务。
 *
 * <p>五个练习：
 * <ol>
 *   <li>获取数据库连接（Connection）</li>
 *   <li>PreparedStatement 防注入 CRUD</li>
 *   <li>ResultSet 遍历与映射</li>
 *   <li>JDBC 事务（commit/rollback）</li>
 *   <li>批量插入性能对比</li>
 * </ol>
 *
 * <p>连接信息：MySQL Docker 3307, database=reboot</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-17
 */
public class Day17_JDBC {

    static final String URL = "jdbc:mysql://127.0.0.1:3307/reboot"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    static final String USER = "root";
    static final String PASS = "1zxcvbnm";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        preparedStatementDemo();
        transactionDemo();
        batchInsertDemo();
    }

    /** 练习1: PreparedStatement CRUD —— 防 SQL 注入 */
    static void preparedStatementDemo() throws SQLException {
        System.out.println("========== PreparedStatement CRUD ==========\n");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            // CREATE TABLE（如果不存在）
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS jdbc_test (
                    id   INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    age  INT
                )
            """);

            // INSERT: 用 ? 占位，防止 SQL 注入
            String insertSQL = "INSERT INTO jdbc_test (name, age) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSQL)) {
                ps.setString(1, "张三");
                ps.setInt(2, 25);
                ps.executeUpdate();
                ps.setString(1, "李四");
                ps.setInt(2, 30);
                ps.executeUpdate();
                ps.setString(1, "王五"); // 尝试注入: "'; DROP TABLE jdbc_test; --"
                ps.setInt(2, 28);
                ps.executeUpdate();
                System.out.println("插入 3 条数据");
            }

            // SELECT: PreparedStatement 查询
            String query = "SELECT id, name, age FROM jdbc_test WHERE age > ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, 26);
                ResultSet rs = ps.executeQuery();
                System.out.println("\n年龄 > 26 的用户:");
                while (rs.next()) {
                    System.out.printf("  id=%d, name=%s, age=%d%n",
                            rs.getInt("id"), rs.getString("name"), rs.getInt("age"));
                }
            }

            // UPDATE
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE jdbc_test SET age = ? WHERE name = ?")) {
                ps.setInt(1, 26);
                ps.setString(2, "张三");
                int updated = ps.executeUpdate();
                System.out.println("\n更新张三年龄: " + updated + " 行");
            }

            // DELETE
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM jdbc_test WHERE name = ?")) {
                ps.setString(1, "王五");
                int deleted = ps.executeUpdate();
                System.out.println("删除王五: " + deleted + " 行");
            }

            // 最终查询
            System.out.println("\n最终数据:");
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM jdbc_test");
            while (rs.next()) {
                System.out.printf("  id=%d, name=%s, age=%d%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(3));
            }
        }
    }

    /** 练习2: JDBC 事务 —— commit / rollback */
    static void transactionDemo() throws SQLException {
        System.out.println("\n========== 事务控制 ==========\n");

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            conn.setAutoCommit(false); // 关闭自动提交

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO jdbc_test (name, age) VALUES (?, ?)")) {
                ps.setString(1, "事务测试A");
                ps.setInt(2, 20);
                ps.executeUpdate();

                ps.setString(1, "事务测试B");
                ps.setInt(2, 22);
                ps.executeUpdate();

                conn.commit();
                System.out.println("事务提交成功 → 2 条数据已持久化");

                // 演示回滚
                ps.setString(1, "回滚测试");
                ps.setInt(2, 99);
                ps.executeUpdate();

                conn.rollback();
                System.out.println("事务回滚 → '回滚测试' 被撤销");
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("事务异常，已回滚: " + e.getMessage());
            }

            // 验证：只有前 2 条
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT name FROM jdbc_test WHERE name LIKE '事务%' OR name LIKE '回滚%'");
            System.out.print("验证: ");
            while (rs.next()) System.out.print(rs.getString(1) + " ");
            System.out.println("(回滚测试不应该出现)");

            conn.setAutoCommit(true);
        }
    }

    /** 练习3: 批量插入性能 */
    static void batchInsertDemo() throws SQLException {
        System.out.println("\n========== 批量插入 ==========\n");
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            conn.setAutoCommit(false);
            String sql = "INSERT INTO jdbc_test (name, age) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                long t1 = System.nanoTime();
                for (int i = 0; i < 1000; i++) {
                    ps.setString(1, "batch-" + i);
                    ps.setInt(2, 20 + i % 30);
                    ps.addBatch();
                    if (i % 100 == 0) ps.executeBatch();
                }
                ps.executeBatch();
                conn.commit();
                long t2 = System.nanoTime();
                System.out.printf("批量插入 1000 条: %.2f ms%n", (t2 - t1) / 1e6);
            }
        }
    }
}
