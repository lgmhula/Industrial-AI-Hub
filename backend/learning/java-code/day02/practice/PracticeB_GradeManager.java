package code.day02.practice;

/**
 * 实战题 B：学生成绩管理器
 *
 * 题目要求：
 *   实现一个控制台成绩管理系统，功能：
 *   1. 添加学生成绩（姓名 + 分数）
 *   2. 查看所有学生成绩
 *   3. 统计：最高分、最低分、平均分、及格率
 *   4. 按分数等级归类（A:90+, B:80+, C:70+, D:60+, F:<60）
 *   5. 退出
 *
 * 考察点：while 主循环 + switch 菜单 + if/else 等级判断 + 数组操作
 *
 * 进阶要求：
 *   1. 学生数量上限 100，超过提示
 *   2. 分数必须在 0-100 之间
 *   3. 用 break/continue 控制流程
 *
 * 运行：javac PracticeB_GradeManager.java && java PracticeB_GradeManager
 */
import java.util.Scanner;

public class PracticeB_GradeManager {

    static final int MAX_STUDENTS = 100;
    static String[] names = new String[MAX_STUDENTS];
    static int[] scores = new int[MAX_STUDENTS];
    static int count = 0; // 当前学生数

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("===== 学生成绩管理系统 =====");

        while (running) {
            printMenu();
            System.out.print("请选择操作: ");
            int choice = scanner.nextInt();
            scanner.nextLine(); // 消费换行符

            switch (choice) {
                case 1:
                    addStudent(scanner);
                    break;
                case 2:
                    showAllStudents();
                    break;
                case 3:
                    showStatistics();
                    break;
                case 4:
                    showGradeDistribution();
                    break;
                case 0:
                    System.out.println("再见！");
                    running = false;
                    break;
                default:
                    System.out.println("无效选项，请输入 0-4。");
            }
        }

        scanner.close();
    }

    static void printMenu() {
        System.out.println("\n--- 菜单 ---");
        System.out.println("1. 添加学生成绩");
        System.out.println("2. 查看所有学生");
        System.out.println("3. 成绩统计");
        System.out.println("4. 等级归类");
        System.out.println("0. 退出");
    }

    static void addStudent(Scanner scanner) {
        if (count >= MAX_STUDENTS) {
            System.out.println("学生数已达上限 (" + MAX_STUDENTS + ")，无法继续添加。");
            return;
        }

        System.out.print("学生姓名: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("姓名不能为空！");
            return;
        }

        System.out.print("成绩 (0-100): ");
        int score = scanner.nextInt();
        scanner.nextLine(); // 消费换行符

        if (score < 0 || score > 100) {
            System.out.println("成绩必须在 0-100 之间！");
            return;
        }

        names[count] = name;
        scores[count] = score;
        count++;
        System.out.println("添加成功！当前共 " + count + " 名学生。");
    }

    static void showAllStudents() {
        if (count == 0) {
            System.out.println("暂无学生数据。");
            return;
        }

        System.out.println("\n--- 所有学生 ---");
        for (int i = 0; i < count; i++) {
            System.out.printf("%2d. %-8s  %3d 分  %s%n",
                    i + 1, names[i], scores[i], getGrade(scores[i]));
        }
    }

    // 等级判断：if/else 链
    static String getGrade(int score) {
        if (score >= 90) return "A (优秀)";
        else if (score >= 80) return "B (良好)";
        else if (score >= 70) return "C (中等)";
        else if (score >= 60) return "D (及格)";
        else return "F (不及格)";
    }

    static void showStatistics() {
        if (count == 0) {
            System.out.println("暂无学生数据。");
            return;
        }

        int max = scores[0], min = scores[0], sum = 0;
        int passCount = 0;

        for (int i = 0; i < count; i++) {
            int s = scores[i];
            if (s > max) max = s;
            if (s < min) min = s;
            sum += s;
            if (s >= 60) passCount++;
        }

        double avg = (double) sum / count;
        double passRate = (double) passCount / count * 100;

        System.out.println("\n--- 成绩统计 ---");
        System.out.printf("学生总数: %d%n", count);
        System.out.printf("最高分:   %d%n", max);
        System.out.printf("最低分:   %d%n", min);
        System.out.printf("平均分:   %.1f%n", avg);
        System.out.printf("及格率:   %.1f%% (%d/%d)%n", passRate, passCount, count);
    }

    static void showGradeDistribution() {
        if (count == 0) {
            System.out.println("暂无学生数据。");
            return;
        }

        int a = 0, b = 0, c = 0, d = 0, f = 0;

        for (int i = 0; i < count; i++) {
            int s = scores[i];
            if (s >= 90) a++;
            else if (s >= 80) b++;
            else if (s >= 70) c++;
            else if (s >= 60) d++;
            else f++;
        }

        System.out.println("\n--- 等级归类 ---");
        System.out.printf("A (优秀, >=90): %d 人%n", a);
        System.out.printf("B (良好, >=80): %d 人%n", b);
        System.out.printf("C (中等, >=70): %d 人%n", c);
        System.out.printf("D (及格, >=60): %d 人%n", d);
        System.out.printf("F (不及格, <60): %d 人%n", f);
    }
}
