package code.day11;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Day 11: String + StringBuilder/StringBuffer + 包装类 + 日期时间。
 *
 * <p>本日主题是 Java 开发里每天都会遇到的常用类：
 * <ol>
 *   <li><b>String</b>：不可变字符串，常用 API 练习</li>
 *   <li><b>StringBuilder/StringBuffer</b>：可变字符串与拼接性能</li>
 *   <li><b>包装类</b>：自动装箱、拆箱、字符串转换</li>
 *   <li><b>java.time</b>：LocalDate/LocalDateTime 日期计算</li>
 * </ol>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-13
 */
public class Day11_CommonClasses {

    public static void main(String[] args) {
        stringApiDemo();
        stringPerformanceDemo();
        wrapperDemo();
        dateTimeDemo();
        miniPractice();
    }

    // ==================== String 常用 API ====================

    /** String API 全面练习 */
    private static void stringApiDemo() {
        System.out.println("========== String 常用 API ==========\n");

        String raw = "  Industrial AI Hub, Java Reboot!  ";
        String text = raw.trim();

        System.out.println("原始字符串: [" + raw + "]");
        System.out.println("trim 后:    [" + text + "]");
        System.out.println("长度 length(): " + text.length());
        System.out.println("是否为空 isEmpty(): " + text.isEmpty());
        System.out.println("是否空白 isBlank(): " + "   ".isBlank());

        System.out.println("\n--- 查找与判断 ---");
        System.out.println("startsWith(\"Industrial\"): " + text.startsWith("Industrial"));
        System.out.println("endsWith(\"!\"): " + text.endsWith("!"));
        System.out.println("contains(\"Java\"): " + text.contains("Java"));
        System.out.println("indexOf(\"AI\"): " + text.indexOf("AI"));
        System.out.println("lastIndexOf('a'): " + text.lastIndexOf('a'));

        System.out.println("\n--- 截取与替换 ---");
        System.out.println("substring(0, 10): " + text.substring(0, 10));
        System.out.println("replace(\"Java\", \"SpringBoot\"): " + text.replace("Java", "SpringBoot"));
        System.out.println("replaceAll(\"[aeiou]\", \"*\"): " + text.replaceAll("[aeiou]", "*"));

        System.out.println("\n--- 大小写与比较 ---");
        String java1 = "Java";
        String java2 = "java";
        System.out.println("toUpperCase(): " + java2.toUpperCase());
        System.out.println("equals(): " + java1.equals(java2));
        System.out.println("equalsIgnoreCase(): " + java1.equalsIgnoreCase(java2));
        System.out.println("compareTo(): " + java1.compareTo(java2));

        System.out.println("\n--- 分割与转换 ---");
        String csv = "device-001,temperature,36.8,C";
        String[] parts = csv.split(",");
        System.out.println("split(\",\"): " + Arrays.toString(parts));
        System.out.println("charAt(0): " + text.charAt(0));
        System.out.println("toCharArray(): " + Arrays.toString("Hub".toCharArray()));
        System.out.println("String.valueOf(123): " + String.valueOf(123));

        System.out.println("\n--- 不可变性 ---");
        String name = "Java";
        String changed = name.replace("J", "L");
        System.out.println("原字符串 name: " + name);
        System.out.println("新字符串 changed: " + changed);
        System.out.println("结论: String 的修改 API 都会返回新对象");
    }

    // ==================== StringBuilder/StringBuffer ====================

    /** String、StringBuilder、StringBuffer 拼接性能对比 */
    private static void stringPerformanceDemo() {
        System.out.println("\n========== 字符串拼接性能 ==========\n");

        int times = 10_000;

        long stringStart = System.nanoTime();
        String result = "";
        for (int i = 0; i < times; i++) {
            result += i;
        }
        long stringCost = System.nanoTime() - stringStart;

        long builderStart = System.nanoTime();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(i);
        }
        String builderResult = builder.toString();
        long builderCost = System.nanoTime() - builderStart;

