package code.day05;

/**
 * 经理类 —— 继承自 {@link Employee}。
 *
 * <p>经理有额外奖金，薪资 = 基础薪资 + 管理奖金。</p>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-10
 */
public class Manager extends Employee {

    /** 管理奖金 */
    private double bonus;

    /**
     * 构造方法。
     *
     * @param name       姓名
     * @param id         员工ID
     * @param baseSalary 基础薪资
     * @param bonus      管理奖金
     */
    public Manager(String name, String id, double baseSalary, double bonus) {
        super(name, id, baseSalary);
        this.bonus = bonus;
    }

    /**
     * 经理的工作内容。
     */
    @Override
    public void work() {
        System.out.println(name + "（经理）正在开会、审批、管理团队...");
    }

    /**
     * 经理的薪资 = 基础薪资 + 管理奖金。
     *
     * @return 实际薪资
     */
    @Override
    public double getSalary() {
        return baseSalary + bonus;
    }

    public double getBonus() { return bonus; }

    @Override
    public String toString() {
        return String.format("[%s] %s（经理）| 基础: ¥%.0f + 奖金: ¥%.0f = ¥%.0f",
                id, name, baseSalary, bonus, getSalary());
    }
}
