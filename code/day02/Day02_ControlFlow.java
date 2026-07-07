/**
 * Day 02: 控制流全面恢复
 * 内容：if/else, switch, for, while, do-while, break, continue
 *
 * 运行：javac Day02_ControlFlow.java && java Day02_ControlFlow
 */
import java.util.Random;
import java.util.Scanner;

public class Day02_ControlFlow {

    public static void main(String[] args) {
        multiplicationTable();  // 练习1: 九九乘法表
        leapYear();             // 练习2: 判断闰年
        guessNumber();          // 练习3: 猜数字游戏
        primeNumbers();         // 练习4: 1-100 质数
        menu();                 // 练习5: switch 菜单
    }

    // ---- 练习 1：九九乘法表 (for 循环) ----
    static void multiplicationTable() {
        System.out.println("========== 练习 1: 九九乘法表 ==========");

        // 标准 9x9 乘法表
        for (int i = 1; i <= 9; i++) {
            for (int j = 1; j <= i; j++) {
                // printf 格式化输出，%-4d 左对齐占 4 位
                System.out.printf("%d×%d=%-4d", j, i, i * j);
            }
            System.out.println();
        }

        // for 循环的结构拆解：
        // for (初始化; 条件; 迭代) { 循环体 }
        //   - 初始化：int i = 1     —— 只在循环开始前执行一次
        //   - 条件：i <= 9          —— 每次循环前检查，false 则退出
        //   - 迭代：i++             —— 每次循环体执行完后执行
        System.out.println("\nfor 循环解剖:");
        System.out.print("i 的值变化: ");
        for (int i = 1; i <= 5; i++) {
            System.out.print(i + " ");
        }
        System.out.println("<-- i=6 时条件 i<=5 为 false, 退出");
    }

