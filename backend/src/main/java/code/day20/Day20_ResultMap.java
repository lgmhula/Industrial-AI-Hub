package code.day20;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.util.*;

/**
 * Day 20: MyBatis ResultMap 多表关联查询。
 *
 * <p>核心知识点：</p>
 * <ul>
 *   <li>&lt;association&gt; —— 一对一（Product → Category）</li>
 *   <li>一对多（Category → List&lt;Product&gt;，Java 层组装）</li>
 *   <li>&lt;choose&gt; / &lt;when&gt; / &lt;otherwise&gt; 动态 SQL</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-18
 */
public class Day20_ResultMap {

    public static void main(String[] args) throws Exception {
        String resource = "code/day20/mybatis-config.xml";
        SqlSessionFactory factory = new SqlSessionFactoryBuilder()
            .build(Resources.getResourceAsStream(resource));

        try (SqlSession session = factory.openSession()) {
            // ===== 1. JOIN 查询 + association（一对一）=====
            System.out.println("===== 1. 产品 + 分类（association 一对一）=====");
            List<Product> products = session.selectList("code.day20.ProductMapper.selectAllWithCategory");
            for (Product p : products) {
                System.out.println("  " + p);
            }

            // ===== 2. 一对多：Category → Products（Java 层组装）=====
            System.out.println("\n===== 2. 分类 → 产品（一对多）=====");
            List<Category> categories = session.selectList("code.day20.CategoryMapper.selectAllCategories");
            for (Category c : categories) {
                List<Product> catProducts = session.selectList(
                    "code.day20.CategoryMapper.selectProductsByCategoryId", c.getId());
                System.out.printf("  %s (%d 个产品)\n", c.getName(), catProducts.size());
                for (Product p : catProducts) {
                    System.out.printf("    - %s (¥%.2f)\n", p.getName(), p.getPrice());
                }
            }

            // ===== 3. 动态 SQL: choose/when/otherwise =====
            System.out.println("\n===== 3. 动态 SQL（choose/when/otherwise）=====");
            Map<String, Object> params = new HashMap<>();

            // 场景 A: 按名称模糊查
            params.put("name", "传感器");
            System.out.print("  按名称'传感器': ");
            List<Product> r1 = session.selectList("code.day20.ProductMapper.selectByCondition", params);
            r1.forEach(p -> System.out.print(p.getName() + " "));

            // 场景 B: 按分类查
            params.clear();
            params.put("categoryId", 2);
            System.out.print("\n  按 categoryId=2: ");
            List<Product> r2 = session.selectList("code.day20.ProductMapper.selectByCondition", params);
            r2.forEach(p -> System.out.print(p.getName() + " "));

            // 场景 C: 无参数走 otherwise
            params.clear();
            System.out.print("\n  无参数(otherwise→price<1000): ");
            List<Product> r3 = session.selectList("code.day20.ProductMapper.selectByCondition", params);
            r3.forEach(p -> System.out.print(p.getName() + " "));

            System.out.println("\n\n✅ Day 20 ResultMap + 动态 SQL 全部通过");
        }
    }
}
