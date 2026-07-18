package code.day13;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Day 13: String/StringBuilder、包装类、日期时间。
 *
 * <p>本日聚焦 Java 中最常用的几个 API：
 * <ol>
 *   <li>String 常用操作与不可变性</li>
 *   <li>StringBuilder 性能（拼接 10 万次对比）</li>
 *   <li>包装类：自动装箱/拆箱、缓存机制</li>
 *   <li>日期时间：LocalDate/LocalDateTime/Duration/Period</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-13
 */
public class Day13_StringAndDate {

    public static void main(String[] args) {
        stringBasics();
        stringBuilderBenchmark();
        wrapperDemo();
        dateTimeDemo();
    }

    /** String 不可变性与常用 API */
    private static void stringBasics() {
        System.out.println("========== String 基础 ==========\n");

        // String 的不可变性
        String s1 = "Hello";
        String s2 = s1.concat(" World");  // 创建新对象，s1 不变
        System.out.println("s1 拼接后: " + s1 + "  ← 原值不变（不可变）");
        System.out.println("s2 新对象: " + s2);

        // 字符串常量池
        String a = "Java";
        String b = "Java";
        String c = new String("Java");
        System.out.println("\na == b (常量池):      " + (a == b));
        System.out.println("a == c (new 对象):    " + (a == c));
        System.out.println("a.equals(c) (内容比): " + a.equals(c));

        // 常用 API 速览
        String text = "  Hello, Industrial AI Hub!  ";
        System.out.println("\n原文:     \"" + text + "\"");
        System.out.println("length:   " + text.length());
        System.out.println("trim:     \"" + text.trim() + "\"");
        System.out.println("upper:    \"" + text.toUpperCase() + "\"");
        System.out.println("lower:    \"" + text.toLowerCase() + "\"");
        System.out.println("sub(8,17):\"" + text.substring(8, 17) + "\"");
        System.out.println("index Hub:" + text.indexOf("Hub"));
        System.out.println("replace:  \"" + text.replace(' ', '_') + "\"");
        System.out.println("contains: " + text.contains("AI"));
        System.out.println("starts:   " + text.trim().startsWith("Hello"));
        System.out.println("split:    " + String.join(" | ", text.trim().split("[ ,]+")));

        // 格式化
        System.out.printf("\n格式化: %s 运行了 %d 天，进度 %.1f%%%n", "Reboot", 13, 13.0 / 112 * 100);
    }

    /** StringBuilder vs String 拼接性能对比 */
    private static void stringBuilderBenchmark() {
        System.out.println("\n========== StringBuilder 性能 ==========\n");

        final int N = 100_000;

        // String 拼接（每次都创建新对象）
        long t1 = System.nanoTime();
        String s = "";
        for (int i = 0; i < N; i++) {
            s += "a";  // 每次 += 都 new 一个 String
        }
        long t2 = System.nanoTime();

        // StringBuilder 拼接（原地修改）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < N; i++) {
            sb.append("a");
        }
        long t3 = System.nanoTime();

        System.out.printf("String += 拼接 %d 次:  %8.2f ms%n", N, (t2 - t1) / 1e6);
        System.out.printf("StringBuilder     %d 次:  %8.2f ms%n", N, (t3 - t2) / 1e6);
        System.out.println("\n结论：循环拼接必须用 StringBuilder！");

        // StringBuffer 是线程安全版，一般用 StringBuilder
        System.out.println("StringBuffer = StringBuilder + synchronized（线程安全，略慢）");
    }

    /** 包装类：自动装箱/拆箱、缓存 */
    private static void wrapperDemo() {
        System.out.println("\n========== 包装类 ==========\n");

        // 自动装箱 (autoboxing)：基本类型 → 包装类
        Integer i1 = 127;  // 等价于 Integer.valueOf(127)
        Integer i2 = 127;
        // 自动拆箱 (unboxing)：包装类 → 基本类型
        int i3 = i1;       // 等价于 i1.intValue()

        // Integer 缓存：-128 ~ 127 之间的值从缓存池取
        System.out.println("Integer 缓存陷阱:");
        System.out.println("  127 == 127:   " + (i1 == i2) + "  ← true（在缓存范围内）");
        Integer j1 = 128, j2 = 128;
        System.out.println("  128 == 128:   " + (j1 == j2) + "  ← false（超出缓存）");
        System.out.println("  128.equals(128): " + j1.equals(j2) + "  ← 用 equals 才是正确的");

        // parse 方法：字符串 → 数值
        int parsed = Integer.parseInt("42");
        double dParsed = Double.parseDouble("3.14");
        boolean bParsed = Boolean.parseBoolean("true");
        System.out.printf("\nparse: %d, %.2f, %s%n", parsed, dParsed, bParsed);

        // 常量
        System.out.println("Integer.MAX: " + Integer.MAX_VALUE);
        System.out.println("Integer.MIN: " + Integer.MIN_VALUE);
    }

    /** 日期时间 API (java.time) */
    private static void dateTimeDemo() {
        System.out.println("\n========== 日期时间 ==========\n");

        // 当前日期/时间
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalDateTime dt = LocalDateTime.now();
        System.out.println("日期: " + today);
        System.out.println("时间: " + now);
        System.out.println("日期时间: " + dt);

        // 创建指定日期
        LocalDate start = LocalDate.of(2026, 7, 6);
        System.out.println("Reboot 开始: " + start + " (" + start.getDayOfWeek() + ")");

        // 计算日期差
        long daysPassed = ChronoUnit.DAYS.between(start, today);
        System.out.println("已过 " + daysPassed + " 天");

        // 日期运算
        LocalDate nextWeek = today.plusWeeks(1);
        LocalDate nextMonth = today.plusMonths(1);
        System.out.println("一周后: " + nextWeek);
        System.out.println("一月后: " + nextMonth);

        // 时间运算
        Duration duration = Duration.between(LocalTime.of(9, 0), now);
        System.out.printf("今天已编程: %d 小时 %d 分钟%n",
                duration.toHours(), duration.toMinutesPart());

        // 格式化
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE");
        System.out.println("格式化: " + today.format(fmt));

        // 判断先后
        System.out.println("start 在今天之前? " + start.isBefore(today));
        System.out.println("今天是周末? " + (today.getDayOfWeek() == DayOfWeek.SATURDAY
                || today.getDayOfWeek() == DayOfWeek.SUNDAY));
    }
}