    // ---- 练习 2：判断闰年 (if/else) ----
    static void leapYear() {
        System.out.println("\n========== 练习 2: 判断闰年 ==========");

        // 闰年规则：
        // 1. 能被 4 整除但不能被 100 整除，或者
        // 2. 能被 400 整除
        int[] years = {2000, 2024, 2100, 2026, 1900, 2028};

        for (int year : years) {
            boolean isLeap = false;

            // 多层 if/else 写法
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

        // 用一行表达式更简洁
        System.out.println("\n使用表达式:");
        for (int year : years) {
            boolean isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
            System.out.printf("%d 年: %s%n", year, isLeap ? "闰年" : "平年");
        }

        // 三元运算符：condition ? trueValue : falseValue
        // if 是语句，不能作为表达式；三元是表达式，可以赋值
    }

    // ---- 练习 3：猜数字游戏 (while + do-while + 随机数) ----
    static void guessNumber() {
        System.out.println("\n========== 练习 3: 猜数字游戏 ==========");

        Random random = new Random();
        int target = random.nextInt(100) + 1; // 1~100 随机数
        Scanner scanner = new Scanner(System.in);

        int guess;
        int attempts = 0;

        System.out.println("(目标数字已生成: 1~100，你来猜)");

        // while 循环：先检查条件，再执行
        // 适合"可能一次都不需要执行"的场景
        while (true) {
            System.out.print("请输入你猜的数字 (1-100): ");
            guess = scanner.nextInt();
            attempts++;

            if (guess == target) {
                System.out.println("恭喜！猜对了！共用了 " + attempts + " 次尝试。");
                break; // break 跳出当前循环
            } else if (guess > target) {
                System.out.println("太大了，再试试。");
            } else {
                System.out.println("太小了，再试试。");
            }
        }

        // while vs do-while 对比
        System.out.println("\n--- while vs do-while 区别 ---");
        System.out.println("目标数字是: " + target);

        // do-while：先执行一次，再检查条件
        // 至少执行一次循环体
        int count = 0;
        do {
            count++;
            System.out.println("do-while 第 " + count + " 次执行");
        } while (count < 1); // count=1 时已不满足，但因为先执行再检查，所以执行了 1 次

        scanner.close();
    }

    // ---- 练习 4：输出 1-100 的质数 (双重 for + break) ----
    static void primeNumbers() {
        System.out.println("\n========== 练习 4: 1-100 的质数 ==========");

        int count = 0;

        for (int num = 2; num <= 100; num++) {
            boolean isPrime = true;

            // 检查能否被 2 到 sqrt(num) 之间的数整除
            // Math.sqrt 开平方：如果 num 有因数，至少一个 <= sqrt(num)
            for (int divisor = 2; divisor <= Math.sqrt(num); divisor++) {
                if (num % divisor == 0) {
                    isPrime = false;
                    break; // 找到一个因数就不用继续找了，跳出内层循环
                }
            }

            if (isPrime) {
                System.out.printf("%3d ", num);
                count++;
                if (count % 10 == 0) {
                    System.out.println(); // 每 10 个换行
                }
            }
        }
        System.out.println("\n1-100 之间共有 " + count + " 个质数。");

        // continue 示例：跳过偶数
        System.out.println("\n--- continue 示例：只打印奇数 ---");
        for (int i = 1; i <= 20; i++) {
            if (i % 2 == 0) {
                continue; // 跳过本次循环的剩余部分，直接进入下一次迭代
            }
            System.out.print(i + " ");
        }
        System.out.println();

        // break 示例：找到第一个就停
        System.out.println("--- break 示例：找第一个能被 7 整除且余 2 的数 ---");
        for (int i = 1; i <= 100; i++) {
            if (i % 7 == 2) {
                System.out.println("找到了: " + i + " (7×" + (i / 7) + "+2)");
                break; // 找到就退出
            }
        }
    }

    // ---- 练习 5：switch 简易菜单 ----
    static void menu() {
        System.out.println("\n========== 练习 5: switch 简易菜单 ==========");
        Scanner scanner = new Scanner(System.in);

        boolean running = true;

        while (running) {
            System.out.println("\n=== 简易计算器菜单 ===");
            System.out.println("1. 加法");
            System.out.println("2. 减法");
            System.out.println("3. 乘法");
            System.out.println("4. 除法");
            System.out.println("5. 余数");
            System.out.println("0. 退出");
            System.out.print("请选择操作: ");

            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    System.out.println("你选择了加法");
                    System.out.print("输入两个数: ");
                    System.out.println("结果: " + (scanner.nextInt() + scanner.nextInt()));
                    break;
                case 2:
                    System.out.println("你选择了减法");
                    System.out.print("输入两个数: ");
                    System.out.println("结果: " + (scanner.nextInt() - scanner.nextInt()));
                    break;
                case 3:
                    System.out.println("你选择了乘法");
                    System.out.print("输入两个数: ");
                    System.out.println("结果: " + (scanner.nextInt() * scanner.nextInt()));
                    break;
                case 4:
                    System.out.println("你选择了除法");
                    System.out.print("输入两个数: ");
                    int dividend = scanner.nextInt();
                    int divisor = scanner.nextInt();
                    if (divisor == 0) {
                        System.out.println("除数不能为 0！");
                    } else {
                        System.out.println("结果: " + ((double) dividend / divisor));
                    }
                    break;
                case 5:
                    System.out.println("你选择了取余");
                    System.out.print("输入两个数: ");
                    System.out.println("结果: " + (scanner.nextInt() % scanner.nextInt()));
                    break;
                case 0:
                    System.out.println("退出程序。");
                    running = false;
                    break;
                default:
                    System.out.println("无效选择，请输入 0-5 之间的数字。");
                    // 没有 break 也可以，switch 会自然结束
            }

            // switch 知识点：
            // 1. switch 支持 int, char, String, enum (Java 7+ 支持 String)
            // 2. 每个 case 必须有 break，否则会"穿透"到下一个 case
            // 3. default 相当于 else，所有 case 都不匹配时执行
        }

        scanner.close();

        // switch 穿透演示
        System.out.println("\n--- switch 穿透 (fall-through) 演示 ---");
        int month = 8;
        String season;
        switch (month) {
            case 12: case 1: case 2:
                season = "冬季";
                break;
            case 3: case 4: case 5:
                season = "春季";
                break;
            case 6: case 7: case 8:
                season = "夏季";
                break;
            case 9: case 10: case 11:
                season = "秋季";
                break;
            default:
                season = "无效月份";
        }
        System.out.println(month + " 月是 " + season);
    }
}
