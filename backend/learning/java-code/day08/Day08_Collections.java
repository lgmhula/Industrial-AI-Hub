package code.day08;

import java.util.*;

/**
 * Day 08: 集合框架 —— ArrayList &amp; LinkedList。
 *
 * <p>本日聚焦 List 接口的两个核心实现：
 * <ul>
 *   <li>{@link ArrayList}：基于动态数组，查询 O(1)，增删 O(n)</li>
 *   <li>{@link LinkedList}：基于双向链表，查询 O(n)，头尾增删 O(1)</li>
 * </ul>
 *
 * <p>四个练习：
 * <ol>
 *   <li>ArrayList 增删改查 + 四种遍历方式</li>
 *   <li>ArrayList 实战：简易通讯录</li>
 *   <li>LinkedList vs ArrayList 性能对比</li>
 *   <li>Collections 工具类排序/查找</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class Day08_Collections {

    public static void main(String[] args) {
        arrayListBasics();
        contactBook();
        performanceCompare();
        collectionsTools();
    }

    /**
     * 练习1：ArrayList 增删改查 + 四种遍历。
     */
    private static void arrayListBasics() {
        System.out.println("========== 练习1: ArrayList 基本操作 ==========\n");

        // 创建与添加
        ArrayList<String> list = new ArrayList<>();
        list.add("Java");
        list.add("Python");
        list.add("Go");
        list.add("Rust");
        list.add(1, "Kotlin"); // 在索引1处插入
        System.out.println("初始列表: " + list);

        // 查询与修改
        System.out.println("索引1的元素: " + list.get(1));
        list.set(2, "TypeScript"); // 修改索引2
        System.out.println("修改后: " + list);

        // 删除
        list.remove("Python");       // 按值删
        list.remove(0);              // 按索引删
        System.out.println("删除后: " + list);
        System.out.println("大小: " + list.size());
        System.out.println("包含 Go? " + list.contains("Go"));

        // 四种遍历方式
        System.out.println("\n--- 四种遍历 ---");
        // 1. for-i（需要索引时用）
        System.out.print("1. for-i:      ");
        for (int i = 0; i < list.size(); i++) {
            System.out.print(list.get(i) + " ");
        }

        // 2. for-each（最简洁，只读）
        System.out.print("\n2. for-each:   ");
        for (String s : list) {
            System.out.print(s + " ");
        }

        // 3. Iterator（需要边遍历边删除时用）
        System.out.print("\n3. Iterator:   ");
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            System.out.print(it.next() + " ");
        }

        // 4. forEach + Lambda（Java 8+ 函数式）
        System.out.print("\n4. Lambda:     ");
        list.forEach(s -> System.out.print(s + " "));
        System.out.println();

        // 使用 List.of 创建不可变列表
        List<String> immutable = List.of("A", "B", "C");
        System.out.println("\n不可变列表: " + immutable);
        // immutable.add("D"); ← 会抛 UnsupportedOperationException
    }

    /**
     * 练习2：ArrayList 实战 —— 简易通讯录。
     *
     * <p>用 {@code ArrayList<Contact>} 存储联系人，支持增删查。</p>
     */
    private static void contactBook() {
        System.out.println("\n========== 练习2: 简易通讯录 ==========");
        Scanner scanner = new Scanner(System.in);
        ArrayList<Contact> contacts = new ArrayList<>();
        boolean running = true;

        while (running) {
            System.out.print("\n1.添加 2.列表 3.搜索 4.删除 0.退出 → ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1 -> {
                    System.out.print("姓名: ");
                    String name = scanner.nextLine();
                    System.out.print("电话: ");
                    String phone = scanner.nextLine();
                    contacts.add(new Contact(name, phone));
                    System.out.println("已添加。");
                }
                case 2 -> {
                    if (contacts.isEmpty()) {
                        System.out.println("通讯录为空。");
                    } else {
                        System.out.println("共 " + contacts.size() + " 人:");
                        contacts.forEach(c -> System.out.println("  " + c));
                    }
                }
                case 3 -> {
                    System.out.print("搜索姓名: ");
                    String q = scanner.nextLine();
                    boolean found = false;
                    for (Contact c : contacts) {
                        if (c.name().contains(q)) {
                            System.out.println("  找到: " + c);
                            found = true;
                        }
                    }
                    if (!found) System.out.println("  未找到。");
                }
                case 4 -> {
                    System.out.print("删除第几个? (1-" + contacts.size() + "): ");
                    int idx = scanner.nextInt();
                    scanner.nextLine();
                    if (idx >= 1 && idx <= contacts.size()) {
                        contacts.remove(idx - 1);
                        System.out.println("已删除。");
                    }
                }
                case 0 -> running = false;
            }
        }
        // 不 close scanner，避免关闭 System.in
        System.out.println("通讯录演示结束。");
    }

    /**
     * 练习3：ArrayList vs LinkedList 性能对比。
     */
    private static void performanceCompare() {
        System.out.println("\n========== 练习3: 性能对比 ==========\n");

        final int N = 100_000;
        ArrayList<Integer> arrayList = new ArrayList<>();
        LinkedList<Integer> linkedList = new LinkedList<>();

        // 尾部追加
        long t1 = System.nanoTime();
        for (int i = 0; i < N; i++) arrayList.add(i);
        long t2 = System.nanoTime();
        for (int i = 0; i < N; i++) linkedList.add(i);
        long t3 = System.nanoTime();
        System.out.printf("尾部追加 %d 个元素:%n", N);
        System.out.printf("  ArrayList:  %6.2f ms%n", (t2 - t1) / 1e6);
        System.out.printf("  LinkedList: %6.2f ms%n", (t3 - t2) / 1e6);

        // 随机查询
        t1 = System.nanoTime();
        for (int i = 0; i < 10000; i++) arrayList.get(i * 10);
        t2 = System.nanoTime();
        for (int i = 0; i < 10000; i++) linkedList.get(i * 10);
        t3 = System.nanoTime();
        System.out.printf("%n随机查询 10000 次:%n");
        System.out.printf("  ArrayList:  %6.2f ms (O(1))%n", (t2 - t1) / 1e6);
        System.out.printf("  LinkedList: %6.2f ms (O(n))%n", (t3 - t2) / 1e6);

        // 头部插入
        t1 = System.nanoTime();
        for (int i = 0; i < 10000; i++) arrayList.add(0, i);
        t2 = System.nanoTime();
        for (int i = 0; i < 10000; i++) linkedList.add(0, i);
        t3 = System.nanoTime();
        System.out.printf("%n头部插入 10000 次:%n");
        System.out.printf("  ArrayList:  %6.2f ms (O(n))%n", (t2 - t1) / 1e6);
        System.out.printf("  LinkedList: %6.2f ms (O(1))%n", (t3 - t2) / 1e6);
    }

    /**
     * 练习4：Collections 工具类。
     */
    private static void collectionsTools() {
        System.out.println("\n========== 练习4: Collections 工具类 ==========\n");

        List<Integer> nums = new ArrayList<>(List.of(5, 2, 8, 1, 9, 3, 7));
        System.out.println("原始: " + nums);

        Collections.sort(nums);
        System.out.println("排序: " + nums);

        Collections.reverse(nums);
        System.out.println("反转: " + nums);

        Collections.shuffle(nums);
        System.out.println("打乱: " + nums);

        System.out.println("最大: " + Collections.max(nums));
        System.out.println("最小: " + Collections.min(nums));
        System.out.println("8 出现次数: " + Collections.frequency(nums, 8));

        // 二分查找（必须先排序）
        Collections.sort(nums);
        int idx = Collections.binarySearch(nums, 5);
        System.out.println("二分查 5: 索引 " + idx);

        // 不可修改视图
        List<Integer> unmod = Collections.unmodifiableList(nums);
        System.out.println("不可变视图创建成功");
        // unmod.add(10); ← 会抛异常
    }

    /**
     * 联系人记录 —— Java 14+ record，自动生成构造/getter/equals/hashCode/toString。
     */
    record Contact(String name, String phone) {
        @Override
        public String toString() {
            return name + " - " + phone;
        }
    }
}
