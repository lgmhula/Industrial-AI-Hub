package code.day04;

/**
 * 银行账户类 —— Day 4 OOP 练习。
 *
 * <p>模拟一个简易银行账户，支持存款、取款、查询余额。
 * 演示封装的核心价值：通过方法控制数据访问，保护数据完整性。</p>
 *
 * <p>业务规则：
 * <ul>
 *   <li>取款金额不能超过余额</li>
 *   <li>存款和取款金额必须为正数</li>
 *   <li>余额不能为负数</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-09
 */
public class BankAccount {

    /** 账户持有人姓名 */
    private String owner;

    /** 账户余额，不允许直接修改 */
    private double balance;

    /** 账户号 */
    private String accountNumber;

    /**
     * 构造方法 —— 创建一个初始余额为 0 的账户。
     *
     * @param owner         账户持有人姓名
     * @param accountNumber 账户号
     */
    public BankAccount(String owner, String accountNumber) {
        this.owner = owner;
        this.accountNumber = accountNumber;
        this.balance = 0.0;
    }

    /**
     * 存款操作。
     *
     * @param amount 存款金额，必须 &gt; 0
     * @return true 表示存款成功，false 表示金额无效
     */
    public boolean deposit(double amount) {
        if (amount <= 0) {
            System.out.println("  存款失败：金额必须大于 0！");
            return false;
        }
        balance += amount;
        System.out.printf("  存款成功：+%.2f 元，当前余额 %.2f 元%n", amount, balance);
        return true;
    }

    /**
     * 取款操作。
     *
     * @param amount 取款金额，必须 &gt; 0 且 &le; 余额
     * @return true 表示取款成功，false 表示余额不足或金额无效
     */
    public boolean withdraw(double amount) {
        if (amount <= 0) {
            System.out.println("  取款失败：金额必须大于 0！");
            return false;
        }
        if (amount > balance) {
            System.out.printf("  取款失败：余额不足（余额 %.2f，需要 %.2f）%n", balance, amount);
            return false;
        }
        balance -= amount;
        System.out.printf("  取款成功：-%.2f 元，当前余额 %.2f 元%n", amount, balance);
        return true;
    }

    /**
     * 查询余额。
     *
     * @return 当前余额
     */
    public double getBalance() {
        return balance;
    }

    /**
     * 获取账户号。
     *
     * @return 账户号
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * 获取账户持有人。
     *
     * @return 持有者姓名
     */
    public String getOwner() {
        return owner;
    }

    /**
     * 返回账户摘要信息。
     *
     * @return 格式为 "账户号 持有人 余额"
     */
    @Override
    public String toString() {
        return String.format("[%s] %s: ¥%.2f", accountNumber, owner, balance);
    }
}
