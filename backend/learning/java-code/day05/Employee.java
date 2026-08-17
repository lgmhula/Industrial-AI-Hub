package code.day05;

/**
 * 员工基类 —— OOP 多态演示。
 *
 * <p>定义了所有员工的共同属性和行为。
 * 子类 {@link Manager} 和 {@link Developer} 将重写 {@code work()} 和 {@code getSalary()}。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-10
 */
public class Employee {

    /** 员工姓名 */
    protected String name;

    /** 员工ID */
    protected String id;

    /** 基础薪资 */
    protected double baseSalary;

    /**
     * 构造方法。
     *
     * @param name       姓名
     * @param id         员工ID
     * @param baseSalary 基础薪资
     */
    public Employee(String name, String id, double baseSalary) {
        this.name = name;
        this.id = id;
        this.baseSalary = baseSalary;
    }

    /**
     * 工作 —— 子类重写以描述具体工作内容。
     */
    public void work() {
        System.out.println(name + " 正在工作中...");
    }

    /**
     * 计算实际薪资 —— 子类重写以加入奖金/补贴。
     *
     * @return 实际薪资
     */
    public double getSalary() {
        return baseSalary;
    }

    /**
     * 员工信息摘要。
     *
     * @return 格式化的员工信息
     */
    @Override
    public String toString() {
        return String.format("[%s] %s | 基础薪资: ¥%.0f | 实际: ¥%.0f",
                id, name, baseSalary, getSalary());
    }

    public String getName() { return name; }
    public String getId() { return id; }
}
