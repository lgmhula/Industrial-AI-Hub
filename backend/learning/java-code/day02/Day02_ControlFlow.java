package code.day02;

import java.util.Random;
import java.util.Scanner;

/**
 * Day 02: 控制流全面恢复
 * <p>
 * 本类用于复习 Java 核心控制流语法，包括：
 * <ul>
 *     <li>if/else 条件判断</li>
 *     <li>switch 多分支选择</li>
 *     <li>for 循环</li>
 *     <li>while 循环</li>
 *     <li>do-while 循环</li>
 *     <li>break 和 continue 跳转控制</li>
 * </ul>
 * <p>
 * 包含以下练习：
 * <ol>
 *     <li>九九乘法表</li>
 *     <li>判断闰年</li>
 *     <li>猜数字游戏</li>
 *     <li>1-100 质数输出</li>
 *     <li>switch 简易菜单</li>
 * </ol>
 *
 * @author hula
 * @version 2.0
 * @see java.util.Random
 * @see java.util.Scanner
 * @since 2026-07-07
 */
public class Day02_ControlFlow {

    /*
    ========== 常量定义（消除魔法值） ==========
     */

    private static final int MULTIPLICATION_MAX = 9;
    private static final int LOOP_DEMO_MAX = 5;
    private static final int PRIME_RANGE_MAX = 100;
    private static final int ODD_RANGE_MAX = 20;
    private static final int BREAK_SEARCH_MAX = 100;
    private static final int BREAK_SEARCH_DIVISOR = 7;
    private static final int BREAK_SEARCH_REMAINDER = 2;
    private static final int GUESS_RANGE_MAX = 100;

    /**
     * 程序主入口方法
     * <p>
     * 使用 try-with-resources 统一管理 Scanner 资源，
     * 通过参数传递给各个需要用户输入的方法。
     * <p>
     * 依次执行所有练习方法：
     * <ul>
     *     <li>{@link #multiplicationTable()} - 九九乘法表</li>
     *     <li>{@link #leapYear()} - 判断闰年</li>
     *     <li>{@link #guessNumber(Scanner)} - 猜数字游戏</li>
     *     <li>{@link #primeNumbers()} - 1-100 质数</li>
     *     <li>{@link #menu(Scanner)} - switch 菜单</li>
     * </ul>
     *
     * @param args 命令行参数（当前版本未使用）
     */
    public static void main(String[] args) {
        // try-with-resources: 资源统一管理，自动关闭
        try (Scanner scanner = new Scanner(System.in)) {
            multiplicationTable();
            leapYear();
            guessNumber(scanner);
            primeNumbers();
            menu(scanner);
        } // scanner 自动关闭
    }

    // ======================================================================
    // 练习 1：九九乘法表
    // ======================================================================

    /**
     * 练习 1：使用 for 循环输出九九乘法表
     * <p>
     * 双重 for 循环实现标准 9x9 乘法表，格式为 {@code j × i = i*j}。
     * <p>
     * 输出示例：
     * <pre>
     * 1×1=1
     * 1×2=2   2×2=4
     * 1×3=3   2×3=6   3×3=9
     * ...
     * </pre>
     * <p>
     * 同时演示了 for 循环的结构拆解，帮助理解循环执行流程。
     *
     * @see #main(String[])
     */
    static void multiplicationTable() {
        System.out.println("========== 练习 1: 九九乘法表 ==========");

        for (int i = 1; i <= MULTIPLICATION_MAX; i++) {
            for (int j = 1; j <= i; j++) {
                System.out.printf("%d×%d=%-4d", j, i, i * j);
            }
            System.out.println();
        }

        System.out.println("\nfor 循环解剖:");
        System.out.print("i 的值变化: ");
        for (int i = 1; i <= LOOP_DEMO_MAX; i++) {
            System.out.print(i + " ");
        }
        System.out.println("<-- i=6 时条件 i<=5 为 false, 退出");
    }

    // ======================================================================
    // 练习 2：判断闰年
    // ======================================================================

    /**
     * 练习 2：使用 if/else 判断指定年份是否为闰年
     * <p>
     * 闰年判定规则：
     * <ul>
     *     <li><b>普通闰年</b>：能被 4 整除，但不能被 100 整除</li>
     *     <li><b>世纪闰年</b>：能被 400 整除</li>
     * </ul>
     * <p>
     * 示例年份：2000（闰年）、2024（闰年）、2100（平年）、2026（平年）
     * <p>
     * 同时演示了两种写法：
     * <ol>
     *     <li>多层 if/else 写法（易读性强）</li>
     *     <li>三元运算符表达式写法（简洁）</li>
     * </ol>
     *
     * @see #main(String[])
     */
    static void leapYear() {
        System.out.println("\n========== 练习 2: 判断闰年 ==========");

        int[] years = {2000, 2024, 2100, 2026, 1900, 2028};

        System.out.println("--- 多层 if/else 写法 ---");
        for (int year : years) {
            boolean isLeap;
            if (year % 400 == 0) {
                isLeap = true;
            } else if (year % 100 == 0) {
                isLeap = false;
            } else if (year % 4 == 0) {
                isLeap = true;
            } else {
                isLeap = false;
            }
            System.out.printf("%d 年: %s%n", year, isLeap ? "是闰年" : "不是闰年");
        }

        System.out.println("\n--- 表达式写法（简洁） ---");
        for (int year : years) {
            // ✅ 直接用布尔表达式，无需 if
            boolean isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
            System.out.printf("%d 年: %s%n", year, isLeap ? "闰年" : "平年");
        }
    }

