package code.day04;

/**
 * 学生实体类 —— Day 4 OOP 练习。
 *
 * <p>演示面向对象的四个基本概念：
 * <ul>
 *   <li><b>封装</b>：属性私有 (private)，通过公有方法 (getter/setter) 访问</li>
 *   <li><b>构造方法</b>：创建对象时初始化属性</li>
 *   <li><b>this 关键字</b>：区分成员变量和局部变量</li>
 *   <li><b>toString()</b>：重写以提供可读的对象描述</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-09
 */
public class Student {

    /** 学生姓名 */
    private String name;

    /** 学生年龄 */
    private int age;

    /** 考试成绩 */
    private double score;

    /**
     * 无参构造方法 —— 创建默认学生对象。
     *
     * <p>如果没有显式定义任何构造方法，编译器会自动生成一个无参构造。
     * 但一旦定义了有参构造，无参构造就消失了，需要手动补回。</p>
     */
    public Student() {
    }

    /**
     * 全参构造方法 —— 创建时一次性初始化所有属性。
     *
     * @param name  学生姓名，不能为空
     * @param age   学生年龄，应在 1~150 之间
     * @param score 考试分数，应在 0~100 之间
     */
    public Student(String name, int age, double score) {
        this.name = name;
        this.age = age;
        this.score = score;
    }

    /**
     * 获取学生姓名。
     * @return 学生姓名
     */
    public String getName() { return name; }

    /**
     * 设置学生姓名。
     * @param name 姓名，不能为空
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取学生年龄。
     * @return 年龄
     */
    public int getAge() { return age; }

    /**
     * 设置学生年龄。
     * @param age 年龄，应在 1~150 之间
     */
    public void setAge(int age) { this.age = age; }

    /**
     * 获取考试分数。
     * @return 分数
     */
    public double getScore() { return score; }

    /**
     * 设置考试分数。
     * @param score 分数，应在 0~100 之间
     */
    public void setScore(double score) { this.score = score; }

    /**
     * 根据分数返回等级描述。
     *
     * <ul>
     *   <li>90+ : 优秀</li>
     *   <li>80+ : 良好</li>
     *   <li>70+ : 中等</li>
     *   <li>60+ : 及格</li>
     *   <li>其他 : 不及格</li>
     * </ul>
     *
     * @return 等级描述字符串
     */
    public String getGrade() {
        if (score >= 90) return "优秀";
        else if (score >= 80) return "良好";
        else if (score >= 70) return "中等";
        else if (score >= 60) return "及格";
        else return "不及格";
    }

    /**
     * 返回学生的可读描述，格式为 "姓名(X岁, XX分, 等级)"。
     *
     * @return 学生信息字符串
     */
    @Override
    public String toString() {
        return String.format("%-8s (%2d岁, %5.1f分, %s)", name, age, score, getGrade());
    }
}