        long bufferStart = System.nanoTime();
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < times; i++) {
            buffer.append(i);
        }
        String bufferResult = buffer.toString();
        long bufferCost = System.nanoTime() - bufferStart;

        System.out.println("拼接次数: " + times);
        System.out.printf("String +=       : %.3f ms%n", stringCost / 1_000_000.0);
        System.out.printf("StringBuilder   : %.3f ms%n", builderCost / 1_000_000.0);
        System.out.printf("StringBuffer    : %.3f ms%n", bufferCost / 1_000_000.0);
        System.out.println("结果长度一致: " + (result.length() == builderResult.length()
                && builderResult.length() == bufferResult.length()));

        System.out.println("\n--- StringBuilder 常用 API ---");
        StringBuilder sql = new StringBuilder();
        sql.append("select * from device");
        sql.append(" where status = 'ONLINE'");
        sql.insert(0, "[SQL] ");
        sql.replace(0, 5, "[QUERY]");
        sql.append(" order by created_at desc");
        System.out.println(sql);
        System.out.println("reverse 示例: " + new StringBuilder("abc").reverse());

        System.out.println("\n结论:");
        System.out.println("单线程大量拼接优先 StringBuilder；");
        System.out.println("需要线程安全的可变字符串才考虑 StringBuffer。");
    }

    // ==================== 包装类 ====================

    /** 包装类、自动装箱/拆箱、类型转换 */
    private static void wrapperDemo() {
        System.out.println("\n========== 包装类 ==========\n");

        int primitive = 100;
        Integer boxed = primitive;      // 自动装箱 int -> Integer
        int unboxed = boxed;            // 自动拆箱 Integer -> int

        System.out.println("基本类型 int: " + primitive);
        System.out.println("包装类 Integer: " + boxed);
        System.out.println("自动拆箱结果: " + unboxed);

        System.out.println("\n--- 字符串与数字转换 ---");
        String scoreText = "95";
        int score = Integer.parseInt(scoreText);
        double temperature = Double.parseDouble("36.8");
        boolean online = Boolean.parseBoolean("true");
        System.out.println("Integer.parseInt(\"95\"): " + score);
        System.out.println("Double.parseDouble(\"36.8\"): " + temperature);
        System.out.println("Boolean.parseBoolean(\"true\"): " + online);
        System.out.println("Integer.toString(2026): " + Integer.toString(2026));

        System.out.println("\n--- 常量与工具方法 ---");
        System.out.println("Integer.MAX_VALUE: " + Integer.MAX_VALUE);
        System.out.println("Integer.MIN_VALUE: " + Integer.MIN_VALUE);
        System.out.println("Integer.compare(10, 20): " + Integer.compare(10, 20));
        System.out.println("Double.isNaN(0.0 / 0.0): " + Double.isNaN(0.0 / 0.0));

        System.out.println("\n--- Integer 缓存陷阱 ---");
        Integer a = 127;
        Integer b = 127;
        Integer c = 128;
        Integer d = 128;
        System.out.println("127 == 127: " + (a == b));
        System.out.println("128 == 128: " + (c == d));
        System.out.println("128 equals 128: " + c.equals(d));
        System.out.println("结论: 包装类比较值，用 equals，不要用 ==。");
    }

    // ==================== 日期时间 ====================

    /** LocalDate/LocalDateTime 日期计算练习 */
    private static void dateTimeDemo() {
        System.out.println("\n========== 日期时间 java.time ==========\n");

        LocalDate today = LocalDate.now();
        LocalDate rebootStart = LocalDate.of(2026, 7, 13);
        LocalDate springBootDay = rebootStart.plusDays(20);

        System.out.println("今天: " + today);
        System.out.println("Reboot 开始日: " + rebootStart);
        System.out.println("第 21 天日期: " + springBootDay);
        System.out.println("今天是星期: " + today.getDayOfWeek());
        System.out.println("本月天数: " + today.lengthOfMonth());
        System.out.println("是否闰年: " + today.isLeapYear());

        System.out.println("\n--- 日期计算 ---");
        System.out.println("7 天后: " + today.plusDays(7));
        System.out.println("1 个月后: " + today.plusMonths(1));
        System.out.println("本周周一: " + today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()));
        System.out.println("距离第 21 天还有: " + Period.between(today, springBootDay).getDays() + " 天");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime codingEnd = now.plusHours(2).plusMinutes(30);
        Duration duration = Duration.between(now, codingEnd);

        System.out.println("\n--- 时间计算 ---");
        System.out.println("当前时间: " + now);
        System.out.println("编码块结束时间: " + codingEnd);
        System.out.println("编码块长度分钟: " + duration.toMinutes());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("格式化时间: " + now.format(formatter));

        String dateText = "2026-08-02 09:00:00";
        LocalDateTime parsed = LocalDateTime.parse(dateText, formatter);
        System.out.println("解析时间: " + parsed);
    }

    // ==================== 综合小练习 ====================

    /** 用今天的知识写一个设备日志格式化小练习 */
    private static void miniPractice() {
        System.out.println("\n========== 综合小练习：设备日志格式化 ==========\n");

        String rawLog = " device-001 | temperature | 36.8 | celsius ";
        String[] fields = rawLog.trim().split("\\|");

        String deviceId = fields[0].trim().toUpperCase();
        String metric = fields[1].trim();
        double value = Double.parseDouble(fields[2].trim());
        String unit = fields[3].trim();
        LocalDateTime recordedAt = LocalDateTime.now();

        StringBuilder log = new StringBuilder();
        log.append("[")
                .append(recordedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("] ")
                .append(deviceId)
                .append(" -> ")
                .append(metric)
                .append("=")
                .append(String.format("%.1f", value))
                .append(" ")
                .append(unit);

        System.out.println("原始日志: " + rawLog);
        System.out.println("格式化后: " + log);
        System.out.println("是否温度告警: " + isTemperatureAlarm(metric, value));
    }

    private static boolean isTemperatureAlarm(String metric, double value) {
        return "temperature".equalsIgnoreCase(metric) && value >= 35.0;
    }
}