    // ======================================================================
    // 练习 3：猜数字游戏
    // ======================================================================

    /**
     * 练习 3：猜数字游戏
     * <p>
     * 程序生成一个 1-100 的随机整数，用户通过输入猜测数字，
     * 程序给出"大了"或"小了"的提示，直到猜中为止。
     * <p>
     * 使用的核心语法：
     * <ul>
     *     <li>{@link Random} - 生成随机数</li>
     *     <li>{@link Scanner} - 接收用户输入</li>
     *     <li>{@code while (true)} + {@code break} - 无限循环直到猜中</li>
     * </ul>
     * <p>
     * 同时演示了 {@code while} 与 {@code do-while} 的区别：
     * <ul>
     *     <li>{@code while}：先判断条件，后执行（可能执行 0 次）</li>
     *     <li>{@code do-while}：先执行一次，后判断条件（至少执行 1 次）</li>
     * </ul>
     *
     * @param scanner 共享的 Scanner 对象（由 main 传入）
     * @see #main(String[])
     * @see Random#nextInt(int)
     */
    static void guessNumber(Scanner scanner) {
        System.out.println("\n========== 练习 3: 猜数字游戏 ==========");

        Random random = new Random();
        int target = random.nextInt(GUESS_RANGE_MAX) + 1;
        int guess;
        int attempts = 0;

        System.out.println("(目标数字已生成: 1~100，你来猜)");

        while (true) {
            System.out.print("请输入你猜的数字 (1-100): ");
            guess = scanner.nextInt();
            attempts++;

            if (guess == target) {
                System.out.println("恭喜！猜对了！共用了 " + attempts + " 次尝试。");
                break;
            } else if (guess > target) {
                System.out.println("太大了，再试试。");
            } else {
                System.out.println("太小了，再试试。");
            }
        }

        // while vs do-while 对比演示
        System.out.println("\n--- while vs do-while 区别 ---");
        System.out.println("目标数字是: " + target);

        // 演示 do-while：至少执行一次
        int count = 0;
        do {
            count++;
            System.out.println("do-while 第 " + count + " 次执行");
            // 条件为 false，但循环体仍执行了 1 次
        } while (count < 0);
        System.out.println("do-while 即使条件为 false，也至少执行 1 次 ✓");

        // 对比 while：条件为 false 时一次都不执行
        count = 0;
        while (count < 0) {
            count++;
            System.out.println("while 第 " + count + " 次执行");
        }
        System.out.println("while 条件为 false 时，一次都不执行 ✓");
    }

    // ======================================================================
    // 练习 4：1-100 质数
    // ======================================================================

    /**
     * 练习 4：输出 1-100 之间的所有质数
     * <p>
     * 质数定义：在大于 1 的自然数中，除了 1 和它本身以外不再有其他因数。
     * <p>
     * 核心算法：
     * <ul>
     *     <li>外层循环遍历 2-100</li>
     *     <li>内层循环从 2 检查到 {@code sqrt(num)}（数学优化，减少循环次数）</li>
     *     <li>若找到因数，使用 {@code break} 提前退出内层循环</li>
     * </ul>
     * <p>
     * 同时演示了：
     * <ul>
     *     <li>{@code continue} - 跳过偶数输出</li>
     *     <li>{@code break} - 找到第一个匹配项后终止循环</li>
     * </ul>
     *
     * @see #main(String[])
     * @see Math#sqrt(double)
     */
    static void primeNumbers() {
        System.out.println("\n========== 练习 4: 1-100 的质数 ==========");

        int count = 0;

        for (int num = 2; num <= PRIME_RANGE_MAX; num++) {
            boolean isPrime = true;

            // 检查到 sqrt(num) 即可
            for (int divisor = 2; divisor <= Math.sqrt(num); divisor++) {
                if (num % divisor == 0) {
                    isPrime = false;
                    break;  // 找到因数，提前退出内层循环
                }
            }

            if (isPrime) {
                System.out.printf("%3d ", num);
                count++;
                if (count % 10 == 0) {
                    System.out.println();
                }
            }
        }
        System.out.println("\n1-100 之间共有 " + count + " 个质数。");

        // continue 示例：跳过偶数
        System.out.println("\n--- continue 示例：只打印奇数 ---");
        for (int i = 1; i <= ODD_RANGE_MAX; i++) {
            if (i % 2 == 0) {
                continue;  // 跳过本次循环的剩余部分
            }
            System.out.print(i + " ");
        }
        System.out.println();

        // break 示例：找到第一个就停
        System.out.println("--- break 示例：找第一个能被 7 整除且余 2 的数 ---");
        for (int i = 1; i <= BREAK_SEARCH_MAX; i++) {
            if (i % BREAK_SEARCH_DIVISOR == BREAK_SEARCH_REMAINDER) {
                System.out.println("找到了: " + i + " (7×" + (i / BREAK_SEARCH_DIVISOR) + "+2)");
                break;  // 找到就退出
            }
        }
    }

