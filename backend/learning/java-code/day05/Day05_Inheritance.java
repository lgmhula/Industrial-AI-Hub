package code.day05;

/**
 * Day 05: 继承与多态。
 *
 * <p>两个演示场景：
 * <ol>
 *   <li>动物体系：继承/super/重写/多态调用</li>
 *   <li>员工管理系统：多态的实际应用 —— 统一处理不同类型员工</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-10
 */
public class Day05_Inheritance {

    public static void main(String[] args) {
        animalDemo();
        employeeDemo();
    }

    /**
     * 演示动物继承体系 + 多态。
     */
    private static void animalDemo() {
        System.out.println("========== 动物继承体系 ==========\n");

        Dog dog = new Dog("旺财", "金毛");
        Cat cat = new Cat("咪咪", 9);

        // 各自调用自己的方法
        System.out.println("--- 各自调用 ---");
        dog.makeSound();
        cat.makeSound();
        dog.eat();
        cat.eat();
        dog.wagTail();
        cat.scratch();

        // 多态：父类引用指向子类对象
        System.out.println("\n--- 多态：Animal 引用 ---");
        Animal a1 = new Dog("小黑", "柴犬");
        Animal a2 = new Cat("小花", 7);

        // 通过父类引用调用，实际执行的是子类的方法（动态绑定）
        a1.makeSound();  // 输出狗叫
        a2.makeSound();  // 输出猫叫
        a1.eat();        // 输出狗吃
        a2.eat();        // 输出猫吃

        // 多态数组：统一处理不同类型的动物
        System.out.println("\n--- 多态数组 ---");
        Animal[] zoo = {
            new Dog("大黄", "中华田园犬"),
            new Cat("橘子", 8),
            new Dog("阿福", "柯基"),
            new Cat("奶糖", 6)
        };
        for (Animal a : zoo) {
            a.makeSound();  // 运行时自动选择正确的子类方法
        }

        // instanceof 类型检查
        System.out.println("\n--- instanceof 检查 ---");
        for (Animal a : zoo) {
            if (a instanceof Dog) {
                ((Dog) a).wagTail();  // 向下转型后调用子类特有方法
            } else if (a instanceof Cat) {
                ((Cat) a).scratch();
            }
        }
    }

    /**
     * 演示员工管理系统中的多态应用。
     */
    private static void employeeDemo() {
        System.out.println("\n========== 员工管理系统 ==========\n");

        Employee[] team = {
            new Manager("张总", "M001", 20000, 8000),
            new Developer("李工", "D001", 15000, 20),
            new Developer("王码", "D002", 12000, 35),
            new Manager("赵管", "M002", 18000, 5000)
        };

        // 多态：统一遍历，各自执行自己的 work() 和 getSalary()
        System.out.println("--- 团队成员工作 ---");
        for (Employee e : team) {
            e.work();
        }

        System.out.println("\n--- 本月薪资表 ---");
        double totalPayroll = 0;
        for (Employee e : team) {
            System.out.println("  " + e);
            totalPayroll += e.getSalary();
        }
        System.out.printf("%n  本月总薪资支出: ¥%.0f%n", totalPayroll);

        // 多态的核心价值
        System.out.println("\n--- 多态的核心价值 ---");
        System.out.println("1. 用父类类型统一管理所有子类对象");
        System.out.println("2. 新增子类（如 Intern）无需修改现有代码");
        System.out.println("3. 运行时动态绑定：同一个方法调用，执行不同子类的实现");
    }
}
