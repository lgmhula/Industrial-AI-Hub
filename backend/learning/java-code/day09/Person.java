package code.day09;

import java.util.Objects;

/**
 * Person 类 —— 演示 {@code equals()} 和 {@code hashCode()} 的正确实现。
 *
 * <h3>为什么这两个方法重要？</h3>
 * <p>当 Person 对象作为 HashMap 的 key 或存入 HashSet 时，
 * 集合框架依赖这两个方法来判断对象是否相等。</p>
 *
 * <p><b>核心契约（Java 规范）：</b>
 * <ol>
 *   <li>如果 {@code a.equals(b)} 为 true，则 {@code a.hashCode() == b.hashCode()} 必须为 true</li>
 *   <li>如果 {@code a.equals(b)} 为 false，hashCode 可以相等也可以不相等（但不等更好，减少哈希冲突）</li>
 *   <li>重写 equals 必须同时重写 hashCode</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class Person {

    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    /**
     * 判断两个 Person 是否"逻辑相等"。
     * <p>比较 name 和 age 两个字段。</p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person other)) return false;
        return age == other.age && Objects.equals(name, other.name);
    }

    /**
     * 根据 name 和 age 生成哈希码。
     * <p>{@link Objects#hash} 是 JDK 7+ 推荐的方式，自动处理 null。</p>
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }

    @Override
    public String toString() {
        return name + "(" + age + ")";
    }

    public String getName() { return name; }
    public int getAge() { return age; }
}
