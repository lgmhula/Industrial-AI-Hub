package code.day02.practice; /**
 * 实战题 A：菱形图案打印
 *
 * 题目要求：
 *   用户输入一个奇数 n（3-21），程序打印一个由星号组成的菱形。
 *   例如 n=5：
 *       *
 *      ***
 *     *****
 *      ***
 *       *
 *
 * 考察点：嵌套 for 循环、空格控制、对称思维
 *
 * 进阶要求（做完基础后挑战）：
 *   1. 输入必须是奇数，否则提示并重新输入
 *   2. 支持空心菱形（只打印边缘）
 *
 * 运行：javac PracticeA_Diamond.java && java PracticeA_Diamond
 */
import java.util.Scanner;

public class PracticeA_Diamond {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // ---- 基础任务：实心菱形 ----
        System.out.println("===== 实战题 A: 菱形图案打印 =====");
        int n = readOddNumber(scanner);
        int mid = n / 2; // 中间行的索引

        System.out.println("\n实心菱形 (n=" + n + "):");
        for (int row = 0; row < n; row++) {
            // 计算当前行应该打印的空格数
            int spaces = Math.abs(mid - row);
            // 计算当前行应该打印的星号数
            int stars = n - 2 * spaces;

            // 打印前置空格
            for (int s = 0; s < spaces; s++) {
                System.out.print(" ");
            }
            // 打印星号
            for (int st = 0; st < stars; st++) {
                System.out.print("*");
            }
            System.out.println();
        }

        // ---- 进阶：空心菱形 ----
        System.out.println("\n空心菱形 (n=" + n + "):");
        for (int row = 0; row < n; row++) {
            int spaces = Math.abs(mid - row);
            int stars = n - 2 * spaces;

            // 打印前置空格
            for (int s = 0; s < spaces; s++) {
                System.out.print(" ");
            }
            // 打印星号（空心：只在两端打印）
            for (int st = 0; st < stars; st++) {
                if (st == 0 || st == stars - 1) {
                    System.out.print("*");
                } else {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }

        scanner.close();
    }

    // 读取奇数：循环直到用户输入合法的奇数
    static int readOddNumber(Scanner scanner) {
        int n;
        while (true) {
            System.out.print("请输入一个奇数 (3-21): ");
            n = scanner.nextInt();
            if (n < 3 || n > 21) {
                System.out.println("  数值必须在 3 到 21 之间！");
                continue;
            }
            if (n % 2 == 0) {
                System.out.println("  必须输入奇数！");
                continue;
            }
            break;
        }
        return n;
    }
}
