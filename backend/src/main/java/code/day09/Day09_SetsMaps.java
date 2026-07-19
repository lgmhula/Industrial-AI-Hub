package code.day09;

import java.util.*;

/**
 * Day 09: Set 和 Map —— Java 面试最高频集合考点。
 *
 * <p>覆盖：
 * <ol>
 *   <li>{@link HashSet}：基于 HashMap，O(1) 增删查，无序</li>
 *   <li>{@link TreeSet}：基于红黑树，O(log n)，自动排序</li>
 *   <li>{@link HashMap}：键值对，key 不可重复，O(1)</li>
 *   <li>{@link TreeMap}：基于红黑树，key 自动排序</li>
 *   <li>{@code equals()} 与 {@code hashCode()} 的契约关系</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class Day09_SetsMaps {

    public static void main(String[] args) {
        hashSetDemo();
        treeSetDemo();
        hashMapDemo();
        wordFrequency();
        equalsHashCodeDemo();
    }

    /** HashSet 去重演示 */
    private static void hashSetDemo() {
        System.out.println("========== HashSet 去重 ==========\n");

        List<Integer> nums = List.of(5, 2, 8, 5, 2, 1, 9, 8, 3);
        System.out.println("原始列表: " + nums);

        Set<Integer> set = new HashSet<>(nums);
        System.out.println("去重后:   " + set);
        System.out.println("重复元素数: " + (nums.size() - set.size()));

        // HashSet 特点：不保证顺序
        System.out.println("注意：HashSet 不保证元素顺序！");

        // 常用操作
        set.add(10);
        System.out.println("添加 10 后: " + set);
        System.out.println("包含 5? " + set.contains(5));
        System.out.println("大小: " + set.size());
    }

    /** TreeSet 自动排序演示 */
    private static void treeSetDemo() {
        System.out.println("\n========== TreeSet 排序 ==========\n");

        Set<Integer> treeSet = new TreeSet<>(List.of(7, 3, 9, 1, 5));
        System.out.println("TreeSet (自动升序): " + treeSet);

        // 降序
        NavigableSet<Integer> desc = ((TreeSet<Integer>) treeSet).descendingSet();
        System.out.println("降序视图:           " + desc);

        // 范围操作（TreeSet 独有）
        System.out.println("大于 3 的元素: " + ((TreeSet<Integer>) treeSet).tailSet(4));
        System.out.println("小于 5 的元素: " + ((TreeSet<Integer>) treeSet).headSet(5));
        System.out.println("3~7 之间:      " + ((TreeSet<Integer>) treeSet).subSet(3, 8));

        // 字符串的 TreeSet
        Set<String> words = new TreeSet<>(List.of("Java", "Python", "Go", "Rust"));
        System.out.println("字符串 TreeSet: " + words + " (按字典序)");
    }

    /** HashMap 键值映射 + 遍历 */
    private static void hashMapDemo() {
        System.out.println("\n========== HashMap 键值映射 ==========\n");

        Map<String, Integer> scores = new HashMap<>();
        scores.put("张三", 85);
        scores.put("李四", 92);
        scores.put("王五", 78);
        scores.put("赵六", 92);
        scores.putIfAbsent("张三", 100); // key 存在则不覆盖
        System.out.println("scores: " + scores);

        // 三种遍历方式
        System.out.println("\n--- HashMap 遍历 ---");
        // 1. entrySet（最推荐，一次拿 key+value）
        System.out.print("1. entrySet:  ");
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            System.out.print(e.getKey() + "=" + e.getValue() + "  ");
        }

        // 2. keySet + get（需要单独查 value）
        System.out.print("\n2. keySet:     ");
        for (String key : scores.keySet()) {
            System.out.print(key + "=" + scores.get(key) + "  ");
        }

        // 3. forEach + Lambda（Java 8+）
        System.out.print("\n3. forEach:    ");
        scores.forEach((k, v) -> System.out.print(k + "=" + v + "  "));
        System.out.println();

        // 常用操作
        System.out.println("\n张三的成绩: " + scores.get("张三"));
        System.out.println("钱七的成绩: " + scores.getOrDefault("钱七", -1) + " (不存在)");
        System.out.println("包含 key 李四? " + scores.containsKey("李四"));
        System.out.println("包含 value 92? " + scores.containsValue(92));

        // 删除
        scores.remove("王五");
        System.out.println("删除王五后: " + scores);
    }

    /** HashMap 实战：统计单词频率 */
    private static void wordFrequency() {
        System.out.println("\n========== 实战: 单词频率统计 ==========\n");

        String text = "Java Python Java Go Rust Python Java Go Java Rust Python Java Go";
        String[] words = text.split(" ");

        Map<String, Integer> freq = new HashMap<>();
        for (String word : words) {
            // merge：如果 key 存在则用函数合并，不存在则插入
            freq.merge(word, 1, Integer::sum);

            // 等价于：
            // freq.put(word, freq.getOrDefault(word, 0) + 1);
        }

        System.out.println("原文: " + text);
        System.out.println("\n词频统计:");
        // 按频率降序排列
        freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.printf("  %-8s: %d 次%n", e.getKey(), e.getValue()));

        // 与 TreeMap 对比
        System.out.println("\nTreeMap (按key字典序):");
        Map<String, Integer> treeFreq = new TreeMap<>(freq);
        treeFreq.forEach((k, v) -> System.out.printf("  %-8s: %d 次%n", k, v));
    }

    /** equals() 和 hashCode() 关系演示 */
    private static void equalsHashCodeDemo() {
        System.out.println("\n========== equals & hashCode ==========\n");

        Person p1 = new Person("张三", 20);
        Person p2 = new Person("张三", 20);
        Person p3 = new Person("张三", 21);

        System.out.println("p1: " + p1);
        System.out.println("p2: " + p2);
        System.out.println("p3: " + p3);
        System.out.println("\np1.equals(p2)? " + p1.equals(p2) + " (同名同龄)");
        System.out.println("p1.hashCode() == p2.hashCode()? "
                + (p1.hashCode() == p2.hashCode()) + " (必须相等)");
        System.out.println("p1.equals(p3)? " + p1.equals(p3) + " (同年龄不同)");

        // 放入 HashSet 验证
        Set<Person> personSet = new HashSet<>();
        personSet.add(p1);
        personSet.add(p2);  // 逻辑相等，不会重复加入
        personSet.add(p3);
        System.out.println("\nHashSet 大小: " + personSet.size() + " (p1==p2，去重了)");
        System.out.println("内容: " + personSet);

        // 如果只重写 equals 不重写 hashCode 会怎样？
        System.out.println("\n--- 陷阱演示 ---");
        System.out.println("如果只重写 equals 不重写 hashCode：");
        System.out.println("  p1.equals(p2) = true ✓");
        System.out.println("  但 p1.hashCode() ≠ p2.hashCode() ✗");
        System.out.println("  → HashSet 会把它们当成不同对象，去重失败！");
        System.out.println("  → 这就是为什么必须同时重写 equals 和 hashCode。");
    }
}