    // ======================================================================
    // 练习 5：switch 简易菜单
    // ======================================================================

    /**
     * 练习 5：使用 switch 实现简易计算器菜单
     * <p>
     * 功能包括：
     * <ul>
     *     <li>1. 加法</li>
     *     <li>2. 减法</li>
     *     <li>3. 乘法</li>
     *     <li>4. 除法（含除数不为 0 的校验）</li>
     *     <li>5. 取余</li>
     *     <li>0. 退出</li>
     * </ul>
     * <p>
     * switch 知识点：
     * <ul>
     *     <li>支持类型：{@code int}、{@code char}、{@link String}、{@code enum}</li>
     *     <li>每个 {@code case} 必须有 {@code break}，否则会发生"穿透"</li>
     *     <li>{@code default} 相当于 else，处理所有未匹配的情况</li>
     * </ul>
     * <p>
     * 同时演示了 switch 穿透技巧：多个 case 共享同一段代码（如季节判断）。
     *
     * @param scanner 共享的 Scanner 对象（由 main 传入）
     * @see #main(String[])
     * @see Scanner
     */
    static void menu(Scanner scanner) {
        System.out.println("\n========== 练习 5: switch 简易菜单 ==========");

        boolean running = true;

        while (running) {
            printMenu();
            int choice = scanner.nextInt();

            // ✅ 增强 switch（Java 14+）
            running = processChoice(choice, scanner);
        }

        // switch 穿透演示
        showFallThroughDemo();
    }

    /**
     * 打印菜单
     */
    private static void printMenu() {
        System.out.println("\n=== 简易计算器菜单 ===");
        System.out.println("1. 加法");
        System.out.println("2. 减法");
        System.out.println("3. 乘法");
        System.out.println("4. 除法");
        System.out.println("5. 余数");
        System.out.println("0. 退出");
        System.out.print("请选择操作: ");
    }

    /**
     * 处理用户选择（Java 14+ 增强 switch）
     *
     * @param choice  用户选择
     * @param scanner Scanner 对象
     * @return true 继续运行，false 退出
     */
    private static boolean processChoice(int choice, Scanner scanner) {
        switch (choice) {
            case 1 -> {
                System.out.println("你选择了加法");
                System.out.print("输入两个数: ");
                System.out.println("结果: " + (scanner.nextInt() + scanner.nextInt()));
            }
            case 2 -> {
                System.out.println("你选择了减法");
                System.out.print("输入两个数: ");
                System.out.println("结果: " + (scanner.nextInt() - scanner.nextInt()));
            }
            case 3 -> {
                System.out.println("你选择了乘法");
                System.out.print("输入两个数: ");
                System.out.println("结果: " + (scanner.nextInt() * scanner.nextInt()));
            }
            case 4 -> {
                System.out.println("你选择了除法");
                System.out.print("输入两个数: ");
                int dividend = scanner.nextInt();
                int divisor = scanner.nextInt();
                if (divisor == 0) {
                    System.out.println("除数不能为 0！");
                } else {
                    System.out.println("结果: " + ((double) dividend / divisor));
                }
            }
            case 5 -> {
                System.out.println("你选择了取余");
                System.out.print("输入两个数: ");
                System.out.println("结果: " + (scanner.nextInt() % scanner.nextInt()));
            }
            case 0 -> {
                System.out.println("退出程序。");
                return false;
            }
            default -> System.out.println("无效选择，请输入 0-5 之间的数字。");
        }
        return true;
    }

    /**
     * switch 穿透演示
     * <p>
     * 利用 fall-through 特性，多个 case 共享同一段代码。
     * 用于判断月份所属季节。
     */
    private static void showFallThroughDemo() {
        System.out.println("\n--- switch 穿透 (fall-through) 演示 ---");

        // ✅ Java 14+ 增强 switch 表达式
        int month = 8;
        String season = switch (month) {
            case 12, 1, 2 -> "冬季";
            case 3, 4, 5 -> "春季";
            case 6, 7, 8 -> "夏季";
            case 9, 10, 11 -> "秋季";
            default -> "无效月份";
        };
        System.out.println(month + " 月是 " + season);

        // 传统 switch 穿透写法
        System.out.println("\n--- 传统 fall-through 写法 ---");
        for (int m = 1; m <= 12; m++) {
            String s;
            switch (m) {
                case 12:
                case 1:
                case 2:
                    s = "冬季";
                    break;
                case 3:
                case 4:
                case 5:
                    s = "春季";
                    break;
                case 6:
                case 7:
                case 8:
                    s = "夏季";
                    break;
                case 9:
                case 10:
                case 11:
                    s = "秋季";
                    break;
                default:
                    s = "无效月份";
            }
            System.out.print(s + " ");
        }
        System.out.println();
    }
}
