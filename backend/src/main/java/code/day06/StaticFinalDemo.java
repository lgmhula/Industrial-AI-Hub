package code.day06;

/**
 * static 和 final 关键字完整演示。
 *
 * <p><b>static（静态）</b>：属于类，不属于对象。
 * <ul>
 *   <li>静态变量：所有对象共享同一份</li>
 *   <li>静态方法：无需创建对象即可调用</li>
 *   <li>静态代码块：类加载时执行一次</li>
 * </ul>
 *
 * <p><b>final（最终）</b>：不可改变。
 * <ul>
 *   <li>final 变量：赋值后不可修改（常量）</li>
 *   <li>final 方法：子类不能重写</li>
 *   <li>final 类：不能被继承（如 String）</li>
 * </ul>
 *
 * @author Reboot
 * @version 1.0
 * @since 2026-07-11
 */
public class StaticFinalDemo {

    /** static 变量：所有实例共享，类级别 */
    static int instanceCount = 0;

    /** static final 常量：全局唯一，不可修改（命名全大写+下划线） */
    static final String APP_NAME = "Industrial AI Hub";

    /** final 实例变量：可以在构造方法中赋值，但只能赋一次 */
    final int id;

    /** 普通实例变量 */
    String name;

    /** static 代码块：类加载时执行一次 */
    static {
        System.out.println("StaticFinalDemo 类已加载（static 代码块执行）");
    }

    public StaticFinalDemo(String name) {
        this.name = name;
        instanceCount++;
        this.id = instanceCount;
    }

    /** static 方法：无需创建对象，直接通过类名调用 */
    public static int getInstanceCount() {
        return instanceCount;
    }

    /** final 方法：子类不能重写 */
    public final void printId() {
        System.out.println("ID: " + id + "（final 方法，不可被重写）");
    }

    public static void main(String[] args) {
        System.out.println("========== static 和 final 演示 ==========\n");

        // static 变量：所有对象共享
        System.out.println("--- static 变量 ---");
        StaticFinalDemo s1 = new StaticFinalDemo("对象一");
        StaticFinalDemo s2 = new StaticFinalDemo("对象二");
        StaticFinalDemo s3 = new StaticFinalDemo("对象三");

        System.out.println("s1.id=" + s1.id + ", s2.id=" + s2.id + ", s3.id=" + s3.id);
        System.out.println("instanceCount（通过类名调用）: " + StaticFinalDemo.instanceCount);
        System.out.println("instanceCount（通过对象调用）: " + s1.instanceCount);
        System.out.println("s1.instanceCount == s2.instanceCount ? " + (s1.instanceCount == s2.instanceCount));

        // static final 常量
        System.out.println("\n--- static final 常量 ---");
        System.out.println("APP_NAME = " + StaticFinalDemo.APP_NAME);
        // StaticFinalDemo.APP_NAME = "xxx";  ← 编译错误！final 变量不能修改

        // final 变量 vs 普通变量
        System.out.println("\n--- final 实例变量 vs 普通变量 ---");
        System.out.println("final id 赋值后不能修改；name 可以随时修改");
        s1.name = "改名后的对象一";
        System.out.println("s1 改名后: " + s1.name);
        // s1.id = 999; ← 编译错误！

        // static 方法
        System.out.println("\n--- static 方法 ---");
        System.out.println("当前实例数: " + StaticFinalDemo.getInstanceCount());

        // final 不能继承的演示
        System.out.println("\n--- final 类的概念 ---");
        System.out.println("String 是 final 类，不能 extends String");
        System.out.println("Math 是 final 类且构造方法 private，不能实例化");
    }
}
