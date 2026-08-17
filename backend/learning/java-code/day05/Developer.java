package code.day05;

/**
 * 开发工程师类 —— 继承自 {@link Employee}。
 *
 * <p>开发者按加班小时获得加班费。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-10
 */
public class Developer extends Employee {

    /** 本月加班小时数 */
    private int overtimeHours;

    /** 加班时薪 */
    private static final double OVERTIME_RATE = 80.0;

    /**
     * 构造方法。
     *
     * @param name          姓名
     * @param id            员工ID
     * @param baseSalary    基础薪资
     * @param overtimeHours 本月加班小时
     */
    public Developer(String name, String id, double baseSalary, int overtimeHours) {
        super(name, id, baseSalary);
        this.overtimeHours = overtimeHours;
    }

    @Override
    public void work() {
        System.out.println(name + "（开发）正在写代码、修Bug、Code Review...");
    }

    /**
     * 开发者薪资 = 基础薪资 + 加班费。
     */
    @Override
    public double getSalary() {
        return baseSalary + overtimeHours * OVERTIME_RATE;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s（开发）| 基础: ¥%.0f + 加班: %dh×¥%.0f = ¥%.0f",
                id, name, baseSalary, overtimeHours, OVERTIME_RATE, getSalary());
    }
}
