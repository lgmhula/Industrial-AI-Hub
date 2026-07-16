package code.day04;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Day 04: 面向对象基础 (OOP Fundamentals)。
 *
 * <p>本类作为主程序入口，演示：
 * <ol>
 *   <li>使用 {@link Student} 类创建对象、对象数组、排序</li>
 *   <li>使用 {@link BankAccount} 类进行存款/取款/余额查询</li>
 *   <li>封装的好处：通过方法控制数据，保护数据不被意外修改</li>
 * </ol>
 *
 * <p>编译：在 Reboot 根目录执行 {@code javac code/day04/*.java}</p>
 * <p>运行：在 Reboot 根目录执行 {@code java code.day04.Day04_OOP}</p>
 *
 * @author Reboot
 * @version 1.0
 * @see Student
 * @see BankAccount
 * @since 2026-07-09
 */
public class Day04_OOP {

    public static void main(String[] args) {
        studentDemo();
        bankDemo();
    }

    /**
     * 演示 Student 类：创建对象、对象数组、按成绩排序。
     */
    private static void studentDemo() {
        System.out.println("========== Student 类练习 ==========");

        // 使用全参构造创建对象
        Student s1 = new Student("张三", 20, 85.5);
        Student s2 = new Student("李四", 21, 92.0);
        Student s3 = new Student("王五", 19, 78.0);
        Student s4 = new Student("赵六", 22, 65.5);

        // 使用无参构造 + setter 创建
        Student s5 = new Student();
        s5.setName("钱七");
        s5.setAge(20);
        s5.setScore(88.0);

        System.out.println("\n五位学生：");
        Student[] students = {s1, s2, s3, s4, s5};
        for (Student s : students) {
            System.out.println("  " + s);
        }

        // 按成绩降序排序（使用 Comparator.comparingDouble）
        System.out.println("\n按成绩降序排序后：");
        Arrays.sort(students, Comparator.comparingDouble(Student::getScore).reversed());
        for (int i = 0; i < students.length; i++) {
            System.out.printf("  第%d名: %s%n", i + 1, students[i]);
        }

        // this 关键字说明
        System.out.println("\n--- this 关键字说明 ---");
        System.out.println("this.name = name;  ← 左边 this.name 是成员变量，右边 name 是参数");
        System.out.println("当局部变量和成员变量同名时，this 用来区分它们。");
    }

    /**
     * 演示 BankAccount 类：存款、取款、余额保护。
     */
    private static void bankDemo() {
        System.out.println("\n========== BankAccount 类练习 ==========");

        BankAccount account = new BankAccount("张三", "6222-0234-5678-9012");

        System.out.println("\n创建账户: " + account);

        // 存款测试
        account.deposit(1000);
        account.deposit(500.50);
        account.deposit(-100);  // 无效金额

        // 取款测试
        account.withdraw(300);
        account.withdraw(2000); // 余额不足
        account.withdraw(-50);  // 无效金额

        System.out.println("\n最终账户状态: " + account);
        System.out.printf("余额: ¥%.2f%n", account.getBalance());

        // 封装的价值说明
        System.out.println("\n--- 封装的价值 ---");
        System.out.println("balance 是 private，外部无法直接 account.balance = 999;");
        System.out.println("必须通过 deposit()/withdraw() 方法，方法内部可以：");
        System.out.println("  1. 校验参数合法性");
        System.out.println("  2. 执行业务规则");
        System.out.println("  3. 记录操作日志（将来可以扩展）");
    }
}
