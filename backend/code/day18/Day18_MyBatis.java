package code.day18;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;
import java.util.List;

/**
 * Day 18: MyBatis XML 映射 —— SqlSession CRUD。
 *
 * <p>五个练习：
 * <ol>
 *   <li>SqlSessionFactory 构建</li>
 *   <li>selectList / selectOne 查询</li>
 *   <li>insert 插入（自动回填主键）</li>
 *   <li>update 更新</li>
 *   <li>delete 删除</li>
 * </ol>
 *
 * @author Reboot
 * @since 2026-07-18
 */
public class Day18_MyBatis {

    static SqlSessionFactory factory;

    static {
        try {
            factory = new SqlSessionFactoryBuilder()
                    .build(Resources.getResourceAsStream("code/day18/mybatis-config.xml"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        selectAll();
        selectById();
        selectByRole();
        insertUser();
        updateStatus();
        deleteUser();
    }

    static void selectAll() {
        System.out.println("========== 1. selectList: 所有用户 ==========");
        try (SqlSession session = factory.openSession()) {
            List<User> users = session.selectList("code.day18.UserMapper.findAll");
            users.forEach(u -> System.out.println("  " + u));
        }
    }

    static void selectById() {
        System.out.println("\n========== 2. selectOne: ID=1 ==========");
        try (SqlSession session = factory.openSession()) {
            User user = session.selectOne("code.day18.UserMapper.findById", 1);
            System.out.println("  " + user);
        }
    }

    static void selectByRole() {
        System.out.println("\n========== 3. selectList: OPERATOR ==========");
        try (SqlSession session = factory.openSession()) {
            List<User> users = session.selectList("code.day18.UserMapper.findByRole", "OPERATOR");
            users.forEach(u -> System.out.println("  " + u));
        }
    }

    static void insertUser() {
        System.out.println("\n========== 4. insert ==========");
        try (SqlSession session = factory.openSession(true)) {
            User u = new User();
            u.setUsername("mybatis_user2");
            u.setPassword("pass123");
            u.setEmail("mybatis@reboot.dev");
            u.setPhone("13900000001");
            u.setRole("VIEWER");
            session.insert("code.day18.UserMapper.insert", u);
            System.out.println("  插入成功, 自增ID=" + u.getId());
        }
    }

    static void updateStatus() {
        System.out.println("\n========== 5. update ==========");
        try (SqlSession session = factory.openSession(true)) {
            int rows = session.update("code.day18.UserMapper.updateStatus",
                    new User() {{ setId(3L); setStatus(0); }}); // id=3, status=0
            System.out.println("  更新 " + rows + " 行");
        }
    }

    static void deleteUser() {
        System.out.println("\n========== 6. delete ==========");
        try (SqlSession session = factory.openSession(true)) {
            int rows = session.delete("code.day18.UserMapper.deleteById", 3);
            System.out.println("  删除 " + rows + " 行");
        }
    }
}
