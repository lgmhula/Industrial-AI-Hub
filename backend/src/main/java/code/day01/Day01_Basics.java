package code.day01;

/**
 * Day 01: Java 基础语法恢复
 * 内容：HelloWorld + 基本数据类型 + 运算符 + 控制台输入 + 字符串操作
 * 运行：mvn exec:java -Dexec.mainClass=code.day01.Day01_Basics
 */
import java.util.Scanner;

public class Day01_Basics {

    public static void main(String[] args) {
        helloWorld();
        basicTypes();
        calculator();
        stringPractice();
    }

    // ---- 练习 1：Hello World ----
    static void helloWorld() {
        System.out.println("========== 练习 1: Hello World ==========");
        System.out.println("Hello, 软件工程师之路 (Reboot) Day 1!");
        System.out.println("今天是: " + java.time.LocalDate.now());
    }

    // ---- 练习 2：8 种基本数据类型 ----
    static void basicTypes() {
        System.out.println("\n========== 练习 2: 8 种基本数据类型 ==========");

        // 整型
        byte b = 127;
        short s = 32767;
        int i = 2147483647;
        long l = 9223372036854775807L; // 注意 L 后缀

        // 浮点型
        float f = 3.1415926f;  // 注意 f 后缀
        double d = 3.141592653589793;

        // 字符型
        char c = 'A';

        // 布尔型
        boolean bool = true;

        System.out.println("byte:    " + b + "  (占 1 字节, 范围: -128 ~ 127)");
        System.out.println("short:   " + s + "  (占 2 字节, 范围: -32768 ~ 32767)");
        System.out.println("int:     " + i + "  (占 4 字节)");
        System.out.println("long:    " + l + "  (占 8 字节)");
        System.out.println("float:   " + f + "  (占 4 字节, 单精度)");
        System.out.println("double:  " + d + "  (占 8 字节, 双精度)");
        System.out.println("char:    " + c + "  (占 2 字节, Unicode)");
        System.out.println("boolean: " + bool + "  (只有 true/false)");

        // 类型转换
        int x = 100;
        long y = x;           // 自动转换 (小 → 大, 安全)
        int z = (int) y;      // 强制转换 (大 → 小, 可能丢失精度)
        System.out.println("\n自动转换 int→long: " + y);
        System.out.println("强制转换 long→int: " + z);

        // 各类型默认值 (作为成员变量时)
        System.out.println("\n--- 类型范围 ---");
        System.out.println("int 最大值:    " + Integer.MAX_VALUE);
        System.out.println("int 最小值:    " + Integer.MIN_VALUE);
        System.out.println("double 最大值: " + Double.MAX_VALUE);
        System.out.println("double 最小值: " + Double.MIN_VALUE);
    }

    // ---- 练习 3：四则运算计算器 (控制台输入) ----
    static void calculator() {
        System.out.println("\n========== 练习 3: 四则运算计算器 ==========");
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.print("请输入第一个数字: ");
            double num1 = scanner.nextDouble();

            System.out.print("请输入运算符 (+, -, *, /): ");
            char operator = scanner.next().charAt(0);

            System.out.print("请输入第二个数字: ");
            double num2 = scanner.nextDouble();

            double result;
            switch (operator) {
                case '+':
                    result = num1 + num2;
                    break;
                case '-':
                    result = num1 - num2;
                    break;
                case '*':
                    result = num1 * num2;
                    break;
                case '/':
                    if (num2 == 0) {
                        System.out.println("错误: 除数不能为 0!");
                        return;
                    }
                    result = num1 / num2;
                    break;
                default:
                    System.out.println("错误: 不支持的运算符: " + operator);
                    return;
            }
            System.out.printf("%.2f %c %.2f = %.2f%n", num1, operator, num2, result);

            // 运算符优先级演示
            System.out.println("\n--- 运算符优先级示例 ---");
            int a = 10, bb = 3, cc = 2;
            System.out.println("10 + 3 * 2 = " + (a + bb * cc) + "  (乘除优先于加减)");
            System.out.println("(10 + 3) * 2 = " + ((a + bb) * cc) + "  (括号改变优先级)");
            System.out.println("10 / 3 = " + (a / bb) + "  (整数除法截断)");
            System.out.println("10 % 3 = " + (a % bb) + "  (取余/模运算)");
            System.out.println("10.0 / 3 = " + (10.0 / bb) + "  (浮点除法保留小数)");

            // 自增自减
            int n = 5;
            System.out.println("\n--- 自增/自减 ---");
            System.out.println("n = " + n);
            System.out.println("n++ (先用后加): " + (n++));
            System.out.println("现在 n = " + n);
            System.out.println("++n (先加后用): " + (++n));
            System.out.println("现在 n = " + n);

        } finally {
            scanner.close();
        }
    }

    // ---- 练习 4：字符串操作 ----
    static void stringPractice() {
        System.out.println("\n========== 练习 4: 字符串操作 ==========");

        String str = "  Hello, Java Reboot 2026!  ";
        System.out.println("原始字符串:  \"" + str + "\"");

        // 长度
        System.out.println("长度: " + str.length());

        // 去空格
        System.out.println("trim(): \"" + str.trim() + "\"");

        // 大小写
        System.out.println("toUpperCase(): \"" + str.toUpperCase() + "\"");
        System.out.println("toLowerCase(): \"" + str.toLowerCase() + "\"");

        // 子串
        System.out.println("substring(10): \"" + str.substring(10) + "\"");
        System.out.println("substring(10, 15): \"" + str.substring(10, 15) + "\"");

        // 查找
        System.out.println("indexOf('J'): " + str.indexOf('J'));
        System.out.println("indexOf(\"Reboot\"): " + str.indexOf("Reboot"));
        System.out.println("contains(\"Java\"): " + str.contains("Java"));

        // 替换
        System.out.println("replace('a', '@'): \"" + str.replace('a', '@') + "\"");
        System.out.println("replaceAll(\"\\\\s+\", \"\"): \"" + str.replaceAll("\\s+", "") + "\"");

        // 拼接 (StringBuilder 性能更优)
        StringBuilder sb = new StringBuilder();
        sb.append("Day ").append(1).append(": ");
        sb.append("Java 基础恢复");
        System.out.println("\nStringBuilder 拼接: \"" + sb.toString() + "\"");

        // 字符串比较 (重要！)
        String s1 = new String("hello");
        String s2 = new String("hello");
        System.out.println("\n--- 字符串比较陷阱 ---");
        System.out.println("s1 == s2:       " + (s1 == s2) + "  ← 比较的是内存地址, 所以是 false");
        System.out.println("s1.equals(s2):  " + s1.equals(s2) + "  ← equals() 比较内容, 所以是 true");

        // 分割
        String csv = "设备,温度,湿度,压力";
        String[] parts = csv.split(",");
        System.out.println("\n分割字符串:");
        for (String part : parts) {
            System.out.println("  " + part);
        }
    }
}
